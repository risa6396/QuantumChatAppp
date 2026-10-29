import quantumchat.core.*;
import quantumchat.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ReconciliationDebug {
    public static void main(String[] args) throws Exception {
        int len = 3100;
        double errRate = 0.0532;
        Random seedRng = new Random(1);
        int[] alice = new int[len];
        for (int i=0;i<len;i++) alice[i] = seedRng.nextInt(2);
        int[] bob = alice.clone();
        int flips = 0;
        for (int i=0;i<len;i++) {
            if (seedRng.nextDouble() < errRate) { bob[i] ^= 1; flips++; }
        }
        System.out.println("Initial injected flips: " + flips);
        System.out.println("Initial hamming distance: " + hamming(alice, bob));

        InMemoryMessageTransport[] pair = InMemoryMessageTransport.createPair();
        QKDChannel aliceCh = new TransportQKDChannel(pair[0]);
        QKDChannel bobCh = new TransportQKDChannel(pair[1]);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Random aliceRng = new Random(55);
        Random bobRng = new Random(77); // bob's rng unused by corrector side but required by signature

        QKDProgressListener listener = new QKDProgressListener() {
            @Override public void onLog(String m) { System.out.println(m); }
        };
        Future<?> af = pool.submit(() -> {
            try { ErrorReconciliation.reconcileAsReference(alice, errRate, aliceCh, aliceRng, listener); }
            catch (Exception e) { e.printStackTrace(); }
        });
        Future<?> bf = pool.submit(() -> {
            try { ErrorReconciliation.reconcileAsCorrector(bob, errRate, bobCh, bobRng, listener); }
            catch (Exception e) { e.printStackTrace(); }
        });
        af.get(); bf.get();
        pool.shutdown();

        System.out.println("Final hamming distance: " + hamming(alice, bob));
    }

    static int hamming(int[] a, int[] b) {
        int d = 0;
        for (int i=0;i<a.length;i++) if (a[i]!=b[i]) d++;
        return d;
    }
}
