package quantumchat.chat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * Reassembles one incoming file from its encrypted frames. It never needs
 * the key to COLLECT frames -- only to read the file's name/size or to write
 * the file out -- which is what lets a locked receiver show "encrypted file,
 * 12 / 40 chunks received" and keep everything sealed until the user
 * supplies the correct session key.
 */
public final class IncomingFile {

    public final int transferId;
    private SecureFrame meta;
    private SecureFrame[] chunks;
    private int expected = -1;
    private int received;
    private long encryptedBytes;

    public IncomingFile(int transferId) {
        this.transferId = transferId;
    }

    /** @return false if the frame is invalid or a duplicate (and was ignored). */
    public synchronized boolean accept(SecureFrame f) {
        if (f.transferId != transferId) {
            return false;
        }
        if (f.total > maxChunks() || f.total < 0) {
            return false;
        }
        if (expected == -1) {
            expected = f.total;
            chunks = new SecureFrame[expected];
        } else if (expected != f.total) {
            return false;
        }
        if (f.type == SecureFrame.FILE_META) {
            if (meta != null) {
                return false;
            }
            meta = f;
            encryptedBytes += f.sealed.length;
            return true;
        }
        if (f.type == SecureFrame.FILE_CHUNK) {
            if (f.index >= expected || chunks[f.index] != null) {
                return false;
            }
            chunks[f.index] = f;
            received++;
            encryptedBytes += f.sealed.length;
            return true;
        }
        return false;
    }

    public static int maxChunks() {
        return (int) (ChatConnection.MAX_FILE_BYTES / ChatConnection.CHUNK_SIZE);
    }

    public synchronized boolean hasMeta() {
        return meta != null;
    }

    /** Number of chunks the sender announced, or -1 if nothing has arrived yet. */
    public synchronized int expectedChunks() {
        return expected;
    }

    public synchronized int receivedChunks() {
        return received;
    }

    public synchronized long encryptedBytes() {
        return encryptedBytes;
    }

    public synchronized boolean isComplete() {
        return meta != null && expected >= 0 && received == expected;
    }

    /** Decrypts the file header. Returns null if it hasn't arrived or the key is wrong. */
    public synchronized FileInfo readInfo(byte[] key) {
        if (meta == null) {
            return null;
        }
        byte[] pt = meta.tryOpen(key);
        if (pt == null) {
            return null;
        }
        try {
            return FileInfo.decode(pt);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Decrypts every chunk with {@code key}, writes the plaintext to
     * {@code dest}, and verifies the length and SHA-256 recorded by the
     * sender. If anything fails (wrong key, tampered chunk, missing chunk,
     * hash mismatch) the partial output is deleted and an exception is thrown.
     */
    public void decryptTo(File dest, byte[] key, ChatConnection.Progress progress) throws Exception {
        final FileInfo info;
        final SecureFrame[] snapshot;
        synchronized (this) {
            if (!isComplete()) {
                throw new IOException("The file has not been fully received yet.");
            }
            info = readInfo(key);
            snapshot = chunks.clone();
        }
        if (info == null) {
            throw new GeneralSecurityException("Wrong key (the file header could not be decrypted).");
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long written = 0;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            for (SecureFrame chunk : snapshot) {
                byte[] pt = chunk.open(key);
                out.write(pt);
                md.update(pt);
                written += pt.length;
                if (progress != null) {
                    progress.onProgress(written, info.size);
                }
            }
        } catch (Exception e) {
            dest.delete();
            throw e;
        }
        if (written != info.size || !MessageDigest.isEqual(md.digest(), info.sha256)) {
            dest.delete();
            throw new IOException("Integrity check failed: the file does not match the sender's SHA-256.");
        }
        if (progress != null && info.size == 0) {
            progress.onProgress(0, 0);
        }
    }
}
