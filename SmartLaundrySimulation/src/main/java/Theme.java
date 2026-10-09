import java.awt.*;

/**
 * Central theme constants for the Smart Laundry Operations Console.
 * All colours, fonts, and spacing defined here for consistency.
 */
public final class Theme {

    private Theme() {}

    // ── COLOURS ──────────────────────────────────────────────────────────────
    public static final Color BG           = hex(0xEAF6FD);
    public static final Color CARD         = hex(0xF2EEE3);
    public static final Color HEADER       = hex(0xF2EEE3);
    public static final Color PANEL        = hex(0xF8F5EE);
    public static final Color BORDER       = hex(0xFFFFFF);
    public static final Color BORDER_LIGHT = hex(0xFFFFFF);

    public static final Color TURQUOISE    = hex(0x0B9C86);
    public static final Color TURQUOISE_DIM= hex(0x0D3530);
    public static final Color BLUE         = hex(0x3447D4);
    public static final Color BLUE_DIM     = hex(0x141A3A);
    public static final Color ORANGE       = hex(0xE0680F);
    public static final Color ORANGE_DIM   = hex(0x2E1A0A);
    public static final Color YELLOW       = hex(0xB97A00);
    public static final Color YELLOW_DIM   = hex(0x2D2408);
    public static final Color GREEN        = hex(0x1A9B52);
    public static final Color GREEN_DIM    = hex(0x0D2A1A);
    public static final Color RED          = hex(0xD62839);
    public static final Color RED_DIM      = hex(0x2A0E13);

    public static final Color TEXT_PRIMARY  = hex(0x2B3440);
    public static final Color TEXT_SECONDARY= hex(0x3C4655);
    public static final Color TEXT_INACTIVE = hex(0x5F6B7A);
    public static final Color TEXT_WHITE    = hex(0x232B36);

    public static final Color BADGE_BG     = new Color(255,255,255,210);
    public static final Color CARD_HOVER   = hex(0xEAE4D6);

    // ── FONTS ─────────────────────────────────────────────────────────────────
    public static final Font FONT_TITLE     = font(26, Font.BOLD);
    public static final Font FONT_SECTION   = font(18, Font.BOLD);
    public static final Font FONT_NORMAL    = font(15, Font.PLAIN);
    public static final Font FONT_SMALL     = font(13, Font.PLAIN);
    public static final Font FONT_STAT      = font(32, Font.BOLD);
    public static final Font FONT_MONO      = monoFont(11);
    public static final Font FONT_BADGE     = font(11, Font.BOLD);
    public static final Font FONT_CARD_ID   = font(10, Font.BOLD);

    // ── SPACING ───────────────────────────────────────────────────────────────
    public static final int GAP    = 20;
    public static final int RADIUS = 18;
    public static final int PAD    = 15;

    // ── HELPERS ───────────────────────────────────────────────────────────────
    private static Color hex(int rgb) { return new Color(rgb); }

    private static Font font(int size, int style) {
        Font f = new Font("Segoe UI", style, size);
        if (!f.getFamily().equals("Segoe UI"))
            f = new Font("SansSerif", style, size);
        return f;
    }

    private static Font monoFont(int size) {
        Font f = new Font("Consolas", Font.PLAIN, size);
        if (!f.getFamily().equals("Consolas"))
            f = new Font("Monospaced", Font.PLAIN, size);
        return f;
    }

    /** Apply rendering hints for crisp, anti-aliased graphics. */
    public static void applyHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,       RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,  RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,          RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,      RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    /** Draw a soft multi-layer glow behind a rounded rect. */
    public static void drawGlow(Graphics2D g2, int x, int y, int w, int h, int r, Color col, float intensity) {
        for (int i = 8; i > 0; i--) {
            float a = intensity * (i / 8f) * 0.09f;
            g2.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), (int)(a*255)));
            g2.fillRoundRect(x-i, y-i, w+i*2, h+i*2, r+i*2, r+i*2);
        }
    }
}
