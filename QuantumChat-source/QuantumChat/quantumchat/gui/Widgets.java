package quantumchat.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.JFormattedTextField;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ComboBoxModel;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/** Small reusable widgets and helpers shared by the screens. */
public final class Widgets {

    private Widgets() { }

    // ------------------------------------------------------------ helpers

    /** Linear blend: t = 0 gives {@code a}, t = 1 gives {@code b}. */
    public static Color mix(Color a, Color b, float t) {
        float u = 1f - t;
        return new Color(
                clamp(a.getRed() * u + b.getRed() * t),
                clamp(a.getGreen() * u + b.getGreen() * t),
                clamp(a.getBlue() * u + b.getBlue() * t));
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double v = bytes;
        String[] units = {"KB", "MB", "GB"};
        int i = -1;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(v < 10 ? "%.1f %s" : "%.0f %s", v, units[i]);
    }

    /** Shortens the middle of a long string: "quarterly_rep...2026.csv". */
    public static String ellipsizeMiddle(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int keep = max - 3;
        int head = (keep + 1) / 2;
        int tail = keep / 2;
        return s.substring(0, head) + "..." + s.substring(s.length() - tail);
    }

    public static String ellipsizeEnd(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

    /**
     * A file name received from a peer is untrusted: drop any directory part
     * and characters that are illegal or awkward on common file systems.
     */
    public static String safeFileName(String name) {
        String n = name == null ? "" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        n = n.replaceAll("[\\p{Cntrl}:*?\"<>|]", "_").trim();
        while (n.startsWith(".")) {
            n = n.substring(1);
        }
        if (n.length() > 120) {
            n = n.substring(0, 120);
        }
        return n.isEmpty() ? "received_file" : n;
    }

    public static void enableAntialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    // ------------------------------------------------------------ widgets

    /** A panel with a rounded, themed background and outline. */
    public static class RoundedPanel extends JPanel implements Theme.Themed {
        public enum Fill { SURFACE, ALT, BG, NONE }

        private Fill fill = Fill.SURFACE;
        private int arc = 14;
        private boolean outline = true;

        public RoundedPanel() {
            this(new BorderLayout());
        }

        public RoundedPanel(LayoutManager lm) {
            super(lm);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        }

        public RoundedPanel fill(Fill f) {
            this.fill = f;
            return this;
        }

        public RoundedPanel arc(int a) {
            this.arc = a;
            return this;
        }

        public RoundedPanel outline(boolean o) {
            this.outline = o;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAntialias(g2);
            Theme.Palette p = Theme.p();
            Color c = fill == Fill.SURFACE ? p.surface : fill == Fill.ALT ? p.surfaceAlt : fill == Fill.BG ? p.bg : null;
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc);
            if (c != null) {
                g2.setColor(c);
                g2.fill(shape);
            }
            if (outline) {
                g2.setColor(p.border);
                g2.draw(shape);
            }
            g2.dispose();
        }

        @Override
        public void applyTheme() {
            repaint();
        }
    }

    /** A small rounded "pill" label, e.g. Locked / Unlocked. */
    public static class Chip extends JLabel {
        private Color fillColor = Color.GRAY;

        public Chip() {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            setFont(Theme.font(Font.BOLD, 11.5f));
            setIconTextGap(5);
        }

