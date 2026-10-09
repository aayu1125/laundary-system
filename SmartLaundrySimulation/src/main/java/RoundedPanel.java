import javax.swing.*;
import java.awt.*;

/**
 * Base panel that paints a rounded rectangle background.
 * Supports an optional glowing border accent colour.
 */
public class RoundedPanel extends JPanel {

    private Color bgColor;
    private Color borderColor;
    private Color glowColor;
    private float glowIntensity = 0f;
    private int radius;
    private int borderThick = 2;

    public RoundedPanel(Color bg, Color border, int radius) {
        this.bgColor     = bg;
        this.borderColor = border;
        this.radius      = radius;
        setOpaque(false);
        setBackground(bg);
    }

    public RoundedPanel(Color bg) {
        this(bg, Theme.BORDER, Theme.RADIUS);
    }

    public void setGlow(Color col, float intensity) {
        this.glowColor     = col;
        this.glowIntensity = intensity;
        repaint();
    }

    public void setBorderColor(Color c) { this.borderColor = c; repaint(); }
    public void setBorderThickness(int t) { this.borderThick = t; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);

        int w = getWidth(), h = getHeight();

        // Glow
        if (glowColor != null && glowIntensity > 0.02f) {
            Theme.drawGlow(g2, 2, 2, w-4, h-4, radius, glowColor, glowIntensity);
        }

        // soft shadow
        for (int i = 3; i >= 1; i--) {
            g2.setColor(new Color(40, 60, 90, 9));
            g2.fillRoundRect(1 - i + 1, 1 + i, w - 2 + (i - 1) * 2, h - 2, radius + i, radius + i);
        }

        // Background
        g2.setColor(bgColor);
        g2.fillRoundRect(1, 1, w-2, h-2, radius, radius);

        // Border
        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderThick));
            g2.drawRoundRect(1, 1, w-2, h-2, radius, radius);
        }

        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) { /* handled in paintComponent */ }
}
