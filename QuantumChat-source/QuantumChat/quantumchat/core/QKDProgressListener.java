package quantumchat.core;

/**
 * Callback interface the protocol classes use to report progress, so a GUI
 * (or a test harness, or nothing at all) can observe a run as it happens.
 * All methods are default no-ops so callers only implement what they need.
 */
public interface QKDProgressListener {

    default void onPhase(QKDPhase phase, String detail) { }

    default void onLog(String message) { }

    default void onStats(QKDStats stats) { }
}
