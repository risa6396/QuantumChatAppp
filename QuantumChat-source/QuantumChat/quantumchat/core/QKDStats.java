package quantumchat.core;

/** Immutable snapshot of a BB84 run's statistics, for display and logging. */
public final class QKDStats {

    public final int numQubits;
    public final int siftedCount;
    public final int sampleSize;
    public final int sampleMismatches;
    public final double qber;
    public final int rawKeyBits;

    /**
     * Ground-truth count of how many qubits an eavesdropper actually touched
     * in this simulation run. This is informational only, for teaching --
     * in a real protocol neither Alice nor Bob could ever know this number;
     * they only ever see the derived QBER. Set to -1 when not available
     * (e.g. on Bob's side, where the simulator doesn't surface it at all).
     */
    public final int simulatedInterceptedCount;

    public QKDStats(int numQubits, int siftedCount, int sampleSize, int sampleMismatches,
                     double qber, int rawKeyBits, int simulatedInterceptedCount) {
        this.numQubits = numQubits;
        this.siftedCount = siftedCount;
        this.sampleSize = sampleSize;
        this.sampleMismatches = sampleMismatches;
        this.qber = qber;
        this.rawKeyBits = rawKeyBits;
        this.simulatedInterceptedCount = simulatedInterceptedCount;
    }
}
