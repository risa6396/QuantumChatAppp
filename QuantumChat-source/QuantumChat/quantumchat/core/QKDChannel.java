package quantumchat.core;

/**
 * The classical communication channel BB84 uses for basis reconciliation,
 * sample publishing, and the final go/no-go decision. Deliberately
 * transport-agnostic: implementations can sit on top of a real socket or an
 * in-memory loopback, letting {@link AliceProtocol} / {@link BobProtocol}
 * run unchanged in either case.
 */
public interface QKDChannel {

    void sendInts(int[] values) throws Exception;

    int[] recvInts() throws Exception;

    void sendString(String s) throws Exception;

    String recvString() throws Exception;

    void sendDouble(double d) throws Exception;

    double recvDouble() throws Exception;
}
