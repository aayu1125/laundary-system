import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Pure Java2D section header icons — no emoji, no images.
 *
 *  LOBBY   → Orange/brown clipboard icon
 *  WASHER  → Cyan/blue spiral washer icon
 *  DRYER   → Orange/red flame icon
 *  PAYMENT → Purple/white document/receipt icon
 */
public class SectionIcon extends JPanel {

    public enum IconType { LOBBY, WASHER, DRYER, PAYMENT }

    private final IconType type;
    private float flamePhase = 0f;
    private final float phaseOffset;
    private final javax.swing.Timer anim;

    public SectionIcon(IconType type) {
        this.type        = type;
        this.phaseOffset = (float)(Math.random() * Math.PI * 2);
        setOpaque(false);
        setPreferredSize(new Dimension(22, 22));

        // Animate flame icon; others are static
        anim = new javax.swing.Timer(30, e -> {
            flamePhase = (flamePhase + 0.06f) % ((float)Math.PI * 2);
            repaint();
        });
        if (type == IconType.DRYER) anim.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);
        int W = getWidth(), H = getHeight();
        int cx = W / 2, cy = H / 2;
        switch (type) {
            case LOBBY:   drawClipboard(g2, cx, cy, W, H); break;
            case WASHER:  drawWasherIcon(g2, cx, cy, W, H); break;
            case DRYER:   drawFlameIcon(g2, cx, cy, W, H); break;
            case PAYMENT: drawReceiptIcon(g2, cx, cy, W, H); break;
        }
        g2.dispose();
    }

    // ── LOBBY: Orange/brown clipboard ─────────────────────────────────────────
    private void drawClipboard(Graphics2D g, int cx, int cy, int W, int H) {
        Color body   = new Color(0xD4, 0x7A, 0x20);  // orange-brown
        Color clip   = new Color(0xA0, 0x55, 0x10);
        Color paper  = new Color(0xFF, 0xE0, 0xB0, 220);
        Color line   = new Color(0xD4, 0x7A, 0x20, 160);

        int bx = cx-7, by = cy-7, bw = 14, bh = 15;

        // Board body
        g.setColor(body);
        g.fillRoundRect(bx, by, bw, bh, 3, 3);

        // Paper on board
        g.setColor(paper);
        g.fillRoundRect(bx+2, by+3, bw-4, bh-4, 2, 2);

        // Lines on paper
        g.setColor(line);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 3; i++)
            g.drawLine(bx+4, by+6+i*3, bx+bw-4, by+6+i*3);

        // Clip at top
        g.setColor(clip);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fillRoundRect(cx-3, by-2, 6, 5, 2, 2);
        g.setColor(body.darker());
        g.drawRoundRect(cx-3, by-2, 6, 5, 2, 2);
    }

    // ── WASHER: Cyan/blue spiral ───────────────────────────────────────────────
    private void drawWasherIcon(Graphics2D g, int cx, int cy, int W, int H) {
        Color ring   = new Color(0x0B, 0x9C, 0x86);   // turquoise/cyan
        Color inner  = new Color(0x5B, 0xA8, 0xFF);   // blue

        // Outer ring
        g.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 50));
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(cx-9, cy-9, 18, 18);

        // Spiral arcs
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(ring);
        g.drawArc(cx-7, cy-7, 14, 14, 0, 270);

        g.setColor(new Color(inner.getRed(), inner.getGreen(), inner.getBlue(), 200));
        g.drawArc(cx-4, cy-4, 8, 8, 90, 270);

        // Centre dot
        g.setColor(ring);
        g.fillOval(cx-2, cy-2, 4, 4);
    }

    // ── DRYER: Organic orange/red flame ───────────────────────────────────────
    private void drawFlameIcon(Graphics2D g, int cx, int cy, int W, int H) {
        float fp = flamePhase + phaseOffset;
        float w  = (float)(Math.sin(fp) * 1.2);
        float tip= (float)(Math.cos(fp * 1.3) * 0.8);

        // Outer flame (red)
        Path2D.Float outer = new Path2D.Float();
        outer.moveTo(cx,     cy - 9 + tip);
        outer.curveTo(cx+6+w, cy-3, cx+7, cy+2, cx+4,  cy+8);
        outer.curveTo(cx+1,  cy+10, cx-1, cy+10, cx-4, cy+8);
        outer.curveTo(cx-7,  cy+2,  cx-6-w, cy-3, cx, cy-9+tip);
        outer.closePath();
        g.setPaint(new GradientPaint(cx, cy-9, new Color(0xFF,0x60,0x20), cx, cy+8, new Color(0xD9,0x30,0x18)));
        g.fill(outer);

        // Middle (orange)
        Path2D.Float mid = new Path2D.Float();
        mid.moveTo(cx,    cy-5+tip*0.6f);
        mid.curveTo(cx+4+w*0.7f, cy, cx+4, cy+5, cx+2, cy+8);
        mid.curveTo(cx,  cy+10, cx-2, cy+10, cx-2, cy+8);
        mid.curveTo(cx-4, cy+5, cx-4-w*0.7f, cy, cx, cy-5+tip*0.6f);
        mid.closePath();
        g.setPaint(new GradientPaint(cx, cy-5, new Color(0xFF,0xA0,0x20), cx, cy+8, new Color(0xFF,0x60,0x10)));
        g.fill(mid);

        // Core (yellow)
        Path2D.Float core = new Path2D.Float();
        core.moveTo(cx, cy);
        core.curveTo(cx+2, cy+3, cx+2, cy+6, cx+1, cy+8);
        core.curveTo(cx, cy+9, cx-1, cy+9, cx-1, cy+8);
        core.curveTo(cx-2, cy+6, cx-2, cy+3, cx, cy);
        core.closePath();
        g.setPaint(new GradientPaint(cx, cy, new Color(0xFF,0xF0,0x80,230), cx, cy+8, new Color(0xFF,0xC0,0x30,200)));
        g.fill(core);
    }

    // ── PAYMENT: Purple/white receipt document ────────────────────────────────
    private void drawReceiptIcon(Graphics2D g, int cx, int cy, int W, int H) {
        Color body  = new Color(0x9B, 0x6D, 0xFF);   // purple
        Color paper = new Color(0xE8, 0xD8, 0xFF, 240);
        Color lineC = new Color(0x6A, 0x40, 0xC0, 160);

        int rx = cx-6, ry = cy-8, rw = 12, rh = 16;

        // Document body
        g.setColor(body);
        g.fillRoundRect(rx, ry, rw, rh, 2, 2);

        // Paper face
        g.setColor(paper);
        g.fillRoundRect(rx+1, ry+2, rw-2, rh-3, 1, 1);

        // Lines
        g.setColor(lineC);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 3; i++) {
            int lx2 = (i == 2) ? rx+rw-5 : rx+rw-2;
            g.drawLine(rx+2, ry+5+i*4, lx2, ry+5+i*4);
        }

        // Zigzag bottom
        g.setColor(body);
        int zy = ry + rh;
        Path2D.Float zig = new Path2D.Float();
        zig.moveTo(rx, zy);
        for (int xi = 0; xi < rw; xi += 3) {
            zig.lineTo(rx+xi+1.5f, zy + (xi%6==0?-3:0));
            zig.lineTo(rx+xi+3, zy);
        }
        zig.lineTo(rx+rw, zy+3);
        g.fill(zig);
    }
}
