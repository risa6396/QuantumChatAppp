package quantumchat.gui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.RoundRectangle2D;

/**
 * One chat message: a rounded bubble with a small header line (who / when),
 * selectable body text, and an optional attachment component (a file card).
 * Its height depends on its width, so {@link ChatView} lays bubbles out using
 * {@link #preferredWidth(int)} and {@link #heightForWidth(int)}.
 */
public class Bubble extends JPanel implements Theme.Themed {

    public enum Side { MINE, THEIRS, SYSTEM }

    public enum Style { NORMAL, ENCRYPTED, ERROR }

    /** Implemented by attachments that should follow the bubble's text colours. */
    public interface Tintable {
        void setTint(Color fg, Color muted);
    }

    private static final int PAD_X = 12;
    private static final int PAD_Y = 8;
    private static final int GAP = 3;
    private static final int ARC = 16;

    private final Side side;
    private Style style = Style.NORMAL;
    private final JLabel meta = new JLabel();
    private final JTextArea text = new JTextArea();
    private final JMenuItem copyItem = new JMenuItem("Copy text");
    private JComponent attachment;
    private String copyText;

    public Bubble(Side side) {
        this.side = side;
        setOpaque(false);
        setLayout(null);

        meta.setFont(Theme.font(Font.PLAIN, 11f));
        meta.setVisible(side != Side.SYSTEM);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        text.setVisible(false);
        add(meta);
        add(text);

        JPopupMenu menu = new JPopupMenu();
        copyItem.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(copyText != null ? copyText : text.getText()), null));
        menu.add(copyItem);
        setComponentPopupMenu(menu);
        text.setComponentPopupMenu(menu);

        restyle();
    }

    public Side getSide() {
        return side;
    }

    public void setMeta(String s) {
        meta.setText(s == null ? "" : s);
        revalidate();
        repaint();
    }

    public void setBody(String s) {
        text.setText(s == null ? "" : s);
        text.setVisible(s != null && !s.isEmpty());
        revalidate();
        repaint();
    }

    public void setStyle(Style st) {
        this.style = st;
        restyle();
        revalidate();
        repaint();
    }

    /** What the right-click "Copy" item copies, when it differs from the visible text. */
    public void setCopyText(String s) {
        this.copyText = s;
        copyItem.setText(s != null ? "Copy ciphertext (Base64)" : "Copy text");
    }

    public void setAttachment(JComponent c) {
        if (attachment != null) {
            remove(attachment);
        }
        attachment = c;
        if (c != null) {
            add(c);
        }
        restyle();
        revalidate();
    }

    // ---------------------------------------------------------------- sizing

    private boolean hasMeta() {
        return meta.isVisible() && !meta.getText().isEmpty();
    }

    private int padY() {
        return side == Side.SYSTEM ? 5 : PAD_Y;
    }

    /** Natural width (clamped to {@code maxW}): short messages get small bubbles. */
    public int preferredWidth(int maxW) {
        int w = 0;
        if (hasMeta()) {
            w = Math.max(w, meta.getPreferredSize().width);
        }
        if (text.isVisible()) {
            FontMetrics fm = text.getFontMetrics(text.getFont());
            for (String line : text.getText().split("\n", -1)) {
                w = Math.max(w, fm.stringWidth(line));
            }
        }
        if (attachment != null) {
            w = Math.max(w, attachment.getPreferredSize().width);
        }
        w += 2 * PAD_X + 4;
        return Math.max(Math.min(110, maxW), Math.min(maxW, w));
    }

    public int heightForWidth(int w) {
        int innerW = Math.max(10, w - 2 * PAD_X);
        int h = padY();
        boolean any = false;
        if (hasMeta()) {
            h += meta.getPreferredSize().height;
            any = true;
        }
        if (text.isVisible()) {
            h += any ? GAP : 0;
            text.setSize(innerW, Short.MAX_VALUE);
            h += text.getPreferredSize().height;
            any = true;
        }
        if (attachment != null) {
            h += any ? GAP + 3 : 0;
            h += attachment.getPreferredSize().height;
        }
        return h + padY();
    }

    @Override
    public Dimension getPreferredSize() {
        int w = preferredWidth(460);
        return new Dimension(w, heightForWidth(w));
    }

    @Override
    public void doLayout() {
        int innerW = Math.max(10, getWidth() - 2 * PAD_X);
        int y = padY();
        boolean any = false;
        if (hasMeta()) {
            int mh = meta.getPreferredSize().height;
            meta.setBounds(PAD_X, y, innerW, mh);
            y += mh;
            any = true;
        } else {
            meta.setBounds(0, 0, 0, 0);
        }
        if (text.isVisible()) {
            y += any ? GAP : 0;
            text.setSize(innerW, Short.MAX_VALUE);
            int th = text.getPreferredSize().height;
            text.setBounds(PAD_X, y, innerW, th);
            y += th;
            any = true;
        }
        if (attachment != null) {
            y += any ? GAP + 3 : 0;
            int ah = attachment.getPreferredSize().height;
            attachment.setBounds(PAD_X, y, innerW, ah);
        }
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Widgets.enableAntialias(g2);
        Theme.Palette p = Theme.p();
        Color fill;
        Color outline = null;
        if (style == Style.ENCRYPTED) {
            fill = p.lockedBg;
            outline = p.lockedBorder;
        } else if (style == Style.ERROR) {
            fill = Widgets.mix(p.surface, p.err, 0.16f);
            outline = p.err;
        } else if (side == Side.MINE) {
            fill = p.mine;
        } else if (side == Side.THEIRS) {
            fill = p.theirs;
        } else {
            fill = p.surfaceAlt;
        }
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1,
                side == Side.SYSTEM ? getHeight() : ARC, side == Side.SYSTEM ? getHeight() : ARC);
        g2.setColor(fill);
        g2.fill(shape);
        if (outline != null) {
            g2.setColor(outline);
            g2.draw(shape);
        }
        g2.dispose();
    }

    private void restyle() {
        Theme.Palette p = Theme.p();
        Color fg;
        Color muted;
        if (style == Style.ENCRYPTED || style == Style.ERROR) {
            fg = p.text;
            muted = p.muted;
        } else if (side == Side.MINE) {
            fg = p.mineText;
            muted = Widgets.mix(p.mineText, p.mine, 0.30f);
        } else if (side == Side.THEIRS) {
            fg = p.theirsText;
            muted = p.muted;
        } else {
            fg = p.muted;
            muted = p.muted;
        }
        text.setBackground(new Color(0, 0, 0, 0)); // Nimbus ignores setOpaque(false) on text areas
        text.setForeground(fg);
        text.setCaretColor(fg);
        text.setSelectionColor(side == Side.MINE && style == Style.NORMAL
                ? Widgets.mix(p.mine, Color.BLACK, 0.35f) : p.accent);
        text.setSelectedTextColor(Color.WHITE);
        text.setFont(style == Style.ENCRYPTED ? Theme.mono(11.5f)
                : Theme.font(Font.PLAIN, side == Side.SYSTEM ? 12f : 13.5f));
        meta.setForeground(muted);
        if (attachment instanceof Tintable) {
            ((Tintable) attachment).setTint(fg, muted);
        }
        repaint();
    }

    @Override
    public void applyTheme() {
        restyle();
        revalidate();
    }
}
