package quantumchat.gui;

import quantumchat.core.AliceProtocol;
import quantumchat.core.BobProtocol;
import quantumchat.core.QKDChannel;
import quantumchat.core.QKDConfig;
import quantumchat.core.QKDResult;
import quantumchat.net.InMemoryMessageTransport;
import quantumchat.net.TransportQKDChannel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs Alice and Bob inside this process, joined by an in-memory loopback
 * transport. Try any QKD configuration and see the outcome instantly; when a
 * key is established you get two chat panes (Alice and Bob) that exchange
 * encrypted messages and files over the simulated link.
 */
public class LocalSimulationPanel extends JPanel implements Disposable, Theme.Themed {

    private final QKDConfig config;
    private final Runnable onBack;

    private final ProtocolDashboard dashboard = new ProtocolDashboard();
    private final JPanel chatHost = new JPanel(new GridLayout(1, 2, 12, 0));
    private final Widgets.SecondaryButton rerunButton = new Widgets.SecondaryButton("Run again");
    private ChatPanel alicePane;
    private ChatPanel bobPane;
    private volatile int generation = 0;

    public LocalSimulationPanel(QKDConfig config, Runnable onBack) {
        this.config = config;
        this.onBack = onBack;

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        Widgets.SecondaryButton back = new Widgets.SecondaryButton("Back to setup");
        back.addActionListener(e -> onBack.run());
        rerunButton.addActionListener(e -> run());
        buttons.add(rerunButton);
        buttons.add(back);
        north.add(buttons, BorderLayout.NORTH);
        north.add(dashboard, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        chatHost.setOpaque(false);
        add(chatHost, BorderLayout.CENTER);
        applyTheme();
    }

    @Override
    public void applyTheme() {
        setOpaque(true);
        setBackground(Theme.p().bg);
    }

    public ChatPanel getAlicePane() {
        return alicePane;
    }

    public ChatPanel getBobPane() {
        return bobPane;
    }

    public void start() {
        run();
    }

    private void run() {
        final int myGeneration = ++generation;
        closeChats();
        dashboard.reset();
        dashboard.setConfig(config);
        dashboard.setLogVisible(true);
        dashboard.appendLog("Local in-memory simulation. Config: " + config);
        showPlaceholder(Icons.Kind.LAB, "Running the key exchange...", null);
        rerunButton.setEnabled(false);
        QuantumChatGUI.status("Local simulation running...");

        InMemoryMessageTransport[] pair = InMemoryMessageTransport.createPair();
        QKDChannel aliceChannel = new TransportQKDChannel(pair[0]);
        QKDChannel bobChannel = new TransportQKDChannel(pair[1]);

        ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "qkd-sim");
            t.setDaemon(true);
            return t;
        });
        Future<QKDResult> aliceFuture = pool.submit(
                () -> new AliceProtocol().run(aliceChannel, config, dashboard.forParty("Alice", true)));
        Future<QKDResult> bobFuture = pool.submit(
                () -> new BobProtocol().run(bobChannel, config, dashboard.forParty("Bob", false)));

        new SwingWorker<Void, Void>() {
            QKDResult aliceResult;
            QKDResult bobResult;

            @Override
            protected Void doInBackground() throws Exception {
                aliceResult = aliceFuture.get();
                bobResult = bobFuture.get();
                return null;
            }

            @Override
            protected void done() {
                pool.shutdown();
                if (myGeneration != generation) {
                    return; // superseded by a newer run, or the screen was closed
                }
                rerunButton.setEnabled(true);
                boolean ok = aliceResult != null && aliceResult.success && bobResult != null && bobResult.success;
                if (ok) {
                    dashboard.showOutcome(true, "Key established on both sides. Chat below (simulated link).");
                    dashboard.setLogVisible(false);
                    buildChats(pair, aliceResult.aesKey);
                    QuantumChatGUI.status("Local simulation: key established");
                } else {
                    String reason = aliceResult != null && !aliceResult.success ? aliceResult.abortReason
                            : (bobResult != null ? bobResult.abortReason : "Unknown failure");
                    dashboard.showOutcome(false, "Key exchange failed: " + reason);
                    showPlaceholder(Icons.Kind.WARN, "No shared key was established, so there is nothing to chat over.",
                            "This is the protocol working as intended: the error rate gave the eavesdropper away.");
                    QuantumChatGUI.status("Local simulation: aborted");
                }
            }
        }.execute();
    }

    private void buildChats(InMemoryMessageTransport[] pair, byte[] key) {
        alicePane = new ChatPanel(pair[0], key, "Alice", () -> { });
        bobPane = new ChatPanel(pair[1], key, "Bob", () -> { });
        chatHost.removeAll();
        chatHost.setLayout(new GridLayout(1, 2, 12, 0));
        chatHost.add(alicePane);
        chatHost.add(bobPane);
        alicePane.start();
        bobPane.start();
        chatHost.revalidate();
        chatHost.repaint();
    }

    private void showPlaceholder(Icons.Kind icon, String title, String subtitle) {
        Theme.Palette p = Theme.p();
        Widgets.RoundedPanel card = new Widgets.RoundedPanel(new BorderLayout(0, 6));
        JLabel t = new JLabel(title, Icons.of(icon, 30, icon == Icons.Kind.WARN ? p.warn : p.muted), SwingConstants.CENTER);
        t.setFont(Theme.font(Font.BOLD, 14f));
        t.setVerticalTextPosition(SwingConstants.BOTTOM);
        t.setHorizontalTextPosition(SwingConstants.CENTER);
        t.setIconTextGap(10);
        card.add(t, BorderLayout.CENTER);
        if (subtitle != null) {
            JLabel s = new JLabel(subtitle, SwingConstants.CENTER);
            s.setForeground(p.muted);
            card.add(s, BorderLayout.SOUTH);
        }
        chatHost.removeAll();
        chatHost.setLayout(new BorderLayout());
        chatHost.add(card, BorderLayout.CENTER);
        chatHost.revalidate();
        chatHost.repaint();
    }

    private void closeChats() {
        if (alicePane != null) {
            alicePane.dispose();
        }
        if (bobPane != null) {
            bobPane.dispose();
        }
        alicePane = null;
        bobPane = null;
        chatHost.removeAll();
        chatHost.setLayout(new GridLayout(1, 2, 12, 0));
    }

    @Override
    public void dispose() {
        generation++;
        closeChats();
    }
}
