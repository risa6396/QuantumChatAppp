import quantumchat.core.*;
import quantumchat.crypto.CryptoUtil;
import quantumchat.net.*;
import java.util.*;
import java.util.concurrent.*;

public class KeyGateTest {
    public static void main(String[] args) throws Exception {
        QKDConfig cfg = QKDConfig.defaults().randomSeed(2024L);
        InMemoryMessageTransport[] p = InMemoryMessageTransport.createPair();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<QKDResult> a = pool.submit(() -> new AliceProtocol().run(new TransportQKDChannel(p[0]), cfg, new QKDProgressListener(){}));
        Future<QKDResult> b = pool.submit(() -> new BobProtocol().run(new TransportQKDChannel(p[1]), cfg, new QKDProgressListener(){}));
        byte[] key = a.get().aesKey; pool.shutdown();
        System.out.println("SEEDKEY=" + CryptoUtil.toHex(key));

        byte[] payload = CryptoUtil.encrypt(key, "top secret message");
        byte[] wrong = key.clone(); wrong[0] ^= 1;   // differs by a single bit
        System.out.println("right key  -> " + CryptoUtil.tryDecrypt(key, payload));
        System.out.println("1-bit-off  -> " + CryptoUtil.tryDecrypt(wrong, payload));
        System.out.println("hex roundtrip ok: " + Arrays.equals(key, CryptoUtil.fromHex(CryptoUtil.toHex(key))));
        System.out.println("spaced/uppercase hex ok: " + Arrays.equals(key,
                CryptoUtil.fromHex(CryptoUtil.toHex(key).toUpperCase().replaceAll("(..)", "$1 ").trim())));
        try { CryptoUtil.fromHex("xyz"); System.out.println("BAD: accepted xyz"); }
        catch (IllegalArgumentException e) { System.out.println("invalid hex rejected: ok"); }
    }
}
