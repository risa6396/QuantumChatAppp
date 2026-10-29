package quantumchat.gui;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;

/**
 * Light / dark colour palettes. Standard Swing controls are styled through
 * the Nimbus look-and-feel (re-installed with matching base colours when the
 * theme changes); the custom-painted components (bubbles, cards, gauge,
 * stepper...) read {@link #p()} directly and implement {@link Themed}.
 */
public final class Theme {

    /** Implemented by components that need to restyle themselves after a theme switch. */
    public interface Themed {
        void applyTheme();
    }

    public static final class Palette {
        public final Color bg, surface, surfaceAlt, border, text, muted, accent, accentText,
                mine, mineText, theirs, theirsText, lockedBg, lockedBorder, ok, warn, err;

        Palette(int bg, int surface, int surfaceAlt, int border, int text, int muted, int accent, int accentText,
                int mine, int mineText, int theirs, int theirsText, int lockedBg, int lockedBorder,
                int ok, int warn, int err) {
            this.bg = new Color(bg);
            this.surface = new Color(surface);
            this.surfaceAlt = new Color(surfaceAlt);
            this.border = new Color(border);
            this.text = new Color(text);
            this.muted = new Color(muted);
            this.accent = new Color(accent);
            this.accentText = new Color(accentText);
            this.mine = new Color(mine);
            this.mineText = new Color(mineText);
            this.theirs = new Color(theirs);
            this.theirsText = new Color(theirsText);
            this.lockedBg = new Color(lockedBg);
            this.lockedBorder = new Color(lockedBorder);
            this.ok = new Color(ok);
            this.warn = new Color(warn);
            this.err = new Color(err);
        }
    }

    private static final Palette LIGHT = new Palette(
            0xF1F4F8, 0xFFFFFF, 0xE8ECF3, 0xD2D9E3, 0x1F2933, 0x66727F, 0x2F6FD6, 0xFFFFFF,
            0x2F6FD6, 0xFFFFFF, 0xE7EBF1, 0x1F2933, 0xFFF3D1, 0xE0B53A,
            0x1E8A44, 0xA86F00, 0xC0392B);

    private static final Palette DARK = new Palette(
            0x1D2025, 0x262A31, 0x30353E, 0x3F4551, 0xE3E6EA, 0x98A2AF, 0x4C8DF0, 0xFFFFFF,
            0x2F6FD6, 0xFFFFFF, 0x343A44, 0xE3E6EA, 0x4B3F1C, 0x9A7B1F,
            0x3DBE6A, 0xE0A93A, 0xE5604F);

    private static boolean dark = false;

    private Theme() { }

    public static boolean isDark() {
        return dark;
    }

    public static void setDark(boolean d) {
        dark = d;
    }

    public static Palette p() {
        return dark ? DARK : LIGHT;
    }

    public static Font font(int style, float size) {
        return new Font(Font.SANS_SERIF, style, 1).deriveFont(size);
    }

    public static Font mono(float size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, 1).deriveFont(size);
    }

    /** (Re)installs Nimbus with colours matching the current palette. */
    public static void install() {
        UIManager.put("defaultFont", new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 13));
        if (dark) {
            UIManager.put("control", new Color(0x2B, 0x2F, 0x36));
            UIManager.put("info", new Color(0x3A, 0x40, 0x4A));
            UIManager.put("nimbusBase", new Color(0x2A, 0x4A, 0x7F));
            UIManager.put("nimbusLightBackground", new Color(0x21, 0x25, 0x2B));
            UIManager.put("nimbusDisabledText", new Color(0x7A, 0x82, 0x8E));
            UIManager.put("nimbusFocus", new Color(0x56, 0x9C, 0xD6));
            UIManager.put("nimbusSelectionBackground", new Color(0x2F, 0x6F, 0xD6));
            UIManager.put("nimbusSelectedText", Color.WHITE);
            UIManager.put("text", new Color(0xE3, 0xE6, 0xEA));
            UIManager.put("menuText", new Color(0xE3, 0xE6, 0xEA));
        } else {
            UIManager.put("control", new Color(0xEE, 0xF1, 0xF6));
            UIManager.put("info", new Color(242, 242, 189));
            UIManager.put("nimbusBase", new Color(51, 98, 140));
            UIManager.put("nimbusLightBackground", Color.WHITE);
            UIManager.put("nimbusDisabledText", new Color(142, 143, 145));
            UIManager.put("nimbusFocus", new Color(115, 164, 209));
            UIManager.put("nimbusSelectionBackground", new Color(57, 105, 138));
            UIManager.put("nimbusSelectedText", Color.WHITE);
            UIManager.put("text", new Color(0x1F, 0x29, 0x33));
            UIManager.put("menuText", new Color(0x1F, 0x29, 0x33));
        }
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            // fall back to whatever look-and-feel is already installed
        }
    }

    /** Calls {@link Themed#applyTheme()} on every themed component below (and including) {@code root}. */
    public static void applyTree(Component root) {
        if (root instanceof Themed) {
            ((Themed) root).applyTheme();
        }
        if (root instanceof Container) {
            for (Component c : ((Container) root).getComponents()) {
                applyTree(c);
            }
        }
    }
}
