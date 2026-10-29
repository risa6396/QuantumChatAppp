package quantumchat.gui;

import quantumchat.crypto.CryptoUtil;
import quantumchat.net.MessageTransport;

import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;

/** The application window: a menu bar, one screen at a time, and a status bar. */
public class QuantumChatGUI extends JFrame implements Theme.Themed {

    private static QuantumChatGUI instance;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final SetupPanel setupPanel;
    private final JLabel statusLabel = new JLabel("Ready");
    private final JPanel statusBar = new JPanel(new BorderLayout());
    private final JCheckBoxMenuItem darkItem = new JCheckBoxMenuItem("Dark mode");
    private Component session;
    private int cardCounter = 0;

    public QuantumChatGUI() {
        super("Quantum-Secured Chat");
        instance = this;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(Icons.image(Icons.Kind.SHIELD, 64, Theme.p().accent));

        setupPanel = new SetupPanel(this::startSession);
        cards.add(setupPanel, "setup");

        statusLabel.setFont(Theme.font(Font.PLAIN, 12f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        setJMenuBar(buildMenuBar());
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(cards, BorderLayout.CENTER);
        getContentPane().add(statusBar, BorderLayout.SOUTH);

        setMinimumSize(new Dimension(900, 640));
        setPreferredSize(new Dimension(1060, 820));
        pack();
        setLocationRelativeTo(null);
        applyTheme();
    }

    // ================================================================= menu / status

    @SuppressWarnings("deprecation")
    private JMenuBar buildMenuBar() {
        JMenuBar bar = new Widgets.ThemedMenuBar();

        JMenu sessionMenu = new JMenu("Session");
        JMenuItem newSession = new JMenuItem("New session");
        newSession.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        newSession.addActionListener(e -> showSetup());
        JMenuItem quit = new JMenuItem("Quit");
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        quit.addActionListener(e -> System.exit(0));
        sessionMenu.add(newSession);
        sessionMenu.addSeparator();
        sessionMenu.add(quit);

        JMenu viewMenu = new JMenu("View");
        darkItem.addActionListener(e -> setDarkMode(darkItem.isSelected()));
        viewMenu.add(darkItem);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem about = new JMenuItem("About / how it works");
        about.addActionListener(e -> showAbout());
        helpMenu.add(about);

        bar.add(sessionMenu);
        bar.add(viewMenu);
        bar.add(helpMenu);
        return bar;
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><body style='width:380px'>"
                        + "<b>Quantum-Secured Chat</b><br><br>"
                        + "Two peers agree on a secret key with a <b>simulated BB84</b> quantum key exchange "
                        + "(sifting, error-rate estimation, eavesdropper detection, error reconciliation, privacy "
                        + "amplification). That key then protects everything they send with <b>AES-256-GCM</b>: "
                        + "chat messages and files.<br><br>"
                        + "What you receive stays encrypted until you enter the session key. Files are sent in "
                        + "authenticated 32 KB chunks and checked against a SHA-256 when saved.<br><br>"
                        + "A TCP socket cannot carry real qubits, so BB84 is simulated statistically; the actual "
                        + "confidentiality comes from AES-256-GCM."
                        + "</body></html>",
                "About", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Shows a short message in the status bar (safe to call from any thread). */
    public static void status(String text) {
        SwingUtilities.invokeLater(() -> {
            if (instance != null) {
                instance.statusLabel.setText(text);
            }
        });
    }

    // ================================================================= theme

    public void setDarkMode(boolean dark) {
        Theme.setDark(dark);
        darkItem.setSelected(dark);
        Theme.install();
        SwingUtilities.updateComponentTreeUI(this);
        Theme.applyTree(this);
        applyTheme();
        revalidate();
        repaint();
    }

    @Override
    public void applyTheme() {
        Theme.Palette p = Theme.p();
        cards.setBackground(p.bg);
        statusBar.setBackground(p.surfaceAlt);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, p.border),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        statusLabel.setForeground(p.muted);
    }

    // ================================================================= sessions

    public void showSetup() {
        closeSession();
        cardLayout.show(cards, "setup");
        status("Ready");
    }

    /** Starts a session exactly as if the user had pressed Start with these parameters. */
    public void startSession(SetupPanel.Params params) {
        closeSession();
        if (params.mode == SetupPanel.Mode.LOCAL_SIM) {
            LocalSimulationPanel panel = new LocalSimulationPanel(params.config, this::showSetup);
            showSessionCard(panel);
            panel.start();
        } else {
            QKDProgressPanel panel = new QKDProgressPanel(params, this::onKeyExchangeDone, this::showSetup);
            showSessionCard(panel);
            panel.start();
        }
    }

    private void onKeyExchangeDone(MessageTransport transport, byte[] aesKey, boolean isHost) {
        String fp;
        try {
            fp = CryptoUtil.fingerprint(aesKey);
        } catch (Exception e) {
            fp = "?";
        }
        closeSession();
        ChatPanel chat = new ChatPanel(transport, aesKey, isHost ? "Alice (host)" : "Bob (joined)", this::showSetup);
        showSessionCard(chat);
        chat.start();
        status("Secure session  \u2022  key fingerprint " + fp + "  \u2022  everything you receive is locked until you enter the key");
    }

    /** The screen currently showing a session (key exchange, simulation or chat), or null on the setup screen. */
    public Component currentSession() {
        return session;
    }

    private void showSessionCard(Component panel) {
        String id = "card" + (cardCounter++);
        cards.add(panel, id);
        cardLayout.show(cards, id);
        session = panel;
    }

    private void closeSession() {
        if (session != null) {
            if (session instanceof Disposable) {
                ((Disposable) session).dispose();
            }
            cards.remove(session);
            session = null;
        }
    }

    // ================================================================= main

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new QuantumChatGUI().setVisible(true);
        });
    }
}
