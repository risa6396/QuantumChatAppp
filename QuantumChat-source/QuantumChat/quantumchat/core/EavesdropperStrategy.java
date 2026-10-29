package quantumchat.core;

import java.util.Random;

/**
 * Pluggable strategy representing what (if anything) happens to qubits while
 * in transit on the simulated quantum channel, before Bob measures them.
 * This is the extension point that makes the framework "configurable":
 * different threat models can be swapped in without touching
 * {@link AliceProtocol} or {@link BobProtocol}.
 */
public interface EavesdropperStrategy {

    /** Human-readable name shown in the GUI / logs. */
    String name();

    /**
     * Given the bits/bases Alice originally prepared, returns what actually
     * arrives at Bob's end of the channel after this strategy has acted.
     */
    InterceptOutcome apply(int[] aliceBits, int[] aliceBases, Random rng);

    /** Result of applying a strategy: what Bob will actually measure against. */
    final class InterceptOutcome {
        public final int[] transmittedBits;
        public final int[] transmittedBases;
        /** How many qubits were actually touched (simulation ground truth, for teaching). */
        public final int interceptedCount;

        public InterceptOutcome(int[] transmittedBits, int[] transmittedBases, int interceptedCount) {
            this.transmittedBits = transmittedBits;
            this.transmittedBases = transmittedBases;
            this.interceptedCount = interceptedCount;
        }
    }

    /** Which built-in strategy a {@link QKDConfig} should build. */
    enum Mode { NONE, FULL_INTERCEPT, PARTIAL_INTERCEPT }

    /** No eavesdropper: qubits arrive exactly as Alice sent them. */
    static EavesdropperStrategy none() {
        return new EavesdropperStrategy() {
            @Override public String name() {
                return "None";
            }
            @Override public InterceptOutcome apply(int[] bits, int[] bases, Random rng) {
                return new InterceptOutcome(bits.clone(), bases.clone(), 0);
            }
        };
    }

    /** Eve intercepts and re-measures every qubit (the classic intercept-resend attack). */
    static EavesdropperStrategy fullIntercept() {
        return partialIntercept(1.0);
    }

    /**
     * Eve intercepts and re-measures only a fraction of qubits, chosen
     * independently at random -- a stealthier attacker, and a good way to
     * see how QBER scales with attack intensity (roughly QBER ~ 0.25 * p).
     */
    static EavesdropperStrategy partialIntercept(double interceptProbability) {
        final double p = Math.max(0.0, Math.min(1.0, interceptProbability));
        return new EavesdropperStrategy() {
            @Override public String name() {
                return p >= 1.0
                        ? "Full intercept-resend"
                        : "Partial intercept-resend (" + Math.round(p * 100) + "% of qubits)";
            }
            @Override public InterceptOutcome apply(int[] aliceBits, int[] aliceBases, Random rng) {
                int n = aliceBits.length;
                int[] outBits = aliceBits.clone();
                int[] outBases = aliceBases.clone();
                int intercepted = 0;
                for (int i = 0; i < n; i++) {
                    if (rng.nextDouble() < p) {
                        intercepted++;
                        int eveBasis = rng.nextInt(2);
                        int eveBit = BB84Math.measure(aliceBits[i], aliceBases[i], eveBasis, rng);
                        outBits[i] = eveBit;
                        outBases[i] = eveBasis;
                    }
                }
                return new InterceptOutcome(outBits, outBases, intercepted);
            }
        };
    }

    static EavesdropperStrategy fromMode(Mode mode, double interceptProbability) {
        switch (mode) {
            case FULL_INTERCEPT:
                return fullIntercept();
            case PARTIAL_INTERCEPT:
                return partialIntercept(interceptProbability);
            case NONE:
            default:
                return none();
        }
    }
}
