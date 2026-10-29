package quantumchat.gui;

import javax.swing.BorderFactory;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * The scrolling message list. It tracks the viewport width, so bubbles re-wrap
 * when the window is resized, and it lays bubbles out itself: your own messages
 * right-aligned, the peer's left-aligned, system notices centred.
 */
public class ChatView extends JPanel implements Scrollable, Theme.Themed {

    private static final int GAP = 8;
    private int lastWidth = -1;

    public ChatView() {
        setLayout(new BubbleLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (getWidth() != lastWidth) {
                    lastWidth = getWidth();
                    revalidate();   // heights depend on the width
                }
            }
        });
        applyTheme();
    }

    public void addBubble(Bubble b) {
        add(b);
        revalidate();
        repaint();
        scrollToBottomLater();
    }

    public void scrollToBottomLater() {
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (sp != null) {
                JScrollBar bar = sp.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        }));
    }

    @Override
    public void applyTheme() {
        setBackground(Theme.p().surface);
        setOpaque(true);
    }

    // ---------------------------------------------------------------- Scrollable

    @Override public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(320, 240);
    }

    @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
        return 24;
    }

    @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
        return Math.max(40, r.height - 40);
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
    }

    // ---------------------------------------------------------------- layout

    private static final class BubbleLayout implements LayoutManager {

        @Override public void addLayoutComponent(String name, Component comp) { }

        @Override public void removeLayoutComponent(Component comp) { }

        private static Dimension sizeFor(Bubble b, int avail) {
            int max = b.getSide() == Bubble.Side.SYSTEM ? avail : (int) (avail * 0.84);
            int w = b.preferredWidth(max);
            return new Dimension(w, b.heightForWidth(w));
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            int totalW = parent.getWidth() > 0 ? parent.getWidth() : 420;
            int avail = Math.max(60, totalW - in.left - in.right);
            int h = in.top + in.bottom;
            boolean first = true;
            for (Component c : parent.getComponents()) {
                if (c instanceof Bubble && c.isVisible()) {
                    h += (first ? 0 : GAP) + sizeFor((Bubble) c, avail).height;
                    first = false;
                }
            }
            return new Dimension(totalW, h);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(100, 40);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int avail = Math.max(60, parent.getWidth() - in.left - in.right);
            int y = in.top;
            for (Component c : parent.getComponents()) {
                if (!(c instanceof Bubble) || !c.isVisible()) {
                    continue;
                }
                Bubble b = (Bubble) c;
                Dimension d = sizeFor(b, avail);
                int x;
                if (b.getSide() == Bubble.Side.MINE) {
                    x = in.left + avail - d.width;
                } else if (b.getSide() == Bubble.Side.SYSTEM) {
                    x = in.left + (avail - d.width) / 2;
                } else {
                    x = in.left;
                }
                b.setBounds(x, y, d.width, d.height);
                y += d.height + GAP;
            }
        }
    }
}
