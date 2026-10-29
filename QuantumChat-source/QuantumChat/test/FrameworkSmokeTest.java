import quantumchat.core.*;
import quantumchat.net.*;

import java.util.concurrent.*;

public class FrameworkSmokeTest {
    public static void main(String[] args) throws Exception {
        System.out.println("== Test 1: clean channel, no eavesdropper ==");
        runCase(QKDConfig.defaults().numQubits(4096).randomSeed(42L));

        System.out.println("\n== Test 2: full intercept-resend eavesdropper (should ABORT) ==");
        runCase(QKDConfig.defaults().numQubits(4096)
                .eavesdropperMode(EavesdropperStrategy.Mode.FULL_INTERCEPT).randomSeed(42L));

        System.out.println("\n== Test 3: partial eavesdropper at 30% intercept (QBER ~7.5% is under the 11% threshold: should SUCCEED, with keys reconciled) ==");
        runCase(QKDConfig.defaults().numQubits(8192)
                .eavesdropperMode(EavesdropperStrategy.Mode.PARTIAL_INTERCEPT)
                .eavesdropProbability(0.3).randomSeed(7L));

        System.out.println("\n== Test 4: noisy channel only (5% flip, no eavesdropper), should still succeed ==");
        runCase(QKDConfig.defaults().numQubits(4096).channelNoiseProbability(0.05).randomSeed(99L));

        System.out.println("\n== Test 5: reproducibility -- same seed should give identical QBER across 2 runs ==");
        QKDConfig cfg = QKDConfig.defaults().numQubits(2048)
                .eavesdropperMode(EavesdropperStrategy.Mode.PARTIAL_INTERCEPT).eavesdropProbability(0.4);
        double q1 = runCase(cfg.randomSeed(123L));
        double q2 = runCase(cfg.randomSeed(123L));
        System.out.println("Seed-123 run A QBER=" + q1 + "  run B QBER=" + q2 + "  match=" + (q1 == q2));

        System.out.println("\nALL SMOKE TESTS COMPLETED");
    }

    static double runCase(QKDConfig config) throws Exception {
        InMemoryMessageTransport[] pair = InMemoryMessageTransport.createPair();
        QKDChannel aliceChannel = new TransportQKDChannel(pair[0]);
        QKDChannel bobChannel = new TransportQKDChannel(pair[1]);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<QKDResult> af = pool.submit(() -> new AliceProtocol().run(aliceChannel, config, new QKDProgressListener(){}));
        Future<QKDResult> bf = pool.submit(() -> new BobProtocol().run(bobChannel, config, new QKDProgressListener(){}));
        QKDResult a = af.get();
        QKDResult b = bf.get();
        pool.shutdown();

        System.out.println("  Alice: success=" + a.success + " reason=" + a.abortReason
                + " qber=" + (a.stats != null ? String.format("%.4f", a.stats.qber) : "n/a")
                + " siftedBits=" + (a.stats != null ? a.stats.siftedCount : -1)
                + " rawKeyBits=" + (a.stats != null ? a.stats.rawKeyBits : -1));
        System.out.println("  Bob:   success=" + b.success + " reason=" + b.abortReason
                + " qber=" + (b.stats != null ? String.format("%.4f", b.stats.qber) : "n/a"));

        if (a.success && b.success) {
            boolean keysMatch = java.util.Arrays.equals(a.aesKey, b.aesKey);
            System.out.println("  Keys match: " + keysMatch + "  (fingerprint=" + quantumchat.crypto.CryptoUtil.fingerprint(a.aesKey) + ")");
            if (!keysMatch) throw new AssertionError("Derived keys differ despite both sides succeeding!");
        }
        return a.stats != null ? a.stats.qber : -1;
    }
}
