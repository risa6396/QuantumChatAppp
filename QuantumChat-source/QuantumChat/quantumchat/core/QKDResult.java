package quantumchat.core;

/** The outcome of running {@link AliceProtocol} or {@link BobProtocol}. */
public final class QKDResult {

    public final boolean success;
    public final String abortReason;   // non-null only when !success
    public final byte[] aesKey;        // non-null only when success
    public final QKDStats stats;       // may be null if failure happened before stats existed

    private QKDResult(boolean success, String abortReason, byte[] aesKey, QKDStats stats) {
        this.success = success;
        this.abortReason = abortReason;
        this.aesKey = aesKey;
        this.stats = stats;
    }

    public static QKDResult success(byte[] aesKey, QKDStats stats) {
        return new QKDResult(true, null, aesKey, stats);
    }

    public static QKDResult aborted(String reason, QKDStats stats) {
        return new QKDResult(false, reason, null, stats);
    }

    public static QKDResult failed(String reason) {
        return new QKDResult(false, reason, null, null);
    }
}
