package quantumchat.crypto;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Turns raw, sifted BB84 key bits into a usable AES-256 key (privacy
 * amplification via a cryptographic hash), and performs authenticated
 * encryption/decryption of chat messages with that key using AES/GCM.
 */
public final class CryptoUtil {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final SecureRandom RNG = new SecureRandom();

    private CryptoUtil() { }

    /**
     * Privacy amplification: compress the raw sifted key bits into a fixed
     * 256-bit AES key via SHA-256. This fixes the key length and washes out
     * any partial information an eavesdropper might have gained (bounded by
     * the QBER check).
     */
    public static byte[] deriveAesKey(int[] rawKeyBits) throws Exception {
        byte[] packed = packBits(rawKeyBits);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return sha256.digest(packed);
    }

    private static byte[] packBits(int[] bits) {
        byte[] bytes = new byte[(bits.length + 7) / 8];
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] == 1) {
                bytes[i / 8] |= (byte) (1 << (7 - (i % 8)));
            }
        }
        return bytes;
    }

    /** Encrypts UTF-8 plaintext; output is (random IV || ciphertext || auth tag). */
    public static byte[] encrypt(byte[] key, String plaintext) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        RNG.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return out;
    }

    public static String decrypt(byte[] key, byte[] data) throws Exception {
        byte[] iv = Arrays.copyOfRange(data, 0, GCM_IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(data, GCM_IV_BYTES, data.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plainBytes = cipher.doFinal(ciphertext);
        return new String(plainBytes, "UTF-8");
    }

    // ------------------------------------------------------------------
    // Byte-level AES-GCM with Additional Authenticated Data (AAD).
    // The AAD is NOT encrypted, but it is covered by the authentication tag:
    // changing even one bit of it makes decryption fail. The chat protocol
    // uses this to bind each frame's header (type, transfer id, chunk index,
    // chunk count) to its ciphertext, so frames can't be reordered, replayed
    // into a different transfer, or truncated without detection.
    // ------------------------------------------------------------------

    /** Output: IV (12 bytes) || ciphertext || tag (16 bytes). */
    public static byte[] encryptBytes(byte[] key, byte[] plaintext, byte[] aad) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        RNG.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        if (aad != null) {
            cipher.updateAAD(aad);
        }
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return out;
    }

    /** Throws (AEADBadTagException) if the key is wrong or the data/AAD was modified. */
    public static byte[] decryptBytes(byte[] key, byte[] data, byte[] aad) throws Exception {
        if (data.length < GCM_IV_BYTES + GCM_TAG_BITS / 8) {
            throw new java.security.GeneralSecurityException("Ciphertext too short");
        }
        byte[] iv = Arrays.copyOfRange(data, 0, GCM_IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(data, GCM_IV_BYTES, data.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        if (aad != null) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(ciphertext);
    }

    public static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    /**
     * Attempts to decrypt with the given key and returns null (instead of
     * throwing) if the key is wrong or the data was tampered with. AES-GCM's
     * authentication tag means a wrong key can never silently yield garbage
     * plaintext -- it always fails.
     */
    public static String tryDecrypt(byte[] key, byte[] data) {
        try {
            return decrypt(key, data);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Parses a hex string (whitespace, ':' and '-' separators are ignored).
     * @throws IllegalArgumentException if it isn't valid hex
     */
    public static byte[] fromHex(String text) {
        String s = text.replaceAll("[\\s:\\-]", "");
        if (s.isEmpty() || s.length() % 2 != 0 || !s.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("Not valid hexadecimal.");
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    public static String toBase64(byte[] data) {
        return java.util.Base64.getEncoder().encodeToString(data);
    }

    /** Short, non-secret fingerprint of a key, safe to display for a manual match check. */
    public static String fingerprint(byte[] key) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(key);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%02x", h[i]));
        }
        return sb.toString();
    }
}
