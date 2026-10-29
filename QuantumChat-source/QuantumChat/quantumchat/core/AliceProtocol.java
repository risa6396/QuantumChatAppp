package quantumchat.core;

import quantumchat.crypto.CryptoUtil;
import java.util.Random;

/**
 * Runs the BB84 protocol as "Alice": generates the initial random bits and
 * bases, applies the configured {@link EavesdropperStrategy} and
 * {@link NoiseModel} to what actually goes out on the channel, and makes
 * the final abort/proceed decision based on the measured QBER.
 */
public class AliceProtocol {

    public QKDResult run(QKDChannel channel, QKDConfig config, QKDProgressListener listener) {
        try {
            config.validate();
            Random rng = config.newRandom();
            int n = config.getNumQubits();

            listener.onPhase(QKDPhase.GENERATING_QUBITS, "Generating " + n + " random bits and bases");
            int[] aliceBits = BB84Math.randomBits(n, rng);
            int[] aliceBases = BB84Math.randomBases(n, rng);

            EavesdropperStrategy eve = config.buildEavesdropper();
            NoiseModel noise = config.buildNoiseModel();

            listener.onPhase(QKDPhase.TRANSMITTING,
                    "Transmitting qubits (simulated) -- Eavesdropper: " + eve.name() + ", Noise: " + noise.name());
            EavesdropperStrategy.InterceptOutcome outcome = eve.apply(aliceBits, aliceBases, rng);
            int[] txBits = outcome.transmittedBits;
            int[] txBases = outcome.transmittedBases;
            for (int i = 0; i < txBits.length; i++) {
                txBits[i] = noise.apply(txBits[i], rng);
            }
            if (outcome.interceptedCount > 0) {
                listener.onLog("[Simulation ground truth] Eavesdropper touched "
                        + outcome.interceptedCount + " / " + n + " qubits.");
            }

            channel.sendInts(txBits);
            channel.sendInts(txBases);

            listener.onPhase(QKDPhase.SIFTING, "Waiting for Bob's measurement bases...");
            int[] bobBases = channel.recvInts();
            int[] matching = BB84Math.matchingIndices(aliceBases, bobBases);
            listener.onLog("Basis reconciliation kept " + matching.length + " / " + n + " bits ("
                    + String.format("%.1f%%", 100.0 * matching.length / n) + ").");
            channel.sendInts(matching);

            int[] aliceSifted = BB84Math.select(aliceBits, matching);
            int siftedLen = aliceSifted.length;

            listener.onPhase(QKDPhase.SAMPLING, "Publishing sample bits to estimate the error rate...");
            int sampleSize = Math.max(1, (int) Math.round(siftedLen * config.getSampleFraction()));
            int[] shuffled = BB84Math.shuffledIndices(siftedLen, rng);
            int[] sampleLocal = java.util.Arrays.copyOf(shuffled, sampleSize);
            int[] aliceSampleBits = BB84Math.select(aliceSifted, sampleLocal);
            channel.sendInts(sampleLocal);
            channel.sendInts(aliceSampleBits);

            listener.onPhase(QKDPhase.EVALUATING, "Waiting for Bob's QBER report...");
            double qber = channel.recvDouble();

            int[] keyLocal = BB84Math.complement(siftedLen, sampleLocal);
            int rawKeyBits = keyLocal.length;

            QKDStats stats = new QKDStats(n, siftedLen, sampleSize,
                    (int) Math.round(qber * sampleSize), qber, rawKeyBits, outcome.interceptedCount);
            listener.onStats(stats);

            if (qber > config.getQberAbortThreshold()) {
                String msg = "QBER " + String.format("%.2f%%", qber * 100) + " exceeds the "
                        + String.format("%.0f%%", config.getQberAbortThreshold() * 100)
                        + " security threshold -- an eavesdropper is likely present.";
                listener.onPhase(QKDPhase.ABORTED, msg);
                channel.sendString("ABORT");
                return QKDResult.aborted(msg, stats);
            }
            if (rawKeyBits < config.getMinFinalKeyBits()) {
                String msg = "Only " + rawKeyBits + " raw key bits survived (need "
                        + config.getMinFinalKeyBits() + ").";
                listener.onPhase(QKDPhase.ABORTED, msg);
                channel.sendString("ABORT");
                return QKDResult.aborted(msg, stats);
            }

            channel.sendString("OK");
            int[] finalKeyBits = BB84Math.select(aliceSifted, keyLocal);

            listener.onPhase(QKDPhase.EVALUATING, "Reconciling any residual bit errors...");
            boolean reconciled = ErrorReconciliation.reconcileAsReference(finalKeyBits, qber, channel, rng, listener);
            if (!reconciled) {
                String msg = "Error reconciliation failed to converge; aborting for safety.";
                listener.onPhase(QKDPhase.ABORTED, msg);
                return QKDResult.aborted(msg, stats);
            }

            byte[] aesKey = CryptoUtil.deriveAesKey(finalKeyBits);
            listener.onPhase(QKDPhase.KEY_READY,
                    "Shared key established (" + rawKeyBits + " raw bits -> 256-bit AES key).");
            return QKDResult.success(aesKey, stats);

        } catch (Exception e) {
            listener.onPhase(QKDPhase.FAILED, "Error: " + e.getMessage());
            return QKDResult.failed(String.valueOf(e.getMessage()));
        }
    }
}
