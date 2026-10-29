package quantumchat.core;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Every tunable parameter of a BB84 run, in one place. Both peers must agree
 * on these values (the GUI's host sends its config as part of handshaking
 * in a real session; for simplicity in this app both sides configure
 * independently and the protocol itself only requires each side's own
 * copy to decide sample size / abort thresholds -- see README for details).
 *
 * Uses a fluent setter style so GUI code and quick experiments can build a
 * config in one expression: {@code QKDConfig.defaults().numQubits(8192).eavesdropperMode(...)}.
 */
public class QKDConfig {

    private int numQubits = 4096;
    private double sampleFraction = 0.25;
    private double qberAbortThreshold = 0.11;
    private int minFinalKeyBits = 128;
    private double channelNoiseProbability = 0.0;
    private EavesdropperStrategy.Mode eavesdropperMode = EavesdropperStrategy.Mode.NONE;
    private double eavesdropProbability = 1.0;
    private Long randomSeed = null; // null => non-deterministic, cryptographically random

    public static QKDConfig defaults() {
        return new QKDConfig();
    }

    // ---- fluent setters ----

    public QKDConfig numQubits(int v) {
        this.numQubits = v;
        return this;
    }

    public QKDConfig sampleFraction(double v) {
        this.sampleFraction = v;
        return this;
    }

    public QKDConfig qberAbortThreshold(double v) {
        this.qberAbortThreshold = v;
        return this;
    }

    public QKDConfig minFinalKeyBits(int v) {
        this.minFinalKeyBits = v;
        return this;
    }

    public QKDConfig channelNoiseProbability(double v) {
        this.channelNoiseProbability = v;
        return this;
    }

    public QKDConfig eavesdropperMode(EavesdropperStrategy.Mode v) {
        this.eavesdropperMode = v;
        return this;
    }

    public QKDConfig eavesdropProbability(double v) {
        this.eavesdropProbability = v;
        return this;
    }

    public QKDConfig randomSeed(Long v) {
        this.randomSeed = v;
        return this;
    }

    // ---- getters ----

    public int getNumQubits() {
        return numQubits;
    }

    public double getSampleFraction() {
        return sampleFraction;
    }

    public double getQberAbortThreshold() {
        return qberAbortThreshold;
    }

    public int getMinFinalKeyBits() {
        return minFinalKeyBits;
    }

    public double getChannelNoiseProbability() {
        return channelNoiseProbability;
    }

    public EavesdropperStrategy.Mode getEavesdropperMode() {
        return eavesdropperMode;
    }

    public double getEavesdropProbability() {
        return eavesdropProbability;
    }

    public Long getRandomSeed() {
        return randomSeed;
    }

    // ---- derived objects ----

    public NoiseModel buildNoiseModel() {
        return channelNoiseProbability <= 0.0 ? NoiseModel.none() : NoiseModel.binarySymmetric(channelNoiseProbability);
    }

    public EavesdropperStrategy buildEavesdropper() {
        return EavesdropperStrategy.fromMode(eavesdropperMode, eavesdropProbability);
    }

    /** A seeded Random for reproducible simulation runs, or a SecureRandom when no seed is set. */
    public Random newRandom() {
        return randomSeed != null ? new Random(randomSeed) : new SecureRandom();
    }

    public void validate() {
        if (numQubits < 32) {
            throw new IllegalArgumentException("Number of qubits must be at least 32.");
        }
        if (sampleFraction <= 0 || sampleFraction >= 1) {
            throw new IllegalArgumentException("Sample fraction must be between 0 and 1 (exclusive).");
        }
        if (qberAbortThreshold <= 0 || qberAbortThreshold >= 1) {
            throw new IllegalArgumentException("QBER abort threshold must be between 0 and 1 (exclusive).");
        }
        if (minFinalKeyBits < 1) {
            throw new IllegalArgumentException("Minimum final key bits must be positive.");
        }
        if (channelNoiseProbability < 0 || channelNoiseProbability > 1) {
            throw new IllegalArgumentException("Channel noise probability must be between 0 and 1.");
        }
        if (eavesdropProbability < 0 || eavesdropProbability > 1) {
            throw new IllegalArgumentException("Eavesdrop intercept probability must be between 0 and 1.");
        }
    }

    @Override
    public String toString() {
        return "QKDConfig{qubits=" + numQubits
                + ", sampleFraction=" + sampleFraction
                + ", qberThreshold=" + qberAbortThreshold
                + ", noise=" + channelNoiseProbability
                + ", eve=" + eavesdropperMode
                + (eavesdropperMode == EavesdropperStrategy.Mode.PARTIAL_INTERCEPT ? "(" + eavesdropProbability + ")" : "")
                + ", seed=" + (randomSeed == null ? "random" : randomSeed)
                + "}";
    }
}
