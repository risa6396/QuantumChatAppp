package quantumchat.gui;

import quantumchat.core.QKDConfig;
import quantumchat.core.QKDPhase;
import quantumchat.core.QKDProgressListener;
import quantumchat.core.QKDStats;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Ellipse2D;

/**
 * Live view of a BB84 run: a step indicator, five statistic cards, the QBER
 * gauge and a collapsible protocol log. Feed it with {@link #forParty}.
 */
public class ProtocolDashboard extends JPanel implements Theme.Themed {

    /** What a coloured value means; resolved against the current palette so it survives theme switches. */
    public enum Tone { NONE, OK, WARN, ERR }

    private static Color colorOf(Tone t) {
        Theme.Palette p = Theme.p();
        if (t == null) {
            return p.text;
        }
        switch (t) {
            case OK: return p.ok;
            case WARN: return p.warn;
            case ERR: return p.err;
            default: return p.text;
        }
    }

    private Tone headlineTone = Tone.NONE;

    private final JLabel headline = new JLabel("Preparing...");
    private final PhaseStepper stepper = new PhaseStepper();
    private final QberGaugePanel gauge = new QberGaugePanel();
    private final StatCard cQubits = new StatCard("Qubits sent");
    private final StatCard cSifted = new StatCard("Bases matched");
    private final StatCard cSample = new StatCard("QBER sample");
    private final StatCard cQber = new StatCard("Measured QBER");
    private final StatCard cKey = new StatCard("Raw key bits");
    private final JTextArea log = new JTextArea();
    private final JScrollPane logScroll = new JScrollPane(log);
    private final Widgets.SecondaryButton logToggle = new Widgets.SecondaryButton("Hide protocol log");
    private boolean logShown = true;
    private double threshold = 0.11;

    public ProtocolDashboard() {
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);

        headline.setFont(Theme.font(Font.BOLD, 15f));

        JPanel top = new Widgets.RoundedPanel(new BorderLayout(0, 6));
        top.setBorder(BorderFactory.createEmptyBorder(10, 14, 8, 14));
        top.add(headline, BorderLayout.NORTH);
        top.add(stepper, BorderLayout.CENTER);

        JPanel cards = new JPanel(new GridLayout(1, 5, 8, 0));
        cards.setOpaque(false);
        cards.add(cQubits);
        cards.add(cSifted);
        cards.add(cSample);
        cards.add(cQber);
        cards.add(cKey);

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        cards.setAlignmentX(LEFT_ALIGNMENT);
        gauge.setAlignmentX(LEFT_ALIGNMENT);
        mid.add(cards);
        mid.add(Box.createVerticalStrut(8));
        mid.add(gauge);

        JPanel upper = new JPanel(new BorderLayout(0, 10));
        upper.setOpaque(false);
        upper.add(top, BorderLayout.NORTH);
        upper.add(mid, BorderLayout.CENTER);
        add(upper, BorderLayout.NORTH);

        log.setEditable(false);
        log.setFont(Theme.mono(11.5f));
        log.setLineWrap(true);
        logScroll.setPreferredSize(new Dimension(100, 130));
        logToggle.addActionListener(e -> setLogVisible(!logShown));
        JPanel logPanel = new JPanel(new BorderLayout(0, 4));
        logPanel.setOpaque(false);
        JPanel togglePanel = new JPanel(new BorderLayout());
        togglePanel.setOpaque(false);
        togglePanel.add(logToggle, BorderLayout.WEST);
        logPanel.add(togglePanel, BorderLayout.NORTH);
        logPanel.add(logScroll, BorderLayout.CENTER);
        add(logPanel, BorderLayout.CENTER);

