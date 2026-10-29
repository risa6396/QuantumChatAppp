package quantumchat.gui;

import quantumchat.core.EavesdropperStrategy;
import quantumchat.core.QKDConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/** The landing screen: choose a role, pick a channel scenario (or tune every parameter), and start. */
public class SetupPanel extends JPanel implements Theme.Themed {

    public enum Mode { HOST, JOIN, LOCAL_SIM }

    public static final class Params {
        public final Mode mode;
        public final String host;
        public final int port;
        public final QKDConfig config;

        public Params(Mode mode, String host, int port, QKDConfig config) {
            this.mode = mode;
            this.host = host;
            this.port = port;
            this.config = config;
        }
    }

    private static final String[] PRESETS = {
        "Ideal channel (no noise, no eavesdropper)",
        "Noisy channel (3% bit-flip noise)",
        "Weak eavesdropper (intercepts 30% of qubits)",
        "Strong eavesdropper (intercepts every qubit)",
        "Noisy channel + weak eavesdropper",
        "Custom"
    };

    private final Consumer<Params> onStart;

    private final RoleCard hostCard = new RoleCard(Icons.Kind.HOST, "Host a session",
            "Play Alice: open a port and wait for a peer to connect.");
    private final RoleCard joinCard = new RoleCard(Icons.Kind.JOIN, "Join a session",
            "Play Bob: connect to a host's address and port.");
    private final RoleCard localCard = new RoleCard(Icons.Kind.LAB, "Local simulation",
            "No network: run both sides here and test any scenario instantly.");

    private final Widgets.PromptTextField hostField = new Widgets.PromptTextField("e.g. 192.168.1.20");
    private final JSpinner portSpinner = new Widgets.StyledSpinner(new SpinnerNumberModel(5000, 1, 65535, 1));

    private final JComboBox<String> presetCombo = new Widgets.StyledCombo<>(PRESETS);
    private final JSpinner numQubitsSpinner = new Widgets.StyledSpinner(new SpinnerNumberModel(4096, 256, 65536, 256));
    private final JSpinner sampleFractionSpinner = new Widgets.StyledSpinner(new SpinnerNumberModel(0.25, 0.05, 0.9, 0.05));
    private final JSpinner qberThresholdSpinner = new Widgets.StyledSpinner(new SpinnerNumberModel(0.11, 0.01, 0.30, 0.01));
    private final JSpinner noiseSpinner = new Widgets.StyledSpinner(new SpinnerNumberModel(0.0, 0.0, 0.30, 0.01));
    private final JComboBox<String> eveCombo = new Widgets.StyledCombo<>(new String[] {
        "None", "Full intercept-resend", "Partial intercept-resend"
    });
    private final JSpinner eveProbSpinner = new Widgets.StyledSpinner(new SpinnerNumberModel(0.5, 0.0, 1.0, 0.05));
    private final Widgets.PromptTextField seedField = new Widgets.PromptTextField("blank = truly random");

