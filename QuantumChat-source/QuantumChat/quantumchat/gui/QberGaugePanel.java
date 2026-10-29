package quantumchat.gui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Gauge of the measured QBER against the abort threshold: green while well
 * below it, amber as it approaches, red once the session would be aborted.
 */
public class QberGaugePanel extends JComponent implements Theme.Themed {

    private static final double SCALE_MAX = 0.5; // the bar spans 0%..50%

    private double qber = 0.0;
    private double threshold = 0.11;
    private boolean measured = false;

    public void setThreshold(double threshold) {
        this.threshold = threshold;
        repaint();
    }

    public void setValues(double qber, double threshold) {
        this.qber = qber;
        this.threshold = threshold;
        this.measured = true;
        repaint();
    }

    public void reset() {
        measured = false;
        qber = 0;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(320, 30);
    }

    @Override
    public void applyTheme() {
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Widgets.enableAntialias(g2);
        Theme.Palette p = Theme.p();
        int w = getWidth();
        int h = getHeight();
        RoundRectangle2D.Float track = new RoundRectangle2D.Float(0, 0, w, h, 12, 12);

        g2.setColor(p.surfaceAlt);
        g2.fill(track);

        int filled = 0;
        if (measured) {
            double clamped = Math.max(0, Math.min(1, qber / SCALE_MAX));
            int fillWidth = (int) Math.round(clamped * w);
            filled = Math.max(fillWidth, 4);
            Color bar = qber > threshold ? p.err : (qber > threshold * 0.6 ? p.warn : p.ok);
            g2.setClip(track);
            g2.setColor(bar);
            g2.fillRect(0, 0, Math.max(fillWidth, 4), h);
            g2.setClip(null);
        }

        int tx = (int) Math.round(Math.min(1.0, threshold / SCALE_MAX) * w);
        g2.setColor(p.muted);
        g2.drawLine(tx, 3, tx, h - 3);

        g2.setColor(p.border);
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 12, 12));

        g2.setFont(Theme.font(Font.BOLD, 12f));
        FontMetrics fm = g2.getFontMetrics();
        String label = measured
                ? String.format("QBER %.2f%%   (abort above %.0f%%)", qber * 100, threshold * 100)
                : String.format("QBER not measured yet   (abort above %.0f%%)", threshold * 100);
        int lx = Math.max(8, (w - fm.stringWidth(label)) / 2);
        int ly = (h - fm.getHeight()) / 2 + fm.getAscent();
        // white where the label sits on the coloured bar, normal text colour on the track
        g2.setClip(0, 0, filled, h);
        g2.setColor(Color.WHITE);
        g2.drawString(label, lx, ly);
        g2.setClip(filled, 0, Math.max(0, w - filled), h);
        g2.setColor(p.text);
        g2.drawString(label, lx, ly);
        g2.setClip(null);
        g2.dispose();
    }
}
