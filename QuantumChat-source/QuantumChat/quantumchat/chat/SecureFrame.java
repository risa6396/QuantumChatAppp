package quantumchat.chat;

import quantumchat.crypto.CryptoUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * One application-level message on the wire.
 *
 * <pre>
 *   byte   type        TEXT / FILE_META / FILE_CHUNK        } 13-byte header,
 *   int    transferId  groups the frames of one file        } sent in the clear
 *   int    index       chunk number (0 for TEXT / FILE_META) } but authenticated
 *   int    total       number of chunks in the transfer     } as GCM AAD
 *   byte[] sealed      IV(12) || AES-256-GCM ciphertext || tag(16)
 * </pre>
 *
 * Everything sensitive (message text, file name, file size, file hash, file
 * bytes) lives inside {@code sealed}. The header is readable so a locked
 * receiver can still group chunks into one "incoming file" and show progress,
 * but it is bound to the ciphertext through the GCM tag: altering the type,
 * transfer id, index or total makes decryption fail, so frames cannot be
 * reordered, spliced into another transfer, or silently truncated.
 */
public final class SecureFrame {

    public static final byte TEXT = 1;
    public static final byte FILE_META = 2;
    public static final byte FILE_CHUNK = 3;

    public static final int HEADER_LEN = 13;
    private static final int MIN_SEALED = 12 + 16;
    /** Upper bound on a single frame (a 1 MB text message or a file chunk, plus overhead). */
    public static final int MAX_FRAME_BYTES = 1_100_000;

    public final byte type;
    public final int transferId;
    public final int index;
    public final int total;
    public final byte[] sealed;

    public SecureFrame(byte type, int transferId, int index, int total, byte[] sealed) {
        this.type = type;
        this.transferId = transferId;
        this.index = index;
        this.total = total;
        this.sealed = sealed;
    }

    private static byte[] header(byte type, int transferId, int index, int total) {
        return ByteBuffer.allocate(HEADER_LEN).put(type).putInt(transferId).putInt(index).putInt(total).array();
    }

    public byte[] header() {
        return header(type, transferId, index, total);
    }

    public static SecureFrame seal(byte[] key, byte type, int transferId, int index, int total, byte[] plaintext)
            throws Exception {
        byte[] sealed = CryptoUtil.encryptBytes(key, plaintext, header(type, transferId, index, total));
        return new SecureFrame(type, transferId, index, total, sealed);
    }

    /** Decrypts and authenticates; throws if the key is wrong or anything was tampered with. */
    public byte[] open(byte[] key) throws Exception {
        return CryptoUtil.decryptBytes(key, sealed, header());
    }

    /** Like {@link #open} but returns null instead of throwing. */
    public byte[] tryOpen(byte[] key) {
        try {
            return open(key);
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] encode() {
        return ByteBuffer.allocate(HEADER_LEN + sealed.length).put(header()).put(sealed).array();
    }

    public static SecureFrame decode(byte[] raw) throws IOException {
        if (raw.length < HEADER_LEN + MIN_SEALED) {
            throw new IOException("Frame too short (" + raw.length + " bytes)");
        }
        if (raw.length > MAX_FRAME_BYTES) {
            throw new IOException("Frame too large (" + raw.length + " bytes)");
        }
        ByteBuffer b = ByteBuffer.wrap(raw);
        byte type = b.get();
        if (type < TEXT || type > FILE_CHUNK) {
            throw new IOException("Unknown frame type " + type);
        }
        int transferId = b.getInt();
        int index = b.getInt();
        int total = b.getInt();
        if (index < 0 || total < 0) {
            throw new IOException("Invalid frame header");
        }
        return new SecureFrame(type, transferId, index, total, Arrays.copyOfRange(raw, HEADER_LEN, raw.length));
    }
}