    private final JLabel estimateLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" ");
    private final Widgets.PrimaryButton startButton = new Widgets.PrimaryButton("Start secure session");
    private final JLabel[] sectionLabels = new JLabel[3];
    private final JLabel[] rowLabels = new JLabel[10];
    private Banner banner;
    private boolean applyingPreset = false;

    public SetupPanel(Consumer<Params> onStart) {
        this.onStart = onStart;
        buildUI();
        applyTheme();
    }

    // ================================================================= UI

    private void buildUI() {
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));

        banner = new Banner();
        add(banner, BorderLayout.NORTH);

        // ---- left column: role + connection ----
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(section(0, "Session type"));
        left.add(Box.createVerticalStrut(6));
        for (RoleCard c : new RoleCard[] {hostCard, joinCard, localCard}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            left.add(c);
            left.add(Box.createVerticalStrut(8));
        }
        ButtonGroup group = new ButtonGroup();
        group.add(hostCard);
        group.add(joinCard);
        group.add(localCard);
        hostCard.setSelected(true);
        for (RoleCard c : new RoleCard[] {hostCard, joinCard, localCard}) {
            c.addActionListener(e -> updateEnabledFields());
        }
        hostField.setText("localhost");

        left.add(Box.createVerticalStrut(4));
        left.add(section(1, "Connection"));
        left.add(Box.createVerticalStrut(6));
        Widgets.RoundedPanel conn = new Widgets.RoundedPanel(new GridBagLayout());
        conn.setAlignmentX(LEFT_ALIGNMENT);
        int r = 0;
        r = addRow(conn, r, 0, "Host / IP", hostField, "Address of the host to join (Join only)");
        addRow(conn, r, 1, "Port", portSpinner, "TCP port to listen on / connect to");
        conn.setMaximumSize(new Dimension(Integer.MAX_VALUE, conn.getPreferredSize().height));
        left.add(conn);
        left.add(Box.createVerticalGlue());
        left.setPreferredSize(new Dimension(350, 400));

        // ---- right column: scenario + parameters ----
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(section(2, "Channel scenario and QKD parameters"));
        right.add(Box.createVerticalStrut(6));
        Widgets.RoundedPanel params = new Widgets.RoundedPanel(new GridBagLayout());
        params.setAlignmentX(LEFT_ALIGNMENT);
        int row = 0;
        row = addRow(params, row, 2, "Scenario preset", presetCombo, "Fills in the fields below; edit any field to switch to Custom");
        row = addRow(params, row, 3, "Number of qubits", numQubitsSpinner, "More qubits give a longer key but a slower handshake");
        row = addRow(params, row, 4, "Sample fraction", sampleFractionSpinner, "Share of the sifted key sacrificed to estimate the error rate");
        row = addRow(params, row, 5, "QBER abort threshold", qberThresholdSpinner, "Abort above this error rate (11% is the standard BB84 security bound)");
        row = addRow(params, row, 6, "Channel noise", noiseSpinner, "Probability that the channel flips a bit, independent of any attacker");
        row = addRow(params, row, 7, "Eavesdropper", eveCombo, "Intercept-resend attack model");
        row = addRow(params, row, 8, "Intercept probability", eveProbSpinner, "Share of qubits a partial eavesdropper touches");
        row = addRow(params, row, 9, "Random seed", seedField, "Set a whole number to reproduce a run exactly");
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(10, 4, 2, 4);
        estimateLabel.setFont(Theme.font(Font.BOLD, 12.5f));
        params.add(estimateLabel, c);
        params.setMaximumSize(new Dimension(Integer.MAX_VALUE, params.getPreferredSize().height));
        right.add(params);
        right.add(Box.createVerticalGlue());

        JPanel body = new JPanel(new BorderLayout(18, 0));
        body.setOpaque(false);
        body.add(left, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // ---- footer ----
        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        errorLabel.setFont(Theme.font(Font.PLAIN, 12.5f));
        bottom.add(errorLabel, BorderLayout.CENTER);
        JPanel buttonWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonWrap.setOpaque(false);
        buttonWrap.add(startButton);
        bottom.add(buttonWrap, BorderLayout.EAST);
        startButton.addActionListener(e -> attemptStart());
        add(bottom, BorderLayout.SOUTH);

        // ---- behaviour ----
        presetCombo.addActionListener(e -> {
            if (!applyingPreset) {
                applyPreset(presetCombo.getSelectedIndex());
            }
        });
        eveCombo.addActionListener(e -> fieldEdited());
        for (JSpinner s : new JSpinner[] {numQubitsSpinner, sampleFractionSpinner, qberThresholdSpinner,
                noiseSpinner, eveProbSpinner}) {
            s.addChangeListener(e -> fieldEdited());
        }
        updateEnabledFields();
        updateEstimate();
    }

    private JLabel section(int idx, String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 13f));
        l.setAlignmentX(LEFT_ALIGNMENT);
        sectionLabels[idx] = l;
        return l;
    }

    private int addRow(JPanel panel, int row, int labelIdx, String label, JComponent field, String tip) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = row;
        JLabel l = new JLabel(label);
        l.setToolTipText(tip);
        field.setToolTipText(tip);
        rowLabels[labelIdx] = l;
        c.gridx = 0;
        c.weightx = 0;
        panel.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 0, 5, 4);
        field.setPreferredSize(new Dimension(230, field.getPreferredSize().height));
        panel.add(field, c);
        return row + 1;
    }

    @Override
    public void applyTheme() {
        Theme.Palette p = Theme.p();
        setOpaque(true);
        setBackground(p.bg);
        for (JLabel l : sectionLabels) {
            if (l != null) {
                l.setForeground(p.text);
            }
        }
        for (JLabel l : rowLabels) {
            if (l != null) {
                l.setForeground(p.muted);
            }
        }
        startButton.setIcon(Icons.of(Icons.Kind.SHIELD, 18, Color.WHITE));
        updateEstimate();
        repaint();
    }

    // ================================================================= presets and estimate

    private void applyPreset(int i) {
        if (i == PRESETS.length - 1) {
            return; // "Custom" keeps whatever is entered
        }
        applyingPreset = true;
        try {
            double noise = 0;
            int eve = 0;
            double prob = 0.5;
            switch (i) {
                case 1: noise = 0.03; break;
                case 2: eve = 2; prob = 0.3; break;
                case 3: eve = 1; break;
                case 4: noise = 0.03; eve = 2; prob = 0.3; break;
                default: break;
            }
            noiseSpinner.setValue(noise);
            eveCombo.setSelectedIndex(eve);
            eveProbSpinner.setValue(prob);
        } finally {
            applyingPreset = false;
        }
        updateEnabledFields();
        updateEstimate();
    }

    private void fieldEdited() {
        updateEnabledFields();
        if (!applyingPreset) {
            applyingPreset = true;
            presetCombo.setSelectedIndex(PRESETS.length - 1);
            applyingPreset = false;
        }
        updateEstimate();
    }

    /** A back-of-the-envelope QBER forecast: intercept-resend adds ~25% x intercept share; noise adds its own flips. */
    private void updateEstimate() {
        double noise = (Double) noiseSpinner.getValue();
        int mode = eveCombo.getSelectedIndex();
        double eveErr = mode == 1 ? 0.25 : mode == 2 ? 0.25 * (Double) eveProbSpinner.getValue() : 0.0;
        double est = eveErr + noise - 2 * eveErr * noise;
        double thr = (Double) qberThresholdSpinner.getValue();
        Theme.Palette p = Theme.p();
        String verdict;
        Color color;
        if (est > thr) {
            verdict = "the exchange will be aborted (eavesdropper suspected)";
            color = p.err;
        } else if (est > thr * 0.75) {
            verdict = "close to the threshold, so random fluctuation may abort it";
            color = p.warn;
        } else {
            verdict = "the exchange should succeed";
            color = p.ok;
        }
        estimateLabel.setText(String.format("Expected QBER ~ %.1f%%  -  %s", est * 100, verdict));
        estimateLabel.setForeground(color);
    }

    private void updateEnabledFields() {
        boolean join = joinCard.isSelected();
        boolean local = localCard.isSelected();
        hostField.setEnabled(join);
        portSpinner.setEnabled(!local);
        eveProbSpinner.setEnabled(eveCombo.getSelectedIndex() == 2);
    }

    // ================================================================= start

    private void attemptStart() {
        errorLabel.setText(" ");
        try {
            Mode mode = hostCard.isSelected() ? Mode.HOST : joinCard.isSelected() ? Mode.JOIN : Mode.LOCAL_SIM;
            String host = hostField.getText().trim();
            int port = (Integer) portSpinner.getValue();
            if (mode == Mode.JOIN && host.isEmpty()) {
                throw new IllegalArgumentException("Please enter the host or IP address to join.");
            }

            QKDConfig config = QKDConfig.defaults()
                    .numQubits((Integer) numQubitsSpinner.getValue())
                    .sampleFraction((Double) sampleFractionSpinner.getValue())
                    .qberAbortThreshold((Double) qberThresholdSpinner.getValue())
                    .channelNoiseProbability((Double) noiseSpinner.getValue())
                    .eavesdropperMode(eveModeFromCombo())
                    .eavesdropProbability((Double) eveProbSpinner.getValue());

            String seedText = seedField.getText().trim();
            if (!seedText.isEmpty()) {
                config.randomSeed(Long.parseLong(seedText));
            }
            config.validate();
            onStart.accept(new Params(mode, host, port, config));
        } catch (NumberFormatException nfe) {
            showError("The random seed must be a whole number.");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setForeground(Theme.p().err);
        errorLabel.setText(msg);
    }

    private EavesdropperStrategy.Mode eveModeFromCombo() {
        switch (eveCombo.getSelectedIndex()) {
            case 1: return EavesdropperStrategy.Mode.FULL_INTERCEPT;
            case 2: return EavesdropperStrategy.Mode.PARTIAL_INTERCEPT;
            default: return EavesdropperStrategy.Mode.NONE;
        }
    }

    // ================================================================= custom widgets

    /** The gradient title banner. */
    private static final class Banner extends JPanel {
        Banner() {
            setOpaque(false);
            setPreferredSize(new Dimension(100, 88));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.enableAntialias(g2);
            Theme.Palette p = Theme.p();
            int w = getWidth();
            int h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, p.accent, w, h, Widgets.mix(p.accent, new Color(0x3A1F8F), 0.7f)));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 20, 20));
            Icons.of(Icons.Kind.SHIELD, 46, Color.WHITE).paintIcon(this, g2, 24, (h - 46) / 2);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.font(Font.BOLD, 22f));
            g2.drawString("Quantum-Secured Chat", 90, h / 2 - 2);
            g2.setColor(new Color(255, 255, 255, 215));
            g2.setFont(Theme.font(Font.PLAIN, 13f));
            g2.drawString("BB84 key exchange simulation  \u2022  AES-256-GCM messaging  \u2022  encrypted file transfer", 91, h / 2 + 20);
            g2.dispose();
        }
    }

    /** A large selectable card: icon, title and a one-line description. */
    private static final class RoleCard extends JToggleButton {
        private final Icons.Kind kind;
        private final String title;
        private final String desc;
        private boolean hover;

        RoleCard(Icons.Kind kind, String title, String desc) {
            this.kind = kind;
            this.title = title;
            this.desc = desc;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(340, 76));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.enableAntialias(g2);
            Theme.Palette p = Theme.p();
            int w = getWidth();
            int h = getHeight();
            boolean sel = isSelected();
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16);
            g2.setColor(sel ? Widgets.mix(p.surface, p.accent, 0.13f) : hover ? p.surfaceAlt : p.surface);
            g2.fill(shape);
            g2.setColor(sel ? p.accent : p.border);
            g2.setStroke(new java.awt.BasicStroke(sel ? 2f : 1f));
            g2.draw(shape);

            int d = 40;
            int cy = (h - d) / 2;
            g2.setColor(sel ? p.accent : p.surfaceAlt);
            g2.fill(new Ellipse2D.Float(14, cy, d, d));
            Icons.of(kind, 22, sel ? Color.WHITE : p.muted).paintIcon(this, g2, 14 + (d - 22) / 2, cy + (d - 22) / 2);

            int tx = 14 + d + 14;
            g2.setColor(p.text);
            g2.setFont(Theme.font(Font.BOLD, 14f));
            g2.drawString(title, tx, h / 2 - 5);
            g2.setColor(p.muted);
            g2.setFont(Theme.font(Font.PLAIN, 11.5f));
            drawWrapped(g2, desc, tx, h / 2 + 11, w - tx - 12, 14, 2);
            g2.dispose();
        }

        private static void drawWrapped(Graphics2D g2, String s, int x, int y, int maxW, int lineH, int maxLines) {
            FontMetrics fm = g2.getFontMetrics();
            String[] words = s.split(" ");
            StringBuilder line = new StringBuilder();
            int lines = 0;
            for (String word : words) {
                String trial = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(trial) > maxW && line.length() > 0) {
                    g2.drawString(line.toString(), x, y + lines * lineH);
                    lines++;
                    if (lines >= maxLines) {
                        return;
                    }
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(trial);
                }
            }
            if (line.length() > 0 && lines < maxLines) {
                g2.drawString(line.toString(), x, y + lines * lineH);
            }
        }
    }
}
