import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Animated customer avatar — clean circle-head + rounded-torso silhouette.
 *
 * Each instance uses a random bob and walk phase so avatars move independently.
 *
 * States:
 *   WAITING → amber / gold   (normal queue)
 *   SERVING → teal           (at kiosk or washer)
 *   STUCK   → red            (machine failed — stays RED until resolved)
 */
public class CustomerIcon extends JPanel {

    public enum State { WAITING, SERVING, STUCK }

    private final int id;
    private State state;

    // Per-instance random phases → each icon animates independently
    private final float bobPhase;
    private final float walkPhase;
    private final int   timerDelay;  // each icon has its own slightly different timer rate

    private float alpha     = 0f;
    private float scale     = 0.5f;
    private float bobY      = 0f;
    private float walkAngle = 0f;

    private final javax.swing.Timer anim;

    // Colours
    public  static final Color COL_WAITING = new Color(0xE0, 0x8A, 0x00);
    public  static final Color COL_SERVING = new Color(0x0B, 0x9C, 0x86);
    public  static final Color COL_STUCK   = new Color(0xB3, 0x12, 0x2E);

    public CustomerIcon(int id, State state) {
        this.id         = id;
        this.state      = state;
        this.bobPhase   = (float)(Math.random() * Math.PI * 2);
        this.walkPhase  = (float)(Math.random() * Math.PI * 2);
        this.timerDelay = 22 + (int)(Math.random() * 12); // 22–34 ms  → staggered frame rates

        setOpaque(false);
        setPreferredSize(new Dimension(50, 68));

        anim = new javax.swing.Timer(timerDelay, e -> {
            long now = System.currentTimeMillis();
            if (alpha < 1f) alpha = Math.min(1f, alpha + 0.10f);
            if (scale < 1f) scale = Math.min(1f, scale + 0.09f);
            bobY      = (float)(Math.sin(now * 0.0020 + bobPhase)  * 2.4);
            walkAngle = (float)(Math.sin(now * 0.0028 + walkPhase) * 20.0);
            repaint();
        });
        anim.start();
    }

    public void setState(State s) { this.state = s; repaint(); }
    public State getState()       { return state; }
    public int   getCustomerId()  { return id; }
    public void  stopAnimation()  { anim.stop(); }

    private Color bodyColor() {
        switch (state) {
            case SERVING: return COL_SERVING;
            case STUCK:   return COL_STUCK;
            default:      return COL_WAITING;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);

        int W = getWidth(), H = getHeight();
        int cx = W / 2;
        // All layout numbers below were designed for a 68px-tall icon.
        // Scale them to whatever height this instance actually got
        // (Lobby uses 56px, PayQueueFloor uses 52px) so nothing clips.
        float s = H / 68f;

        // Fade + pop-in
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        if (scale < 0.98f) {
            g2.translate(cx, H / 2f);
            g2.scale(scale, scale);
            g2.translate(-cx, -H / 2f);
        }

        Color col  = bodyColor();
        Color dark = col.darker();
        int   bob  = (int) (bobY * s);

        // ── Ground shadow ──────────────────────────────────────────────────
        g2.setColor(new Color(0, 0, 0, 38));
        g2.fillOval(cx - r(10,s), r(56,s) + bob, r(20,s), r(5,s));

        // ── Legs ──────────────────────────────────────────────────────────
        drawLimb(g2, dark, 5.0f*s, cx-r(5,s), r(41,s)+bob,  walkAngle * 0.50f,  0, r(13,s));
        drawLimb(g2, dark, 5.0f*s, cx+r(5,s), r(41,s)+bob, -walkAngle * 0.50f,  0, r(13,s));

        // ── Body (rounded torso) ───────────────────────────────────────────
        // Wide top, narrowing toward waist — achieved with two overlapping shapes
        g2.setColor(col);
        // Shoulder-width rectangle
        g2.fillRoundRect(cx - r(13,s), r(17,s) + bob, r(26,s), r(26,s), r(13,s), r(13,s));

        // ── Arms ──────────────────────────────────────────────────────────
        drawLimb(g2, col, 4.5f*s, cx-r(12,s), r(22,s)+bob, -walkAngle * 0.60f - 12, r(-10,s), r(5,s));
        drawLimb(g2, col, 4.5f*s, cx+r(12,s), r(22,s)+bob,  walkAngle * 0.60f + 12,  r(10,s), r(5,s));

        // ── Head ──────────────────────────────────────────────────────────
        // Large circle, proportionally matching screenshot
        g2.setColor(col);
        g2.fillOval(cx - r(11,s), r(1,s) + bob, r(22,s), r(22,s));
        // Highlight
        g2.setColor(new Color(255, 255, 255, 75));
        g2.fillOval(cx - r(7,s), r(3,s) + bob, r(7,s), r(6,s));

        // ── Red failure ring (STUCK state) ─────────────────────────────────
        if (state == State.STUCK) {
            g2.setColor(new Color(0xFF, 0x60, 0x60, 180));
            g2.setStroke(new BasicStroke(2.5f*s));
            g2.drawOval(cx - r(13,s), bob, r(26,s), r(26,s));
        }

        // ── ID tag ────────────────────────────────────────────────────────
        String tag = "C" + String.format("%02d", id);
        int th = Math.max(9, Math.round(12 * Math.min(1f, s + 0.15f)));
        g2.setFont(new Font("Monospaced", Font.BOLD, Math.max(7, th - 3)));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(tag);
        int ty = Math.min(H - th - 1, r(57,s) + bob);
        g2.setColor(new Color(0, 0, 0, 155));
        g2.fillRoundRect(cx - tw/2 - 3, ty, tw + 6, th, 4, 4);
        g2.setColor(Color.WHITE);
        g2.drawString(tag, cx - tw/2, ty + th - 3);

        g2.dispose();
    }

    /** Scale-and-round helper: converts a "designed for 68px" pixel value to the actual size. */
    private static int r(float value, float scale) { return Math.round(value * scale); }

    /** Draw one arm or leg as a rotated line from pivot. */
    private void drawLimb(Graphics2D g2, Color col, float w,
                           float px, float py, float angleDeg, float dx, float dy) {
        Graphics2D gl = (Graphics2D) g2.create();
        gl.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gl.setColor(col);
        gl.setStroke(new BasicStroke(Math.max(1.5f, w), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gl.translate(px, py);
        gl.rotate(Math.toRadians(angleDeg));
        gl.draw(new java.awt.geom.Line2D.Float(0, 0, dx, dy));
        gl.dispose();
    }
}
