package quantumchat.net;

import quantumchat.core.QKDChannel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Implements the typed QKD classical-channel protocol (send/receive of int
 * arrays, strings, doubles) on top of any {@link MessageTransport}. This is
 * what lets {@code AliceProtocol}/{@code BobProtocol} run unchanged over a
 * real socket or an in-memory loopback transport.
 */
public class TransportQKDChannel implements QKDChannel {

    private final MessageTransport transport;

    public TransportQKDChannel(MessageTransport transport) {
        this.transport = transport;
    }

    @Override
    public void sendInts(int[] values) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(values.length);
        for (int v : values) {
            dos.writeInt(v);
        }
        transport.send(bos.toByteArray());
    }

    @Override
    public int[] recvInts() throws Exception {
        byte[] raw = transport.receive();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(raw));
        int len = dis.readInt();
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            out[i] = dis.readInt();
        }
        return out;
    }

    @Override
    public void sendString(String s) throws Exception {
        transport.send(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String recvString() throws Exception {
        return new String(transport.receive(), StandardCharsets.UTF_8);
    }

    @Override
    public void sendDouble(double d) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new DataOutputStream(bos).writeDouble(d);
        transport.send(bos.toByteArray());
    }

    @Override
    public double recvDouble() throws Exception {
        byte[] raw = transport.receive();
        return new DataInputStream(new ByteArrayInputStream(raw)).readDouble();
    }
}
