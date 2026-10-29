package quantumchat.gui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Small vector icons drawn with Java2D, so they scale cleanly and follow the theme colours. */
public final class Icons {

    public enum Kind { LOCK, UNLOCK, FILE, CLIP, SEND, SHIELD, CHECK, DOWNLOAD, HOST, JOIN, LAB, WARN }

    private Icons() { }

    public static Icon of(Kind kind, int size, Color color) {
        return new VectorIcon(kind, size, color);
    }

    public static BufferedImage image(Kind kind, int size, Color color) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        new VectorIcon(kind, size, color).paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    private static final class VectorIcon implements Icon {
        private final Kind kind;
        private final int size;
        private final Color color;

        VectorIcon(Kind kind, int size, Color color) {
            this.kind = kind;
            this.size = size;
            this.color = color;
        }

        @Override public int getIconWidth() { return size; }

        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            g.scale(size / 24.0, size / 24.0);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            draw(g);
            g.dispose();
        }

        private void draw(Graphics2D g) {
            switch (kind) {
                case LOCK: {
                    g.fill(new RoundRectangle2D.Double(5, 11, 14, 10, 3, 3));
                    Path2D s = new Path2D.Double();
                    s.moveTo(8, 11);
                    s.lineTo(8, 8);
                    s.curveTo(8, 3.2, 16, 3.2, 16, 8);
                    s.lineTo(16, 11);
                    g.draw(s);
                    break;
                }
                case UNLOCK: {
                    g.fill(new RoundRectangle2D.Double(5, 11, 14, 10, 3, 3));
                    Path2D s = new Path2D.Double();
                    s.moveTo(8, 11);
                    s.lineTo(8, 6.5);
                    s.curveTo(8, 2.2, 16, 2.2, 16, 6.5);
                    s.lineTo(16, 8);
                    g.draw(s);
                    break;
                }
                case FILE: {
                    Path2D f = new Path2D.Double();
                    f.moveTo(6, 3);
                    f.lineTo(14, 3);
                    f.lineTo(19, 8);
                    f.lineTo(19, 21);
                    f.lineTo(6, 21);
                    f.closePath();
                    g.draw(f);
                    Path2D fold = new Path2D.Double();
                    fold.moveTo(14, 3);
                    fold.lineTo(14, 8);
                    fold.lineTo(19, 8);
                    g.draw(fold);
                    g.draw(new Line2D.Double(9, 13, 16, 13));
                    g.draw(new Line2D.Double(9, 17, 16, 17));
                    break;
                }
                case CLIP: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(16, 7);
                    p.lineTo(16, 16);
                    p.append(new Arc2D.Double(8, 12, 8, 8, 0, -180, Arc2D.OPEN), true);
                    p.lineTo(8, 6.5);
                    p.append(new Arc2D.Double(8, 4, 5, 5, 180, -180, Arc2D.OPEN), true);
                    p.lineTo(13, 15);
                    p.append(new Arc2D.Double(11, 14, 2, 2, 0, -180, Arc2D.OPEN), true);
                    p.lineTo(11, 8.5);
                    g.draw(p);
                    break;
                }
                case SEND: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(3, 11);
                    p.lineTo(21, 3);
                    p.lineTo(15, 21);
                    p.lineTo(11.5, 13.5);
                    p.closePath();
                    g.fill(p);
                    break;
                }
                case SHIELD: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(12, 2.5);
                    p.lineTo(20, 5.5);
                    p.lineTo(20, 11.5);
                    p.curveTo(20, 16.5, 16.5, 20, 12, 21.5);
                    p.curveTo(7.5, 20, 4, 16.5, 4, 11.5);
                    p.lineTo(4, 5.5);
                    p.closePath();
                    g.draw(p);
                    Path2D ck = new Path2D.Double();
                    ck.moveTo(8.5, 12);
                    ck.lineTo(11, 14.5);
                    ck.lineTo(15.5, 9.5);
                    g.draw(ck);
                    break;
                }
                case CHECK: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(5, 13);
                    p.lineTo(10, 18);
                    p.lineTo(19, 7);
                    g.draw(p);
                    break;
                }
                case DOWNLOAD: {
                    g.draw(new Line2D.Double(12, 4, 12, 15));
                    Path2D p = new Path2D.Double();
                    p.moveTo(7, 10);
                    p.lineTo(12, 15);
                    p.lineTo(17, 10);
                    g.draw(p);
                    g.draw(new Line2D.Double(5, 19.5, 19, 19.5));
                    break;
                }
                case HOST: {
                    g.draw(new RoundRectangle2D.Double(4, 4, 16, 6.5, 2.5, 2.5));
                    g.draw(new RoundRectangle2D.Double(4, 13.5, 16, 6.5, 2.5, 2.5));
                    g.fill(new Ellipse2D.Double(6.5, 6.2, 2.2, 2.2));
                    g.fill(new Ellipse2D.Double(6.5, 15.7, 2.2, 2.2));
                    break;
                }
                case JOIN: {
                    Path2D br = new Path2D.Double();
                    br.moveTo(14, 4);
                    br.lineTo(19, 4);
                    br.lineTo(19, 20);
                    br.lineTo(14, 20);
                    g.draw(br);
                    g.draw(new Line2D.Double(4, 12, 15, 12));
                    Path2D ar = new Path2D.Double();
                    ar.moveTo(11, 8);
                    ar.lineTo(15, 12);
                    ar.lineTo(11, 16);
                    g.draw(ar);
                    break;
                }
                case LAB: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(10, 3);
                    p.lineTo(10, 10);
                    p.lineTo(5, 19);
                    p.quadTo(4.2, 21, 6.4, 21);
                    p.lineTo(17.6, 21);
                    p.quadTo(19.8, 21, 19, 19);
                    p.lineTo(14, 10);
                    p.lineTo(14, 3);
                    g.draw(p);
                    g.draw(new Line2D.Double(8.5, 3, 15.5, 3));
                    g.draw(new Line2D.Double(7.5, 15, 16.5, 15));
                    break;
                }
                case WARN: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(12, 3.5);
                    p.lineTo(22, 20.5);
                    p.lineTo(2, 20.5);
                    p.closePath();
                    g.draw(p);
                    g.draw(new Line2D.Double(12, 9.5, 12, 14.5));
                    g.fill(new Ellipse2D.Double(11, 16.4, 2, 2));
                    break;
                }
                default:
                    break;
            }
        }
    }
}
