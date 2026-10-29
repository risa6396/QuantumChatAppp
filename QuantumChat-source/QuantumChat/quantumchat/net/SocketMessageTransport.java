package quantumchat.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

/** Length-prefixed framing of messages over a TCP socket's streams. */
public class SocketMessageTransport implements MessageTransport {

    /** Sanity cap so a misbehaving peer can't make us allocate gigabytes. */
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    public SocketMessageTransport(Socket socket) throws Exception {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public synchronized void send(byte[] data) throws Exception {
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    @Override
    public byte[] receive() throws Exception {
        int len = in.readInt();
        if (len < 0 || len > MAX_FRAME_BYTES) {
            throw new java.io.IOException("Refusing frame of " + len + " bytes");
        }
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }
}
