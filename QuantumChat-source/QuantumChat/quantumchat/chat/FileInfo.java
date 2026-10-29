package quantumchat.chat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** The encrypted "header" of a file transfer: name, size, chunk size and SHA-256 of the whole file. */
public final class FileInfo {

    public final String name;
    public final long size;
    public final int chunkSize;
    public final byte[] sha256;

    public FileInfo(String name, long size, int chunkSize, byte[] sha256) {
        this.name = name;
        this.size = size;
        this.chunkSize = chunkSize;
        this.sha256 = sha256;
    }

    public static byte[] encode(String name, long size, int chunkSize, byte[] sha256) throws IOException {
        String n = name.length() > 255 ? name.substring(0, 255) : name;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeUTF(n);
        out.writeLong(size);
        out.writeInt(chunkSize);
        out.write(sha256);
        return bos.toByteArray();
    }

    public static FileInfo decode(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String name = in.readUTF();
        long size = in.readLong();
        int chunkSize = in.readInt();
        byte[] sha = new byte[32];
        in.readFully(sha);
        if (size < 0) {
            throw new IOException("Invalid file size");
        }
        return new FileInfo(name, size, chunkSize, sha);
    }
}
