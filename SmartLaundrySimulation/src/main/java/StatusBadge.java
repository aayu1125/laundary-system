import javax.swing.*;
import java.awt.*;

/**
 * Small rounded pill badge used in card headers (e.g. "0/6 in use").
 */
public class StatusBadge extends JLabel {

    private Color bgColor;
    private Color borderColor;
    private int arc = 10;

    public StatusBadge(String text, Color bg, Color border) {
        super(text);
        this.bgColor     = bg;
        this.borderColor = border;
        setFont(Theme.FONT_BADGE);
        setForeground(Theme.TEXT_WHITE);
        setOpaque(false);
        int padV = 4, padH = 10;
        setBorder(BorderFactory.createEmptyBorder(padV, padH, padV, padH));
    }

    public StatusBadge(String text) {
        this(text, Theme.BADGE_BG, Theme.BORDER_LIGHT);
    }

    public void setBgColor(Color c)     { bgColor = c; repaint(); }
    public void setBorderColor(Color c) { borderColor = c; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);
        int w = getWidth(), h = getHeight();
        g2.setColor(bgColor);
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(0, 0, w-1, h-1, arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }
}
