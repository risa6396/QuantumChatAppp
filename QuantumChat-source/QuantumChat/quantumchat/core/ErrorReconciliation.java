package quantumchat.core;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A simplified multi-pass block-parity error reconciliation (a basic form
 * of the Cascade protocol), followed by an explicit verification step.
 *
 * Even when the QBER sample looks acceptable, the un-sampled remainder of
 * the sifted key can still contain residual bit errors (channel noise, or a
 * partial eavesdropper not fully caught by the sample). Left uncorrected,
 * those would make Alice's and Bob's final AES keys silently diverge and
 * break the chat with no obvious explanation. This class fixes that in two
 * stages:
 *
 * <ol>
 *   <li>A fixed schedule of block-parity-and-bisect passes (standard
 *       Cascade style), each with a fresh random permutation so later
 *       passes can catch error pairs that cancelled out in an earlier
 *       pass's block parity.</li>
 *   <li>A verify-then-cleanup loop: both sides compare a short hash of
 *       their current key; if it doesn't match yet, one more small-block
 *       cleanup pass runs and the hash is checked again, up to a bounded
 *       number of rounds. This makes convergence a guarantee rather than a
 *       probability -- {@link #reconcileAsReference} /
 *       {@link #reconcileAsCorrector} return {@code false} (rather than
 *       silently returning a mismatched key) in the vanishingly rare case
 *       the cleanup budget is exhausted.</li>
 * </ol>
 *
 * NOTE: comparing parities (and the final verification hash) over the
 * public channel leaks a small, bounded amount of information about the
 * key. Real QKD systems feed that leakage into privacy amplification
 * formally. This implementation keeps things approachable for a teaching
 * tool -- the subsequent SHA-256 hashing in
 * {@link quantumchat.crypto.CryptoUtil#deriveAesKey} still substantially
 * reduces an eavesdropper's usable information, but isn't a rigorous
 * leftover-hash-lemma bound. Treat this as educational, not
 * production-grade reconciliation.
 */
public final class ErrorReconciliation {

    private static final int MAX_CLEANUP_ROUNDS = 20;

    private ErrorReconciliation() { }

    /**
     * Alice's side: her bits are the reference and are never changed, but she
     * must still answer every parity/bisection/verification query so Bob can
     * correct his copy. Returns true once both sides have verified matching keys.
     */
    public static boolean reconcileAsReference(int[] referenceKey, double estimatedQber, QKDChannel channel,
                                                Random rng, QKDProgressListener listener) throws Exception {
        return reconcile(referenceKey, true, estimatedQber, channel, rng, listener);
    }

    /** Bob's side: corrects his own key array in place to match Alice's. */
    public static boolean reconcileAsCorrector(int[] key, double estimatedQber, QKDChannel channel,
                                                Random rng, QKDProgressListener listener) throws Exception {
        return reconcile(key, false, estimatedQber, channel, rng, listener);
    }

    private static boolean reconcile(int[] key, boolean isReference, double estimatedQber, QKDChannel channel,
                                      Random rng, QKDProgressListener listener) throws Exception {
        int len = key.length;
        if (len < 2) {
            return true;
        }

        // Standard Cascade-ish schedule: start around 0.73/QBER bits per block
        // (so an average block has roughly one error), then double each pass.
        double q = Math.max(estimatedQber, 0.005);
        int baseBlock = (int) Math.max(4, Math.min(64, Math.round(0.73 / q)));
        int[] schedule = { baseBlock, baseBlock * 2, baseBlock * 4, baseBlock * 8 };
        for (int i = 0; i < schedule.length; i++) {
            runPass(key, isReference, Math.min(schedule[i], len), channel, rng, listener, "pass " + (i + 1));
        }

        for (int round = 0; round < MAX_CLEANUP_ROUNDS; round++) {
            if (verifyMatch(key, isReference, channel)) {
                if (listener != null) {
                    listener.onLog("Error reconciliation verified: both keys match.");
                }
                return true;
            }
            int cleanupBlock = Math.max(2, Math.min(8, len));
            runPass(key, isReference, cleanupBlock, channel, rng, listener, "cleanup " + (round + 1));
        }
        return verifyMatch(key, isReference, channel);
    }

    private static void runPass(int[] key, boolean isReference, int blockSize, QKDChannel channel,
                                 Random rng, QKDProgressListener listener, String label) throws Exception {
        int len = key.length;
        if (blockSize < 2) {
            return;
        }

        int[] permutation;
        if (isReference) {
            permutation = BB84Math.shuffledIndices(len, rng);
            channel.sendInts(permutation);
        } else {
            permutation = channel.recvInts();
        }

        int numBlocks = (len + blockSize - 1) / blockSize;
        int[] starts = new int[numBlocks];
        int[] ends = new int[numBlocks];
        int[] myParities = new int[numBlocks];
        for (int b = 0; b < numBlocks; b++) {
            int start = b * blockSize;
            int end = Math.min(len, start + blockSize);
            starts[b] = start;
            ends[b] = end;
            myParities[b] = parityOf(key, permutation, start, end);
        }

        int[] mismatchedBlocks;
        if (isReference) {
            channel.sendInts(myParities);
            mismatchedBlocks = channel.recvInts();
        } else {
            int[] refParities = channel.recvInts();
            List<Integer> mism = new ArrayList<>();
            for (int b = 0; b < numBlocks; b++) {
                if (refParities[b] != myParities[b]) {
                    mism.add(b);
                }
            }
            mismatchedBlocks = new int[mism.size()];
            for (int i = 0; i < mismatchedBlocks.length; i++) {
                mismatchedBlocks[i] = mism.get(i);
            }
            channel.sendInts(mismatchedBlocks);
        }

        for (int blockIndex : mismatchedBlocks) {
            bisectAndCorrect(key, permutation, starts[blockIndex], ends[blockIndex], isReference, channel);
        }

        if (listener != null && mismatchedBlocks.length > 0) {
            listener.onLog("[Error reconciliation, " + label + "] block size " + blockSize
                    + ": corrected " + mismatchedBlocks.length + " block(s).");
        }
    }

    private static int parityOf(int[] key, int[] permutation, int start, int end) {
        int p = 0;
        for (int i = start; i < end; i++) {
            p ^= key[permutation[i]];
        }
        return p;
    }

    /** Binary-searches a mismatched [start,end) range down to one differing bit and corrects it. */
    private static void bisectAndCorrect(int[] key, int[] permutation, int start, int end,
                                          boolean isReference, QKDChannel channel) throws Exception {
        int lo = start;
        int hi = end;
        while (hi - lo > 1) {
            int mid = lo + (hi - lo) / 2;
            int direction; // 0 = error is in [lo,mid), 1 = error is in [mid,hi)
            if (isReference) {
                int refHalfParity = parityOf(key, permutation, lo, mid);
                channel.sendInts(new int[] { refHalfParity });
                direction = channel.recvInts()[0];
            } else {
                int refHalfParity = channel.recvInts()[0];
                int myHalfParity = parityOf(key, permutation, lo, mid);
                direction = (refHalfParity != myHalfParity) ? 0 : 1;
                channel.sendInts(new int[] { direction });
            }
            if (direction == 0) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        if (!isReference) {
            key[permutation[lo]] ^= 1;
        }
    }

    /** Both sides compare a short hash of their current key; returns the (identical, on both sides) verdict. */
    private static boolean verifyMatch(int[] key, boolean isReference, QKDChannel channel) throws Exception {
        int[] myHash = toIntArray(shortHash(key));
        if (isReference) {
            channel.sendInts(myHash);
            return channel.recvInts()[0] == 1;
        } else {
            int[] refHash = channel.recvInts();
            boolean match = Arrays.equals(refHash, myHash);
            channel.sendInts(new int[] { match ? 1 : 0 });
            return match;
        }
    }

    private static byte[] shortHash(int[] key) throws Exception {
        byte[] packed = new byte[(key.length + 7) / 8];
        for (int i = 0; i < key.length; i++) {
            if (key[i] == 1) {
                packed[i / 8] |= (byte) (1 << (7 - (i % 8)));
            }
        }
        byte[] full = MessageDigest.getInstance("SHA-256").digest(packed);
        return Arrays.copyOf(full, 8); // a short checksum is plenty for a verification compare
    }

    private static int[] toIntArray(byte[] b) {
        int[] out = new int[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[i] & 0xFF;
        }
        return out;
    }
}
