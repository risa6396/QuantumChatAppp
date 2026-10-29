package quantumchat.core;

import quantumchat.crypto.CryptoUtil;
import java.util.Random;

/**
 * Runs the BB84 protocol as "Bob": chooses measurement bases independently
 * of Alice, measures the incoming (possibly disturbed) qubits, and computes
 * the QBER from the publicly compared sample.
 */
public class BobProtocol {

    public QKDResult run(QKDChannel channel, QKDConfig config, QKDProgressListener listener) {
        try {
            config.validate();
            Random rng = config.newRandom();
            int n = config.getNumQubits();

            listener.onPhase(QKDPhase.GENERATING_QUBITS, "Choosing " + n + " random measurement bases");
            int[] bobBases = BB84Math.randomBases(n, rng);

            listener.onPhase(QKDPhase.TRANSMITTING, "Receiving qubits (simulated)...");
            int[] rxBits = channel.recvInts();
            int[] rxBases = channel.recvInts();

            listener.onPhase(QKDPhase.MEASURING, "Measuring qubits...");
            int[] bobBits = BB84Math.measureAll(rxBits, rxBases, bobBases, rng);

            channel.sendInts(bobBases);

            listener.onPhase(QKDPhase.SIFTING, "Basis reconciliation...");
            int[] matching = channel.recvInts();
            listener.onLog("Basis reconciliation kept " + matching.length + " / " + n + " bits.");
            int[] bobSifted = BB84Math.select(bobBits, matching);
            int siftedLen = bobSifted.length;

            listener.onPhase(QKDPhase.SAMPLING, "Estimating QBER from the published sample...");
            int[] sampleLocal = channel.recvInts();
            int[] aliceSampleBits = channel.recvInts();
            int[] bobSampleBits = BB84Math.select(bobSifted, sampleLocal);
            int mismatches = 0;
            for (int i = 0; i < sampleLocal.length; i++) {
                if (aliceSampleBits[i] != bobSampleBits[i]) {
                    mismatches++;
                }
            }
            double qber = sampleLocal.length == 0 ? 0.0 : mismatches / (double) sampleLocal.length;
            listener.onLog("Computed QBER: " + String.format("%.2f%%", qber * 100)
                    + " (" + mismatches + "/" + sampleLocal.length + " sample bits mismatched)");
            channel.sendDouble(qber);

            listener.onPhase(QKDPhase.EVALUATING, "Waiting for Alice's decision...");
            String decision = channel.recvString();

            int[] keyLocal = BB84Math.complement(siftedLen, sampleLocal);
            QKDStats stats = new QKDStats(n, siftedLen, sampleLocal.length, mismatches, qber, keyLocal.length, -1);
            listener.onStats(stats);

            if (!"OK".equals(decision)) {
                listener.onPhase(QKDPhase.ABORTED, "Alice aborted the key exchange.");
                return QKDResult.aborted(
                        "Peer aborted the key exchange (QBER too high or insufficient key material).", stats);
            }

            int[] finalKeyBits = BB84Math.select(bobSifted, keyLocal);

            listener.onPhase(QKDPhase.EVALUATING, "Reconciling any residual bit errors...");
            boolean reconciled = ErrorReconciliation.reconcileAsCorrector(finalKeyBits, qber, channel, rng, listener);
            if (!reconciled) {
                String msg = "Error reconciliation failed to converge; aborting for safety.";
                listener.onPhase(QKDPhase.ABORTED, msg);
                return QKDResult.aborted(msg, stats);
            }

            byte[] aesKey = CryptoUtil.deriveAesKey(finalKeyBits);
            listener.onPhase(QKDPhase.KEY_READY, "Shared key established.");
            return QKDResult.success(aesKey, stats);

        } catch (Exception e) {
            listener.onPhase(QKDPhase.FAILED, "Error: " + e.getMessage());
            return QKDResult.failed(String.valueOf(e.getMessage()));
        }
    }
}
