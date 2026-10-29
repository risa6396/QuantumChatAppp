package quantumchat.net;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * An in-process, loopback transport. Two linked instances created via
 * {@link #createPair()} let the exact same protocol code
 * ({@code AliceProtocol}/{@code BobProtocol}, {@code ChatConnection}, ...)
 * run entirely within one JVM, with no sockets at all. This is what powers
 * the GUI's "Local simulation" mode, where a user can try out different QKD
 * configurations instantly without needing a second machine.
 */
public class InMemoryMessageTransport implements MessageTransport {

    private static final byte[] POISON = new byte[0];

    private final BlockingQueue<byte[]> inbound;
    private final BlockingQueue<byte[]> outbound;
    private volatile boolean closed = false;

    private InMemoryMessageTransport(BlockingQueue<byte[]> inbound, BlockingQueue<byte[]> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    public static InMemoryMessageTransport[] createPair() {
        BlockingQueue<byte[]> aToB = new LinkedBlockingQueue<>();
        BlockingQueue<byte[]> bToA = new LinkedBlockingQueue<>();
        InMemoryMessageTransport a = new InMemoryMessageTransport(bToA, aToB);
        InMemoryMessageTransport b = new InMemoryMessageTransport(aToB, bToA);
        return new InMemoryMessageTransport[] { a, b };
    }

    @Override
    public void send(byte[] data) throws Exception {
        if (closed) {
            throw new IOException("Transport closed");
        }
        outbound.put(data.clone());
    }

    @Override
    public byte[] receive() throws Exception {
        byte[] v = inbound.take();
        if (v == POISON) {
            throw new EOFException("Peer closed the connection");
        }
        return v;
    }

    @Override
    public void close() {
        closed = true;
        inbound.offer(POISON);   // wake up our own blocked receive()
        outbound.offer(POISON);  // and let the peer see that we hung up
    }
}
