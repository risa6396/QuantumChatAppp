package quantumchat.core;

import java.util.Random;

/**
 * Pluggable model of classical channel/detector imperfections, applied to
 * transmitted bits independently of any eavesdropper. Real QKD links always
 * have some baseline error rate from the hardware itself; modeling it
 * separately from an attack lets the simulation distinguish "noisy but
 * honest channel" from "someone is listening".
 */
public interface NoiseModel {

    String name();

    /** Possibly flips a bit to simulate real-world channel/detector noise. */
    int apply(int bit, Random rng);

    static NoiseModel none() {
        return new NoiseModel() {
            @Override public String name() {
                return "None";
            }
            @Override public int apply(int bit, Random rng) {
                return bit;
            }
        };
    }

    /** Each bit is independently flipped with probability {@code flipProbability}. */
    static NoiseModel binarySymmetric(double flipProbability) {
        final double p = Math.max(0.0, Math.min(1.0, flipProbability));
        return new NoiseModel() {
            @Override public String name() {
                return "Binary symmetric (" + String.format("%.1f%%", p * 100) + ")";
            }
            @Override public int apply(int bit, Random rng) {
                return rng.nextDouble() < p ? (1 - bit) : bit;
            }
        };
    }
}
