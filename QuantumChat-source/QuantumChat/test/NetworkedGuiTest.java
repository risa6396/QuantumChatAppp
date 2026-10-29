import quantumchat.chat.ChatConnection;
import quantumchat.core.*;
import quantumchat.net.*;
import quantumchat.crypto.CryptoUtil;
import quantumchat.gui.*;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Exercises the GUI's HOST mode over a REAL TCP socket: the GUI listens and runs Alice;
 * a plain protocol client plays Bob, sends a text and a file, and we verify that the GUI
 * ends up in a locked chat, unlocks with the key, and saves a byte-identical file.
 */
public class NetworkedGuiTest {
    static final String RIGHT = "a8ee4a9e89d23f779bbe22d47be06e09eadd92712e58e590efd27bce58b52e57";
    static QuantumChatGUI gui;

    public static void main(String[] a) throws Exception {
        File out = new File(a[0]); out.mkdirs();
        File src = new File(out, "demo_files/quarterly_report.csv");
        int port = 5599;
        QKDConfig cfg = QKDConfig.defaults().randomSeed(2024L);

        SwingUtilities.invokeAndWait(() -> { Theme.install(); gui = new QuantumChatGUI(); gui.setVisible(true);
            gui.startSession(new SetupPanel.Params(SetupPanel.Mode.HOST, "", port, cfg)); });

        // the remote peer (Bob), over a real socket
        Thread peer = new Thread(() -> {
            try {
                Socket s = null;
                for (int i = 0; i < 100 && s == null; i++) {
                    try { s = new Socket("localhost", port); } catch (Exception e) { Thread.sleep(100); }
                }
                SocketMessageTransport t = new SocketMessageTransport(s);
                QKDResult r = new BobProtocol().run(new TransportQKDChannel(t), cfg, new QKDProgressListener() {});
                System.out.println("remote Bob key exchange success=" + r.success);
                ChatConnection c = new ChatConnection(t, r.aesKey);
                c.sendText("Hello from a remote peer over TCP.");
                c.sendFile(src, null);
                Thread.sleep(60000);
            } catch (Exception e) { e.printStackTrace(); }
        });
        peer.setDaemon(true);
        peer.start();

        long end = System.currentTimeMillis() + 30000;
        while (!(gui.currentSession() instanceof ChatPanel) && System.currentTimeMillis() < end) Thread.sleep(50);
        if (!(gui.currentSession() instanceof ChatPanel)) throw new RuntimeException("GUI never reached the chat screen");
        System.out.println("GUI reached the chat screen after the key exchange");
        ChatPanel chat = (ChatPanel) gui.currentSession();
        Thread.sleep(2000);
        shoot(out, "net_1_locked");

        boolean[] ok = new boolean[1];
        SwingUtilities.invokeAndWait(() -> ok[0] = chat.unlockWithKeyHex(RIGHT));
        System.out.println("unlock with the right key: " + ok[0]);
        File saved = new File(out, "saved/net_report.csv"); saved.getParentFile().mkdirs(); saved.delete();
        SwingUtilities.invokeAndWait(() -> chat.saveLastReceivedFile(saved));
        end = System.currentTimeMillis() + 10000;
        while ((!saved.exists() || saved.length() != src.length()) && System.currentTimeMillis() < end) Thread.sleep(50);
        Thread.sleep(600);
        boolean same = saved.exists() && Arrays.equals(Files.readAllBytes(src.toPath()), Files.readAllBytes(saved.toPath()));
        System.out.println("FILE RECEIVED OVER TCP IDENTICAL: " + same);
        shoot(out, "net_2_unlocked_saved");
        System.exit(same && ok[0] ? 0 : 1);
    }

    static void shoot(File out, String name) throws Exception {
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage img = new BufferedImage(gui.getRootPane().getWidth(), gui.getRootPane().getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); gui.getRootPane().paint(g); g.dispose();
            try { ImageIO.write(img, "png", new File(out, name + ".png")); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
