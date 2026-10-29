import quantumchat.core.*;
import quantumchat.net.*;
import java.net.*;

public class SocketSmokeTest {
    public static void main(String[] args) throws Exception {
        int port = 7711;
        QKDConfig config = QKDConfig.defaults().numQubits(4096).channelNoiseProbability(0.02);

        Thread serverThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                Socket s = ss.accept();
                MessageTransport t = new SocketMessageTransport(s);
                QKDChannel ch = new TransportQKDChannel(t);
                QKDResult r = new AliceProtocol().run(ch, config, new QKDProgressListener() {
                    public void onLog(String m) { System.out.println("[ALICE] " + m); }
                });
                System.out.println("ALICE RESULT: success=" + r.success + " reason=" + r.abortReason
                        + " qber=" + (r.stats != null ? r.stats.qber : -1));
                if (r.success) {
                    System.out.println("ALICE FINGERPRINT: " + quantumchat.crypto.CryptoUtil.fingerprint(r.aesKey));
                    // send one encrypted chat message to prove the transport is reusable post-handshake
                    byte[] payload = quantumchat.crypto.CryptoUtil.encrypt(r.aesKey, "hello over real TCP socket");
                    t.send(payload);
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        Thread clientThread = new Thread(() -> {
            try {
                Thread.sleep(500);
                Socket s = new Socket("localhost", port);
                MessageTransport t = new SocketMessageTransport(s);
                QKDChannel ch = new TransportQKDChannel(t);
                QKDResult r = new BobProtocol().run(ch, config, new QKDProgressListener() {
                    public void onLog(String m) { System.out.println("[BOB] " + m); }
                });
                System.out.println("BOB RESULT: success=" + r.success + " reason=" + r.abortReason
                        + " qber=" + (r.stats != null ? r.stats.qber : -1));
                if (r.success) {
                    System.out.println("BOB FINGERPRINT: " + quantumchat.crypto.CryptoUtil.fingerprint(r.aesKey));
                    byte[] incoming = t.receive();
                    String plaintext = quantumchat.crypto.CryptoUtil.decrypt(r.aesKey, incoming);
                    System.out.println("BOB DECRYPTED MESSAGE: " + plaintext);
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();
    }
}
