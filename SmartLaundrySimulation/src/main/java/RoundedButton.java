import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Custom rounded button with hover and press animations.
 * No default Swing appearance — fully custom-painted.
 *
 * Icons (play / reset) are drawn with Java2D via {@link Glyph} rather than
 * embedded as Unicode text characters (▶ ↺), because those glyphs render as
 * empty "tofu" boxes on systems/fonts that don't ship them. Vector-drawn
 * icons always render identically everywhere.
 */
public class RoundedButton extends JButton {

    /** Which vector icon (if any) to draw to the left of the button text. */
    public enum Glyph { NONE, PLAY, RESET, PAUSE }

    private final Color normalBg;
    private final Color hoverBg;
    private final Color pressBg;
    private final Color textColor;
    private final int radius;
    private Glyph glyph;

    private float hoverAlpha = 0f;
    private boolean pressing = false;
    private javax.swing.Timer hoverTimer;

    public RoundedButton(String text, Color bg, Color textCol, int radius, Glyph glyph) {
        super(text);
        this.normalBg  = bg;
        this.hoverBg   = bg.brighter();
        this.pressBg   = bg.darker();
        this.textColor = textCol;
        this.radius    = radius;
        this.glyph     = glyph;

        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setFont(Theme.FONT_NORMAL.deriveFont(Font.BOLD, 14f));
        setForeground(textCol);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setHorizontalAlignment(SwingConstants.CENTER);
        setIconTextGap(8);
        if (glyph != Glyph.NONE) setIcon(new GlyphIcon(glyph, textCol));

        hoverTimer = new javax.swing.Timer(16, e -> repaint());

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                animateTo(1f);
            }
            @Override public void mouseExited(MouseEvent e) {
                pressing = false;
                animateTo(0f);
            }
            @Override public void mousePressed(MouseEvent e) { pressing = true; repaint(); }
            @Override public void mouseReleased(MouseEvent e) { pressing = false; repaint(); }
        });
    }

    /** Backward-compatible constructor — no icon. */
    public RoundedButton(String text, Color bg, Color textCol, int radius) {
        this(text, bg, textCol, radius, Glyph.NONE);
    }

    /** Swap the vector icon (used by Pause/Resume so the icon always matches the label). */
    public void setGlyph(Glyph g) {
        this.glyph = g;
        setIcon(g == Glyph.NONE ? null : new GlyphIcon(g, textColor));
    }

    private void animateTo(float target) {
        hoverTimer.stop();
        hoverTimer = new javax.swing.Timer(14, e -> {
            float diff = target - hoverAlpha;
            if (Math.abs(diff) < 0.03f) {
                hoverAlpha = target;
                ((javax.swing.Timer)e.getSource()).stop();
            } else {
                hoverAlpha += diff * 0.18f;
            }
            repaint();
        });
        hoverTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);
        int w = getWidth(), h = getHeight();

        // Interpolate colour
        Color bg = pressing ? pressBg : blend(normalBg, hoverBg, hoverAlpha);

        // Glow when hovered
        if (hoverAlpha > 0.05f && !pressing) {
            Theme.drawGlow(g2, 2, 2, w-4, h-4, radius, normalBg, hoverAlpha * 0.7f);
        }

        g2.setColor(bg);
        g2.fillRoundRect(0, 0, w, h, radius, radius);

        // Pressed indent
        if (pressing) {
            g2.setColor(new Color(0,0,0,40));
            g2.fillRoundRect(0, 0, w, h, radius, radius);
        }

        g2.dispose();
        super.paintComponent(g);
    }

    private Color blend(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t)
        );
    }

    /** Vector-drawn 14×14 icon — play triangle or reset arrow. Never renders as a missing-glyph box. */
    private static class GlyphIcon implements Icon {
        private final Glyph type;
        private final Color color;
        private static final int SIZE = 14;

        GlyphIcon(Glyph type, Color color) { this.type = type; this.color = color; }

        @Override public int getIconWidth()  { return SIZE; }
        @Override public int getIconHeight() { return SIZE; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);

            if (type == Glyph.PLAY) {
                int[] px = { x + 1, x + 1, x + SIZE - 1 };
                int[] py = { y,     y + SIZE, y + SIZE / 2 };
                g2.fillPolygon(px, py, 3);
            } else if (type == Glyph.PAUSE) {
                int barW = 4, gap = 3;
                int barH = SIZE;
                g2.fillRoundRect(x + 1,                 y, barW, barH, 1, 1);
                g2.fillRoundRect(x + 1 + barW + gap,     y, barW, barH, 1, 1);
            } else if (type == Glyph.RESET) {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Circular arc (~300°) forming the refresh loop
                g2.drawArc(x + 1, y + 1, SIZE - 2, SIZE - 2, 40, 280);
                // Arrowhead at the open end of the arc
                double angle = Math.toRadians(40);
                int ax = (int) (x + SIZE / 2.0 + (SIZE / 2.0 - 1) * Math.cos(angle));
                int ay = (int) (y + SIZE / 2.0 - (SIZE / 2.0 - 1) * Math.sin(angle));
                int[] hx = { ax - 4, ax + 4, ax + 1 };
                int[] hy = { ay - 3, ay - 1, ay + 5 };
                g2.fillPolygon(hx, hy, 3);
            }
            g2.dispose();
        }
    }
}
