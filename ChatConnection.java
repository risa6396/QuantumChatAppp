package quantumchat.chat;

import quantumchat.net.MessageTransport;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encrypted messaging and file transfer over any {@link MessageTransport}.
 *
 * Outgoing text and files are sealed with the session key (AES-256-GCM).
 * Incoming frames are NOT decrypted here: they are handed to the listener as
 * {@link SecureFrame}s, still encrypted, and the application decides when --
 * and with which key -- to open them (the GUI requires the user to enter the
 * session key first).
 */
public class ChatConnection {

    /** Plaintext bytes per file chunk. */
    public static final int CHUNK_SIZE = 32 * 1024;
    /** Largest file that can be sent (files are held in memory on the receiving side). */
    public static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    /** Largest text message, in UTF-8 bytes. */
    public static final int MAX_TEXT_BYTES = 1_000_000;

    private final MessageTransport transport;
    private final byte[] aesKey;
    private final AtomicInteger nextTransferId = new AtomicInteger(new SecureRandom().nextInt(1 << 28));
    private volatile boolean running = false;

    public ChatConnection(MessageTransport transport, byte[] aesKey) {
        this.transport = transport;
        this.aesKey = aesKey;
    }

    public void sendText(String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IOException("Message is too long (limit " + MAX_TEXT_BYTES / 1000 + " KB).");
        }
        transport.send(SecureFrame.seal(aesKey, SecureFrame.TEXT, 0, 0, 1, bytes).encode());
    }

    /**
     * Encrypts and sends a file: one sealed header frame (name, size, SHA-256)
     * followed by sealed {@value #CHUNK_SIZE}-byte chunks. Blocking -- call it
     * from a background thread. {@code progress} is called after every chunk.
     *
     * @return the transfer id
     */
    public int sendFile(File file, Progress progress) throws Exception {
        if (!file.isFile()) {
            throw new IOException("Not a regular file: " + file.getName());
        }
        long size = file.length();
        if (size > MAX_FILE_BYTES) {
            throw new IOException("File is too large (limit " + (MAX_FILE_BYTES >> 20) + " MB).");
        }

        byte[] buf = new byte[CHUNK_SIZE];

        // Pass 1: hash the file so the (encrypted) header can carry its SHA-256.
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long counted = 0;
        try (InputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
                counted += n;
            }
        }
        if (counted != size) {
            throw new IOException("The file changed while it was being read.");
        }

        int chunks = (int) ((size + CHUNK_SIZE - 1) / CHUNK_SIZE);
        int id = nextTransferId.incrementAndGet();
        byte[] meta = FileInfo.encode(file.getName(), size, CHUNK_SIZE, md.digest());
        transport.send(SecureFrame.seal(aesKey, SecureFrame.FILE_META, id, 0, chunks, meta).encode());

        // Pass 2: send the chunks.
        long sent = 0;
        try (InputStream in = new FileInputStream(file)) {
            for (int i = 0; i < chunks; i++) {
                int want = (int) Math.min(CHUNK_SIZE, size - sent);
                int off = 0;
                while (off < want) {
                    int n = in.read(buf, off, want - off);
                    if (n < 0) {
                        throw new EOFException("The file shrank while it was being sent.");
                    }
                    off += n;
                }
                byte[] plain = want == buf.length ? buf : Arrays.copyOf(buf, want);
                transport.send(SecureFrame.seal(aesKey, SecureFrame.FILE_CHUNK, id, i, chunks, plain).encode());
                sent += want;
                if (progress != null) {
                    progress.onProgress(sent, size);
                }
            }
        }
        if (progress != null && size == 0) {
            progress.onProgress(0, 0);
        }
        return id;
    }

    /** Starts a background thread delivering each incoming frame, still encrypted. */
    public void startListening(Listener listener) {
        running = true;
        Thread receiver = new Thread(() -> {
            while (running) {
                byte[] raw;
                try {
                    raw = transport.receive();
                } catch (Exception e) {
                    if (running) {
                        listener.onDisconnected();
                    }
                    return;
                }
                try {
                    listener.onFrame(SecureFrame.decode(raw));
                } catch (IOException malformed) {
                    listener.onInvalidFrame(malformed.getMessage());
                }
            }
        }, "chat-receiver");
        receiver.setDaemon(true);
        receiver.start();
    }

    public void close() {
        running = false;
        try {
            transport.close();
        } catch (Exception ignored) {
            // best-effort close
        }
    }

    public interface Listener {
        /** A frame arrived. Its payload is still encrypted. */
        void onFrame(SecureFrame frame);

        void onDisconnected();

        /** A frame was malformed and dropped. */
        default void onInvalidFrame(String reason) { }
    }

    public interface Progress {
        void onProgress(long done, long total);
    }
}