        public void setTone(Color fill, Color fg) {
            this.fillColor = fill;
            setForeground(fg);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAntialias(g2);
            g2.setColor(fillColor);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * A themed, rounded text field that shows a grey hint while it is empty
     * and unfocused. (Painted by hand so it looks identical in light and dark
     * mode, instead of depending on Nimbus's colour derivation.)
     */
    public static class PromptTextField extends JTextField {
        private final String hint;

        public PromptTextField(String hint) {
            this.hint = hint;
            setOpaque(false);
            setBackground(new Color(0, 0, 0, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme.Palette p = Theme.p();
            Graphics2D g2 = (Graphics2D) g.create();
            enableAntialias(g2);
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setColor(isEnabled() ? p.surface : p.surfaceAlt);
            g2.fill(shape);
            boolean focused = hasFocus() && isEnabled();
            g2.setColor(focused ? p.accent : p.border);
            g2.setStroke(new BasicStroke(focused ? 1.6f : 1f));
            g2.draw(shape);
            g2.dispose();

            Color fg = isEnabled() ? p.text : p.muted;
            if (!fg.equals(getForeground())) {
                setForeground(fg);
            }
            if (!p.text.equals(getCaretColor())) {
                setCaretColor(p.text);
            }
            if (!p.muted.equals(getDisabledTextColor())) {
                setDisabledTextColor(p.muted);
            }
            super.paintComponent(g);

            if (getText().isEmpty() && !hasFocus()) {
                Graphics2D h = (Graphics2D) g.create();
                enableAntialias(h);
                h.setColor(p.muted);
                h.setFont(getFont());
                FontMetrics fm = h.getFontMetrics();
                h.drawString(hint, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                h.dispose();
            }
        }
    }

    /** A filled circle with a letter, used as a participant avatar. */
    public static class Avatar extends JComponent {
        private final String letter;

        public Avatar(String letter, int size) {
            this.letter = letter;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAntialias(g2);
            int d = Math.min(getWidth(), getHeight());
            g2.setColor(Theme.p().accent);
            g2.fill(new Ellipse2D.Float(0, 0, d, d));
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.font(Font.BOLD, d * 0.46f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(letter, (d - fm.stringWidth(letter)) / 2f, (d - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }

    /** A filled accent-coloured call-to-action button. */
    public static class PrimaryButton extends JButton {
        private boolean hover;

        public PrimaryButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(Theme.font(Font.BOLD, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
            setIconTextGap(8);
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
            enableAntialias(g2);
            Color base = Theme.p().accent;
            Color c = !isEnabled() ? Widgets.mix(base, Theme.p().surface, 0.6f)
                    : getModel().isPressed() ? Widgets.mix(base, Color.BLACK, 0.25f)
                    : hover ? Widgets.mix(base, Color.BLACK, 0.12f) : base;
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A flat secondary button: surface fill with an outline, hover/pressed states, readable in both themes. */
    public static class SecondaryButton extends JButton {
        private boolean hover;

        public SecondaryButton(String text) {
            super(text);
            flatten();
            setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
            setFont(Theme.font(Font.PLAIN, 12.5f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIconTextGap(6);
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

        private void flatten() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            flatten();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme.Palette p = Theme.p();
            Graphics2D g2 = (Graphics2D) g.create();
            enableAntialias(g2);
            Color fill = !isEnabled() ? p.surfaceAlt
                    : getModel().isPressed() ? Widgets.mix(p.surfaceAlt, p.border, 0.6f)
                    : hover ? p.surfaceAlt : p.surface;
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setColor(fill);
            g2.fill(shape);
            g2.setColor(hover && isEnabled() ? p.muted : p.border);
            g2.draw(shape);
            g2.dispose();
            Color fg = isEnabled() ? p.text : p.muted;
            if (!fg.equals(getForeground())) {
                setForeground(fg);
            }
            super.paintComponent(g);
        }
    }

    /** A combo box drawn with the theme palette (Nimbus's own would ignore it). */
    public static class StyledCombo<E> extends JComboBox<E> implements Theme.Themed {

        public StyledCombo(E[] items) {
            super(items);
            setOpaque(false);
            setFont(Theme.font(Font.PLAIN, 13f));
            setRenderer(new Renderer<E>(this));
        }

        @Override
        public void updateUI() {
            setUI(new ComboUI());
        }

        @Override
        public void applyTheme() {
            repaint();
        }

        private static final class Renderer<T> extends DefaultListCellRenderer {
            private final JComboBox<T> combo;

            Renderer(JComboBox<T> combo) {
                this.combo = combo;
            }

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                Theme.Palette p = Theme.p();
                setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
                if (index < 0) {                       // the closed combo's current value
                    setOpaque(false);
                    setForeground(combo.isEnabled() ? p.text : p.muted);
                } else {                               // a row in the drop-down list
                    setOpaque(true);
                    setBackground(selected ? p.accent : p.surface);
                    setForeground(selected ? Color.WHITE : p.text);
                }
                return this;
            }
        }

        private static final class ComboUI extends BasicComboBoxUI {
            @Override
            protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        enableAntialias(g2);
                        g2.setColor(comboBox != null && comboBox.isEnabled() ? Theme.p().muted
                                : Widgets.mix(Theme.p().muted, Theme.p().surfaceAlt, 0.5f));
                        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                        g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                        g2.dispose();
                    }
                };
                b.setBorder(null);
                b.setContentAreaFilled(false);
                b.setFocusable(false);
                b.setOpaque(false);
                b.setPreferredSize(new Dimension(26, 20));
                return b;
            }

            @Override
            public void paint(Graphics g, JComponent c) {
                Theme.Palette p = Theme.p();
                Graphics2D g2 = (Graphics2D) g.create();
                enableAntialias(g2);
                RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0.5f, 0.5f, c.getWidth() - 1, c.getHeight() - 1, 10, 10);
                g2.setColor(c.isEnabled() ? p.surface : p.surfaceAlt);
                g2.fill(shape);
                g2.setColor(p.border);
                g2.draw(shape);
                g2.dispose();
                super.paint(g, c);
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                // the rounded background is painted in paint()
            }

            @Override
            protected javax.swing.plaf.basic.ComboPopup createPopup() {
                BasicComboPopup popup = (BasicComboPopup) super.createPopup();
                popup.setBorder(BorderFactory.createLineBorder(Theme.p().border));
                popup.getList().setBackground(Theme.p().surface);
                return popup;
            }
        }
    }

    /** A spinner whose number field follows the theme (enabled and disabled). */
    public static class StyledSpinner extends JSpinner implements Theme.Themed {

        public StyledSpinner(SpinnerModel model) {
            super(model);
            style();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            style();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            style();
        }

        @Override
        public void applyTheme() {
            style();
        }

        private void style() {
            JComponent ed = getEditor();
            if (ed instanceof JSpinner.DefaultEditor) {
                JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
                Theme.Palette p = Theme.p();
                if (!(tf.getUI() instanceof javax.swing.plaf.basic.BasicFormattedTextFieldUI)) {
                    tf.setUI(new javax.swing.plaf.basic.BasicFormattedTextFieldUI());
                    tf.setOpaque(true);
                }
                tf.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(p.border), BorderFactory.createEmptyBorder(2, 8, 2, 8)));
                tf.setBackground(isEnabled() ? p.surface : p.surfaceAlt);
                tf.setForeground(isEnabled() ? p.text : p.muted);
                tf.setDisabledTextColor(p.muted);
                tf.setCaretColor(p.text);
            }
        }
    }

    /** A menu bar painted in the theme colours (Nimbus would draw its own gradient). */
    public static class ThemedMenuBar extends JMenuBar implements Theme.Themed {

        @Override
        public void paint(Graphics g) {
            Theme.Palette p = Theme.p();
            g.setColor(p.surfaceAlt);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(p.border);
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            paintChildren(g);
        }

        @Override
        public void applyTheme() {
            Theme.Palette p = Theme.p();
            for (int i = 0; i < getMenuCount(); i++) {
                JMenu m = getMenu(i);
                m.setForeground(p.text);
                for (Component c : m.getMenuComponents()) {
                    c.setForeground(p.text);
                }
            }
            repaint();
        }
    }
}