        applyTheme();
    }

    public void setConfig(QKDConfig c) {
        threshold = c.getQberAbortThreshold();
        gauge.setThreshold(threshold);
        cQubits.setValue(String.valueOf(c.getNumQubits()), Tone.NONE);
    }

    public void setHeadline(String text, Tone tone) {
        headline.setText(text);
        headlineTone = tone == null ? Tone.NONE : tone;
        headline.setForeground(colorOf(headlineTone));
    }

    public void setLogVisible(boolean visible) {
        logShown = visible;
        logToggle.setText(visible ? "Hide protocol log" : "Show protocol log");
        logScroll.setVisible(visible);
        revalidate();
    }

    public void appendLog(String line) {
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    /** Resets everything for a fresh run. */
    public void reset() {
        stepper.reset();
        gauge.reset();
        cSifted.setValue("-", Tone.NONE);
        cSample.setValue("-", Tone.NONE);
        cQber.setValue("-", Tone.NONE);
        cKey.setValue("-", Tone.NONE);
        log.setText("");
        setHeadline("Starting key exchange...", Tone.NONE);
    }

    public void showOutcome(boolean success, String message) {
        setHeadline(message, success ? Tone.OK : Tone.ERR);
    }

    /**
     * A progress listener for one participant. Calls may arrive on any
     * thread; everything is marshalled onto the event dispatch thread. Only
     * the {@code primary} party drives the step indicator, statistics and
     * headline, but every party writes to the log.
     */
    public QKDProgressListener forParty(String who, boolean primary) {
        final String prefix = who == null ? "" : "[" + who + "] ";
        return new QKDProgressListener() {
            @Override
            public void onPhase(QKDPhase phase, String detail) {
                SwingUtilities.invokeLater(() -> {
                    appendLog(prefix + phase + ": " + detail);
                    if (primary) {
                        stepper.setPhase(phase);
                        setHeadline(detail, phase == QKDPhase.ABORTED || phase == QKDPhase.FAILED ? Tone.ERR
                                : phase == QKDPhase.KEY_READY ? Tone.OK : Tone.NONE);
                    }
                });
            }

            @Override
            public void onLog(String message) {
                SwingUtilities.invokeLater(() -> appendLog(prefix + message));
            }

            @Override
            public void onStats(QKDStats s) {
                if (!primary) {
                    return;
                }
                SwingUtilities.invokeLater(() -> showStats(s));
            }
        };
    }

    private void showStats(QKDStats s) {
        cQubits.setValue(String.valueOf(s.numQubits), Tone.NONE);
        cSifted.setValue(s.siftedCount + "  (" + Math.round(100.0 * s.siftedCount / Math.max(1, s.numQubits)) + "%)", Tone.NONE);
        cSample.setValue(s.sampleSize + " bits", Tone.NONE);
        Tone qt = s.qber > threshold ? Tone.ERR : (s.qber > threshold * 0.6 ? Tone.WARN : Tone.OK);
        cQber.setValue(String.format("%.2f%%", s.qber * 100), qt);
        cKey.setValue(String.valueOf(s.rawKeyBits), Tone.NONE);
        gauge.setValues(s.qber, threshold);
    }

    @Override
    public void applyTheme() {
        logScroll.setBorder(BorderFactory.createLineBorder(Theme.p().border));
        headline.setForeground(colorOf(headlineTone));
        repaint();
    }

    // ---------------------------------------------------------------- stat card

    private static final class StatCard extends Widgets.RoundedPanel {
        private final JLabel title = new JLabel();
        private final JLabel value = new JLabel("-");

        StatCard(String t) {
            super(new BorderLayout(0, 2));
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            title.setText(t);
            title.setFont(Theme.font(Font.PLAIN, 11.5f));
            value.setFont(Theme.font(Font.BOLD, 17f));
            add(title, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            applyTheme();
        }

        private Tone tone = Tone.NONE;

        void setValue(String v, Tone t) {
            value.setText(v);
            tone = t == null ? Tone.NONE : t;
            value.setForeground(colorOf(tone));
        }

        @Override
        public void applyTheme() {
            title.setForeground(Theme.p().muted);
            value.setForeground(colorOf(tone));
            super.applyTheme();
        }
    }

    // ---------------------------------------------------------------- phase stepper

    private static final class PhaseStepper extends JComponent {
        private static final String[] STEPS = {"Generate", "Transmit", "Measure", "Sift", "Sample", "Evaluate", "Key ready"};

        private int current = -1;
        private boolean finished;
        private boolean failed;

        void reset() {
            current = -1;
            finished = false;
            failed = false;
            repaint();
        }

        void setPhase(QKDPhase ph) {
            int idx;
            switch (ph) {
                case GENERATING_QUBITS: idx = 0; break;
                case TRANSMITTING: idx = 1; break;
                case MEASURING: idx = 2; break;
                case SIFTING: idx = 3; break;
                case SAMPLING: idx = 4; break;
                case EVALUATING: idx = 5; break;
                case KEY_READY: idx = 6; finished = true; break;
                case ABORTED:
                case FAILED: idx = current; failed = true; break;
                default: idx = -1; break;
            }
            current = Math.max(current, idx);
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(560, 54);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.enableAntialias(g2);
            Theme.Palette p = Theme.p();
            int n = STEPS.length;
            int w = getWidth();
            double colW = (double) w / n;
            int cy = 14;
            int r = 10;
            g2.setFont(Theme.font(Font.PLAIN, 11.5f));
            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i < n; i++) {
                double cx = colW * i + colW / 2;
                if (i < n - 1) {
                    boolean done = i < current || (finished && i < n - 1);
                    g2.setColor(done ? p.accent : p.border);
                    g2.setStroke(new java.awt.BasicStroke(2f));
                    g2.drawLine((int) (cx + r + 2), cy, (int) (cx + colW - r - 2), cy);
                }
            }
            for (int i = 0; i < n; i++) {
                double cx = colW * i + colW / 2;
                boolean isFailed = failed && i == current;
                boolean done = i < current || (finished && i <= current);
                boolean active = i == current && !finished && !failed;
                Ellipse2D circle = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
                if (isFailed) {
                    g2.setColor(p.err);
                    g2.fill(circle);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    g2.drawLine((int) cx - 4, cy - 4, (int) cx + 4, cy + 4);
                    g2.drawLine((int) cx - 4, cy + 4, (int) cx + 4, cy - 4);
                } else if (done) {
                    g2.setColor(p.accent);
                    g2.fill(circle);
                    Icons.of(Icons.Kind.CHECK, 14, Color.WHITE).paintIcon(this, g2, (int) cx - 7, cy - 7);
                } else if (active) {
                    g2.setColor(Widgets.mix(p.surface, p.accent, 0.25f));
                    g2.fill(circle);
                    g2.setColor(p.accent);
                    g2.setStroke(new java.awt.BasicStroke(2.4f));
                    g2.draw(circle);
                    g2.fill(new Ellipse2D.Double(cx - 3.5, cy - 3.5, 7, 7));
                } else {
                    g2.setColor(p.surface);
                    g2.fill(circle);
                    g2.setColor(p.border);
                    g2.setStroke(new java.awt.BasicStroke(2f));
                    g2.draw(circle);
                }
                g2.setColor(isFailed ? p.err : (done || active) ? p.text : p.muted);
                String label = STEPS[i];
                g2.drawString(label, (float) (cx - fm.stringWidth(label) / 2.0), cy + r + 6 + fm.getAscent());
            }
            g2.dispose();
        }
    }
}
