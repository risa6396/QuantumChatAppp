import quantumchat.core.EavesdropperStrategy;
import quantumchat.core.QKDConfig;
import quantumchat.gui.*;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/**
 * Drives the real GUI in-process and saves a screenshot of every important
 * state. Usage: java GuiShowcase <output-dir>   (needs a display, e.g. xvfb-run)
 */
public class GuiShowcase {
    static QuantumChatGUI gui;
    static File outDir;
    // Session key produced by a local simulation with seed 2024 and default parameters.
    static final String RIGHT = "a8ee4a9e89d23f779bbe22d47be06e09eadd92712e58e590efd27bce58b52e57";
    static final String WRONG = "a8ee4a9e89d23f779bbe22d47be06e09eadd92712e58e590efd27bce58b52e58";

    static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    static void waitFor(String what, BooleanSupplier c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) return;
            Thread.sleep(50);
        }
        throw new RuntimeException("timed out waiting for " + what);
    }

    static void shoot(String name) throws Exception {
        Thread.sleep(350);   // let layout/animation settle
        edt(() -> {
            BufferedImage img = new BufferedImage(gui.getRootPane().getWidth(), gui.getRootPane().getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            gui.getRootPane().paint(g);
            g.dispose();
            try { ImageIO.write(img, "png", new File(outDir, name + ".png")); } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("saved " + name + ".png");
    }

    static LocalSimulationPanel sim() { return (LocalSimulationPanel) gui.currentSession(); }

    static void startLocal(QKDConfig cfg) throws Exception {
        edt(() -> gui.startSession(new SetupPanel.Params(SetupPanel.Mode.LOCAL_SIM, "", 0, cfg)));
    }

    public static void main(String[] a) throws Exception {
        outDir = new File(a[0]);
        outDir.mkdirs();
        File demo = new File(outDir, "demo_files");
        demo.mkdirs();

        // demo file: a ~150 KB CSV
        File report = new File(demo, "quarterly_report.csv");
        StringBuilder sb = new StringBuilder("quarter,region,units,revenue\n");
        java.util.Random r = new java.util.Random(5);
        for (int i = 0; i < 4500; i++) sb.append("Q").append(1 + i % 4).append(",R").append(i % 17).append(",").append(r.nextInt(5000)).append(",").append(r.nextInt(900000) / 10.0).append("\n");
        Files.write(report.toPath(), sb.toString().getBytes("UTF-8"));
        System.out.println("demo file: " + report.length() + " bytes");

        edt(() -> { Theme.install(); gui = new QuantumChatGUI(); gui.setVisible(true); });
        shoot("01_setup_light");

        edt(() -> gui.setDarkMode(true));
        shoot("02_setup_dark");
        edt(() -> gui.setDarkMode(false));

        // ---- local simulation, then chat with a file transfer ----
        startLocal(QKDConfig.defaults().randomSeed(2024L));
        waitFor("key exchange", () -> sim().getBobPane() != null, 30000);
        shoot("03_key_exchange_done");

        ChatPanel alice = sim().getAlicePane(), bob = sim().getBobPane();
        edt(() -> { alice.sendText("Meet at the old bridge at noon."); alice.sendFile(report); });
        Thread.sleep(1500);
        edt(() -> bob.sendText("Understood. I will bring the originals."));
        Thread.sleep(600);
        shoot("04_locked_text_and_file");

        boolean[] r1 = new boolean[2];
        edt(() -> r1[0] = bob.unlockWithKeyHex(WRONG));
        shoot("05_wrong_key");
        edt(() -> r1[1] = bob.unlockWithKeyHex(RIGHT));
        System.out.println("unlock(wrong)=" + r1[0] + " unlock(right)=" + r1[1]);
        shoot("06_unlocked_file_card");

        File saved = new File(new File(outDir, "saved"), "quarterly_report.csv");
        saved.getParentFile().mkdirs();
        saved.delete();
        edt(() -> bob.saveLastReceivedFile(saved));
        waitFor("saved file", () -> saved.exists() && saved.length() == report.length(), 10000);
        Thread.sleep(700);
        boolean same = Arrays.equals(Files.readAllBytes(report.toPath()), Files.readAllBytes(saved.toPath()));
        System.out.println("SAVED FILE IDENTICAL TO ORIGINAL: " + same);
        shoot("07_file_saved");

        // ---- eavesdropper ----
        startLocal(QKDConfig.defaults().eavesdropperMode(EavesdropperStrategy.Mode.FULL_INTERCEPT).randomSeed(7L));
        Thread.sleep(3500);
        shoot("08_eavesdropper_detected");

        // ---- dark-mode chat ----
        edt(() -> gui.setDarkMode(true));
        startLocal(QKDConfig.defaults().randomSeed(2024L));
        waitFor("key exchange", () -> sim().getBobPane() != null, 30000);
        ChatPanel a2 = sim().getAlicePane(), b2 = sim().getBobPane();
        edt(() -> { a2.sendText("Dark mode looks good too."); a2.sendFile(report); });
        Thread.sleep(1500);
        edt(() -> b2.unlockWithKeyHex(RIGHT));
        shoot("09_dark_chat");
        edt(() -> gui.setDarkMode(false));
        shoot("10_light_after_live_switch");

        System.out.println("SHOWCASE DONE");
        System.exit(0);
    }
}
