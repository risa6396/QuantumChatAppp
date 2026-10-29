package quantumchat.core;

/** The stages of a BB84 run, reported to a {@link QKDProgressListener} as they happen. */
public enum QKDPhase {
    CONNECTING,
    GENERATING_QUBITS,
    TRANSMITTING,
    MEASURING,
    SIFTING,
    SAMPLING,
    EVALUATING,
    KEY_READY,
    ABORTED,
    FAILED
}
