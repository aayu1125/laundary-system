import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * Pill-shaped segmented toggle used to pick the scenario ("Normal run" / "Bonus: Congested").
 * A teal thumb slides under the selected label. Replaces the old drop-down so both scenarios
 * are visible at all times and switching is one click.
 */
public class SegmentedToggle extends JComponent {

    private final String[] labels;
    private int   selected = 0;
    private float thumb    = 0f;       // animated position, 0..labels.length-1
    private int   hover    = -1;
    private Runnable onChange;
    private javax.swing.Timer anim;

    public SegmentedToggle(String... labels) {
        this.labels = labels;
        setOpaque(false);
        setPreferredSize(new Dimension(262, 40));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD, 13f));

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!isEnabled()) return;
                int i = indexAt(e.getX());
                if (i >= 0) setSelectedIndex(i);
            }
            @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = isEnabled() ? indexAt(e.getX()) : -1;
                if (i != hover) { hover = i; repaint(); }
            }
        });
    }

    public int  getSelectedIndex() { return selected; }
    public void setOnChange(Runnable r) { onChange = r; }

    public void setSelectedIndex(int i) {
        if (i < 0 || i >= labels.length) return;
        boolean changed = i != selected;
        selected = i;
        if (anim != null) anim.stop();
        anim = new javax.swing.Timer(14, e -> {
            float d = selected - thumb;
            if (Math.abs(d) < 0.01f) { thumb = selected; ((javax.swing.Timer) e.getSource()).stop(); }
            else thumb += d * 0.28f;
            repaint();
        });
        anim.start();
        if (changed && onChange != null) onChange.run();
    }

    @Override public void setEnabled(boolean b) { super.setEnabled(b); setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); repaint(); }

    private int indexAt(int x) {
        double seg = (getWidth() - 8) / (double) labels.length;
        int i = (int) ((x - 4) / seg);
        return (i >= 0 && i < labels.length) ? i : -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);
        int w = getWidth(), h = getHeight();
        if (!isEnabled()) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.62f));

        // track: slightly darker cotton with a white rim
        g2.setColor(new Color(0xE4DECF));
        g2.fillRoundRect(0, 0, w, h, h, h);
        g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 22), 0, h / 2f, new Color(0, 0, 0, 0)));
        g2.fillRoundRect(0, 0, w, h, h, h);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.6f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, h, h);

        // thumb
        double seg = (w - 8) / (double) labels.length;
        double tx = 4 + thumb * seg;
        RoundRectangle2D th = new RoundRectangle2D.Double(tx, 4, seg, h - 8, h - 8, h - 8);
        for (int i = 4; i > 0; i--) {                                   // soft glow
            g2.setColor(new Color(Theme.TURQUOISE.getRed(), Theme.TURQUOISE.getGreen(), Theme.TURQUOISE.getBlue(), 14));
            g2.fill(new RoundRectangle2D.Double(tx - i, 4 - i + 1, seg + 2 * i, h - 8 + 2 * i, h, h));
        }
        g2.setPaint(new GradientPaint(0, 4, new Color(0x19B9A0), 0, h - 4, Theme.TURQUOISE.darker()));
        g2.fill(th);
        g2.setColor(new Color(255, 255, 255, 70));
        g2.fill(new RoundRectangle2D.Double(tx + 3, 5, seg - 6, (h - 8) / 2.2, h, h));

        // labels
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < labels.length; i++) {
            double cx = 4 + seg * i + seg / 2;
            double near = Math.max(0, 1 - Math.abs(thumb - i));          // 1 when the thumb is under it
            Color base = (i == hover) ? Theme.TEXT_WHITE : Theme.TEXT_INACTIVE;
            int r = (int) (base.getRed()   + (255 - base.getRed())   * near);
            int gr= (int) (base.getGreen() + (255 - base.getGreen()) * near);
            int b = (int) (base.getBlue()  + (255 - base.getBlue())  * near);
            g2.setColor(new Color(r, gr, b));
            g2.drawString(labels[i], (float) (cx - fm.stringWidth(labels[i]) / 2.0), (h + fm.getAscent() - fm.getDescent()) / 2f);
        }
        g2.dispose();
    }
}
