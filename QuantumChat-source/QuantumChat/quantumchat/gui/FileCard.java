package quantumchat.gui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;

/** The attachment shown inside a bubble for a file: icon, name, detail line, optional progress bar and button. */
public class FileCard extends JPanel implements Theme.Themed, Bubble.Tintable {

    private final JLabel iconLabel = new JLabel();
    private final JLabel nameLabel = new JLabel(" ");
    private final JLabel detailLabel = new JLabel(" ");
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final Widgets.SecondaryButton action = new Widgets.SecondaryButton("");
    private Icons.Kind kind = Icons.Kind.FILE;
    private Color fg = Color.BLACK;
    private Color muted = Color.GRAY;
    private Runnable onAction;

    public FileCard() {
        setOpaque(false);
        setLayout(new BorderLayout(10, 0));

        iconLabel.setPreferredSize(new Dimension(32, 32));
        add(iconLabel, BorderLayout.WEST);

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        nameLabel.setFont(Theme.font(Font.BOLD, 13f));
        detailLabel.setFont(Theme.font(Font.PLAIN, 11.5f));
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);
        detailLabel.setAlignmentX(LEFT_ALIGNMENT);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        bar.setPreferredSize(new Dimension(120, 8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        bar.setVisible(false);
        mid.add(nameLabel);
        mid.add(detailLabel);
        mid.add(Box.createVerticalStrut(4));
        mid.add(bar);
        add(mid, BorderLayout.CENTER);

        action.setVisible(false);
        action.addActionListener(e -> {
            if (onAction != null) {
                onAction.run();
            }
        });
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        east.add(action);
        add(east, BorderLayout.EAST);

        Theme.Palette p = Theme.p();
        setTint(p.text, p.muted);   // default until the parent bubble applies its own tint
    }

    public void setKind(Icons.Kind k) {
        this.kind = k;
        refreshIcon();
    }

    public void setFileName(String name) {
        nameLabel.setText(Widgets.ellipsizeMiddle(name, 30));
        nameLabel.setToolTipText(name);
    }

    public void setDetail(String s) {
        detailLabel.setText(s);
    }

    /** 0..100 shows the bar; a negative value hides it. */
    public void setProgress(int pct) {
        boolean show = pct >= 0;
        if (show) {
            bar.setValue(Math.min(100, pct));
        }
        if (bar.isVisible() != show) {
            bar.setVisible(show);
            revalidate();
        }
    }

    /** A null label hides the button. */
    public void setAction(String label, Runnable handler) {
        boolean show = label != null;
        this.onAction = handler;
        if (show) {
            action.setText(label);
        }
        if (action.isVisible() != show) {
            action.setVisible(show);
            revalidate();
        }
    }

    @Override
    public void setTint(Color fg, Color muted) {
        this.fg = fg;
        this.muted = muted;
        nameLabel.setForeground(fg);
        detailLabel.setForeground(muted);
        refreshIcon();
    }

    private void refreshIcon() {
        iconLabel.setIcon(Icons.of(kind, 28, fg));
    }

    @Override
    public void applyTheme() {
        // Intentionally empty: the enclosing Bubble re-tints this card (its text colour depends on
        // the bubble's fill), and Theme.applyTree visits the child after the parent.
    }
}
