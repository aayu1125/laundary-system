import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Animated machine card (washer / dryer / payment kiosk) drawn like the real appliance.
 *
 * WASHER : white front-loading machine. Control panel with knob and LCD, chrome door ring,
 *          glass window. While BUSY the drum turns, soapy water sloshes, clothes tumble and
 *          bubbles rise; the whole machine vibrates slightly.
 * DRYER  : warm-silver front-loading machine. While BUSY the drum glows orange and hot
 *          clothes tumble faster.
 * KIOSK  : payment terminal with a screen, contactless pad, keypad and receipt slot. While
 *          BUSY it shows "PAYING", the pad pulses and a receipt is printed. When broken it
 *          shows OUT OF SERVICE behind hazard tape.
 *
 * Everything is drawn in a fixed "design space" and scaled to the cell, so the machines look
 * right at any window size. Each card has its own random phase, so machines never move in
 * lock-step.
 */
public class MachineCard extends JPanel {

    /** Time source for the progress bar (set to the simulation clock by LaundryApp). */
    public static java.util.function.LongSupplier clock = System::currentTimeMillis;

    public enum MachineType { WASHER, DRYER, KIOSK }
    public enum State       { IDLE, BUSY, FAILED, OUT_OF_SERVICE }

    private final String      machineId;
    private final MachineType type;
    private State  state         = State.IDLE;
    private String customerLabel = "";

    private final float spinOffset;
    private final float phaseOffset;
    private final int   timerMs;

    private float spinAngle  = 0f;   // drum angle in degrees
    private float glowAlpha  = 0f;
    private float progress   = 0f;
    private long  busyStart  = 0;
    private long  busyDuration = 3000;
    private float flickAlpha = 1f;
    private boolean flickUp  = false;
    private float phase      = 0f;   // general animation phase (radians)

    private final javax.swing.Timer anim;

    static final int W = 120, H = 150;

    // ── palette ───────────────────────────────────────────────────────────────
    private static final Color BODY_TOP_W = new Color(0xF7F9FC), BODY_BOT_W = new Color(0xC3CDD8);
    private static final Color BODY_TOP_D = new Color(0xF3EEE6), BODY_BOT_D = new Color(0xBBB2A6);
    private static final Color BODY_TOP_K = new Color(0x4B5078), BODY_BOT_K = new Color(0x242843);
    private static final Color VIOLET = new Color(0xA77BFF);
    private static final Color[] CLOTHES_W = {
        new Color(0xFF6B8B), new Color(0xFFD166), new Color(0x06D6A0), new Color(0xF4F7FA), new Color(0x8E7DFF)
    };
    private static final Color[] CLOTHES_D = {
        new Color(0xFF9E5E), new Color(0xFFD166), new Color(0xFF6B6B), new Color(0xFFF1D6), new Color(0xE8A87C)
    };

    public MachineCard(String id, MachineType type) {
        this.machineId   = id;
        this.type        = type;
        this.spinOffset  = (float)(Math.random() * 360);
        this.phaseOffset = (float)(Math.random() * Math.PI * 2);
        this.timerMs     = 24 + (int)(Math.random() * 14);

        setPreferredSize(new Dimension(W, H));
        setOpaque(false);

        spinAngle = spinOffset;
        phase     = phaseOffset;

        anim = new javax.swing.Timer(timerMs, e -> {
            float targetGlow = (state == State.IDLE) ? 0f : 1f;
            glowAlpha += (targetGlow - glowAlpha) * 0.08f;

            phase = (phase + 0.07f) % ((float)Math.PI * 2000);
            if (state == State.BUSY) {
                spinAngle = (spinAngle + (type == MachineType.DRYER ? 5.0f : 3.4f)) % 360;
                if (busyDuration > 0)
                    progress = Math.min(1f, (float)(clock.getAsLong() - busyStart) / busyDuration);
            }
            if (state == State.FAILED || state == State.OUT_OF_SERVICE) {
                if (flickUp) { flickAlpha = Math.min(1f,  flickAlpha + 0.08f); if (flickAlpha >= 1f)   flickUp = false; }
                else         { flickAlpha = Math.max(0.25f, flickAlpha - 0.08f); if (flickAlpha <= 0.25f) flickUp = true; }
            } else flickAlpha = 1f;
            repaint();
        });
        anim.start();
    }

    public void setState(State s, String customer, long durationMs) {
        this.state         = s;
        this.customerLabel = customer != null ? customer : "";
        if (s == State.BUSY) {
            busyStart    = clock.getAsLong();
            busyDuration = durationMs > 0 ? durationMs : 3000;
            progress     = 0f;
        }
        if (s != State.BUSY) progress = 0f;
    }
    public void setState(State s) { setState(s, "", 0); }

    // ── PAINT ─────────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int w = getWidth(), h = getHeight();

        // Soft floor tile behind the machine
        g2.setColor(new Color(255, 255, 255, 95));
        g2.fillRoundRect(1, 1, w - 2, h - 2, 16, 16);
        g2.setColor(new Color(255, 255, 255, 235));
        g2.setStroke(new BasicStroke(1.6f));
        g2.drawRoundRect(1, 1, w - 2, h - 2, 16, 16);

        boolean kiosk = type == MachineType.KIOSK;
        double dw = kiosk ? 80 : 100, dh = kiosk ? 128 : 118;
        double s = Math.min((w - 10) / dw, (h - 8) / dh);
        g2.translate((w - dw * s) / 2.0, (h - dh * s) / 2.0);
        g2.scale(s, s);

        if (kiosk) paintKiosk(g2);
        else       paintLaundryMachine(g2, type == MachineType.DRYER);
        g2.dispose();
    }

    private Color accent() {
        switch (type) {
            case DRYER: return Theme.ORANGE;
            case KIOSK: return VIOLET;
            default:    return Theme.TURQUOISE;
        }
    }

    private static Color a(Color c, int alpha) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha); }

    private static void centered(Graphics2D g, String t, double cx, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(t, (float)(cx - fm.stringWidth(t) / 2.0), (float) y);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WASHER / DRYER  (design space 100 x 118)
    // ══════════════════════════════════════════════════════════════════════════
    private void paintLaundryMachine(Graphics2D g2, boolean dryer) {
        boolean busy   = state == State.BUSY;
        boolean broken = state == State.FAILED || state == State.OUT_OF_SERVICE;
        Color acc = accent();

        // floor shadow
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fill(new Ellipse2D.Double(8, 111, 84, 7));

        // glow while in use
        if (glowAlpha > 0.05f) {
            Color gc = broken ? Theme.RED : acc;
            Theme.drawGlow(g2, 4, 2, 92, 110, 14, gc, glowAlpha * 0.7f);
        }

        // vibration while running
        Graphics2D g = (Graphics2D) g2.create();
        if (busy) g.translate(Math.sin(phase * 9) * 0.45, Math.cos(phase * 11) * 0.3);

        // feet
        g.setColor(new Color(0x59626E));
        g.fillRoundRect(12, 109, 10, 4, 2, 2);
        g.fillRoundRect(78, 109, 10, 4, 2, 2);

        // body
        Color top = dryer ? BODY_TOP_D : BODY_TOP_W, bot = dryer ? BODY_BOT_D : BODY_BOT_W;
        g.setPaint(new GradientPaint(0, 2, top, 0, 110, bot));
        g.fillRoundRect(4, 2, 92, 108, 14, 14);
        // side highlight
        g.setPaint(new GradientPaint(4, 0, new Color(255, 255, 255, 110), 30, 0, new Color(255, 255, 255, 0)));
        g.fillRoundRect(4, 2, 28, 108, 14, 14);
        Color rim = broken ? Theme.RED : (busy ? a(acc, 230) : new Color(0x8793A1));
        if (broken) rim = a(Theme.RED, (int)(255 * flickAlpha));
        g.setColor(rim);
        g.setStroke(new BasicStroke(busy || broken ? 2.2f : 1.3f));
        g.drawRoundRect(4, 2, 92, 108, 14, 14);

        // control panel
        g.setColor(dryer ? new Color(0x3B3530) : new Color(0x2A3340));
        g.fillRoundRect(9, 6, 82, 20, 8, 8);
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(0.8f));
        g.drawRoundRect(9, 6, 82, 20, 8, 8);

        // knob (turns while running)
        Graphics2D gk = (Graphics2D) g.create();
        gk.translate(22, 16);
        gk.setPaint(new GradientPaint(-6, -6, new Color(0xF2F5F8), 6, 6, new Color(0x8A95A2)));
        gk.fill(new Ellipse2D.Double(-7, -7, 14, 14));
        gk.setColor(new Color(0x4A5360));
        gk.setStroke(new BasicStroke(0.8f));
        gk.draw(new Ellipse2D.Double(-7, -7, 14, 14));
        gk.rotate(Math.toRadians(busy ? 40 + progress * 280 : 0));
        gk.setColor(acc);
        gk.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gk.drawLine(0, -2, 0, -6);
        gk.dispose();

        // LCD
        Color lcdBg   = broken ? new Color(0x2A0A0E) : dryer ? new Color(0x2A1A06) : new Color(0x08241E);
        Color lcdOn   = broken ? Theme.RED : dryer ? new Color(0xFFB347) : new Color(0x5CFFB0);
        Color lcdIdle = dryer ? new Color(0x8A5A1C) : new Color(0x2F7D63);
        g.setColor(lcdBg);
        g.fillRoundRect(34, 9, 34, 14, 4, 4);
        g.setColor(new Color(0, 0, 0, 120));
        g.drawRoundRect(34, 9, 34, 14, 4, 4);
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        String lcd; Color lc;
        if (broken)                     { lcd = "ERR"; lc = a(lcdOn, (int)(255 * flickAlpha)); }
        else if (busy && !customerLabel.isEmpty()) { lcd = customerLabel; lc = lcdOn; }
        else if (busy)                  { lcd = dryer ? "DRY" : "WASH"; lc = lcdOn; }
        else                            { lcd = "READY"; lc = lcdIdle; }
        g.setColor(lc);
        centered(g, lcd, 51, 19.5);

        // status buttons
        g.setColor(busy ? acc : new Color(0x6B7684));
        g.fill(new Ellipse2D.Double(74, 11, 5, 5));
        g.setColor(broken ? a(Theme.RED, (int)(255 * flickAlpha)) : new Color(0x6B7684));
        g.fill(new Ellipse2D.Double(83, 11, 5, 5));
        g.setColor(new Color(0x6B7684));
        g.fillRoundRect(74, 19, 14, 2, 2, 2);

        // door: chrome ring -> gasket -> glass
        double cx = 50, cy = 63;
        g.setPaint(new GradientPaint(20, 33, new Color(0xFFFFFF), 80, 93, dryer ? new Color(0x9D9488) : new Color(0x94A1AF)));
        g.fill(new Ellipse2D.Double(cx - 31, cy - 31, 62, 62));
        g.setColor(new Color(0x6B7684));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Ellipse2D.Double(cx - 31, cy - 31, 62, 62));
        g.setPaint(new GradientPaint(0, (float)(cy - 27), new Color(0x58626E), 0, (float)(cy + 27), new Color(0x2B323B)));
        g.fill(new Ellipse2D.Double(cx - 27, cy - 27, 54, 54));
        if (busy) { g.setColor(a(acc, 130)); g.setStroke(new BasicStroke(1.2f)); g.draw(new Ellipse2D.Double(cx - 27, cy - 27, 54, 54)); }

        paintGlass(g, cx, cy, 23, dryer, busy, broken);

        // door handle
        g.setPaint(new GradientPaint(80, 0, new Color(0xF4F6F8), 86, 0, new Color(0x8892A0)));
        g.fillRoundRect(81, 55, 6, 17, 4, 4);

        // progress strip
        g.setColor(new Color(0, 0, 0, 55));
        g.fillRoundRect(16, 98, 68, 4, 4, 4);
        if (busy && progress > 0) {
            int bw = Math.max(4, (int)(68 * progress));
            g.setPaint(new GradientPaint(16, 0, acc, 84, 0, dryer ? Theme.YELLOW : Theme.GREEN));
            g.fillRoundRect(16, 98, bw, 4, 4, 4);
        } else if (broken) {
            g.setColor(a(Theme.RED, (int)(255 * flickAlpha)));
            g.fillRoundRect(16, 98, 68, 4, 4, 4);
        }

        // machine id plate
        g.setFont(new Font("SansSerif", Font.BOLD, 8));
        g.setColor(broken ? Theme.RED.darker() : new Color(0x56616E));
        centered(g, machineId, 50, 109);
        g.dispose();
    }

    /** Glass window: dark drum, perforation ring, water, tumbling clothes, bubbles, reflections. */
    private void paintGlass(Graphics2D g, double cx, double cy, double r, boolean dryer, boolean busy, boolean broken) {
        Graphics2D gg = (Graphics2D) g.create();
        Ellipse2D glass = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        gg.setClip(glass);

        // drum interior
        if (dryer && busy) {
            gg.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r,
                new float[]{0f, 1f}, new Color[]{new Color(0x7A3A10), new Color(0x1E0F06)}));
        } else {
            gg.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r,
                new float[]{0f, 1f}, new Color[]{new Color(0x26384A), new Color(0x0C141C)}));
        }
        gg.fill(glass);

        // perforated drum wall (rotates with the drum)
        Graphics2D gd = (Graphics2D) gg.create();
        gd.translate(cx, cy);
        gd.rotate(Math.toRadians(spinAngle));
        gd.setColor(new Color(255, 255, 255, busy ? 60 : 32));
        for (int i = 0; i < 14; i++) {
            double ang = i * Math.PI * 2 / 14;
            gd.fill(new Ellipse2D.Double(Math.cos(ang) * (r - 3.2) - 0.9, Math.sin(ang) * (r - 3.2) - 0.9, 1.8, 1.8));
        }
        // drum paddles
        gd.setColor(new Color(255, 255, 255, busy ? 45 : 25));
        gd.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            gd.rotate(Math.PI * 2 / 3);
            gd.drawLine(0, (int) (-r + 8), 0, (int) (-r + 13));
        }
        gd.dispose();

        if (busy) {
            Color[] cl = dryer ? CLOTHES_D : CLOTHES_W;
            if (!dryer) {
                // soapy water that sloshes
                Path2D.Double water = new Path2D.Double();
                double level = cy + r * 0.18;
                water.moveTo(cx - r, level);
                for (double x = -r; x <= r; x += 2)
                    water.lineTo(cx + x, level + Math.sin(phase * 3 + x * 0.22 + spinOffset) * 1.6);
                water.lineTo(cx + r, cy + r); water.lineTo(cx - r, cy + r); water.closePath();
                gg.setPaint(new GradientPaint(0, (float) level, new Color(0x6FC8FF, false).brighter(), 0, (float)(cy + r), new Color(0x2C7DD1)));
                gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
                gg.fill(water);
                gg.setComposite(AlphaComposite.SrcOver);
            }
            // tumbling clothes
            for (int i = 0; i < cl.length; i++) {
                double ang = Math.toRadians(spinAngle * (dryer ? 0.8 : 0.55) + i * 72 + spinOffset);
                double orbit = r * (0.40 + 0.10 * Math.sin(i * 1.7 + phase * 0.6));
                double px = cx + Math.cos(ang) * orbit, py = cy + Math.sin(ang) * orbit * 0.9;
                double sz = 6.5 + (i % 2) * 1.8;
                Graphics2D gc = (Graphics2D) gg.create();
                gc.translate(px, py);
                gc.rotate(ang * 1.6);
                gc.setColor(cl[i]);
                gc.fill(new RoundRectangle2D.Double(-sz / 2, -sz / 2.6, sz, sz / 1.3, 4, 4));
                gc.setColor(new Color(255, 255, 255, 80));
                gc.fill(new RoundRectangle2D.Double(-sz / 2 + 0.8, -sz / 2.6 + 0.8, sz * 0.5, 1.4, 1, 1));
                gc.dispose();
            }
            if (!dryer) {
                // bubbles
                for (int i = 0; i < 6; i++) {
                    double t = ((phase * 0.35 + i * 0.17 + spinOffset) % 1.0 + 1.0) % 1.0;
                    double bx = cx + Math.sin(i * 2.3 + phase) * r * 0.55;
                    double by = cy + r * 0.8 - t * r * 1.5;
                    double br = 1.1 + (i % 3) * 0.7;
                    gg.setColor(new Color(255, 255, 255, (int) (140 * (1 - t))));
                    gg.setStroke(new BasicStroke(0.6f));
                    gg.draw(new Ellipse2D.Double(bx - br, by - br, 2 * br, 2 * br));
                }
            } else {
                // hot air shimmer
                gg.setColor(new Color(255, 170, 60, 60));
                gg.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 3; i++) {
                    double yy = cy - r * 0.5 + i * r * 0.5;
                    Path2D.Double wv = new Path2D.Double();
                    wv.moveTo(cx - r * 0.7, yy);
                    for (double x = -r * 0.7; x <= r * 0.7; x += 2)
                        wv.lineTo(cx + x, yy + Math.sin(phase * 4 + x * 0.35 + i) * 1.4);
                    gg.draw(wv);
                }
            }
        } else if (!broken) {
            // idle: a few clothes resting at the bottom of the drum
            Color[] cl = dryer ? CLOTHES_D : CLOTHES_W;
            gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
            for (int i = 0; i < 3; i++) {
                gg.setColor(cl[i]);
                gg.fill(new RoundRectangle2D.Double(cx - 12 + i * 8, cy + r * 0.55, 8, 5, 4, 4));
            }
            gg.setComposite(AlphaComposite.SrcOver);
        }

        if (broken) {
            gg.setColor(a(Theme.RED, (int)(110 * flickAlpha)));
            gg.fill(glass);
        }

        // glass reflections
        gg.setPaint(new GradientPaint((float)(cx - r), (float)(cy - r), new Color(255, 255, 255, 120),
                                      (float)(cx + r * 0.2), (float)(cy), new Color(255, 255, 255, 0)));
        gg.fill(new Ellipse2D.Double(cx - r * 0.85, cy - r * 0.9, r * 1.1, r * 0.8));
        gg.dispose();

        gg = (Graphics2D) g.create();
        gg.setColor(new Color(255, 255, 255, 150));
        gg.setStroke(new BasicStroke(1f));
        gg.draw(glass);
        gg.dispose();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PAYMENT KIOSK  (design space 80 x 128)
    // ══════════════════════════════════════════════════════════════════════════
    private void paintKiosk(Graphics2D g2) {
        boolean busy   = state == State.BUSY;
        boolean broken = state == State.FAILED || state == State.OUT_OF_SERVICE;

        g2.setColor(new Color(0, 0, 0, 80));
        g2.fill(new Ellipse2D.Double(8, 121, 64, 6));
        if (glowAlpha > 0.05f)
            Theme.drawGlow(g2, 4, 2, 72, 116, 14, broken ? Theme.RED : VIOLET, glowAlpha * 0.7f);

        Graphics2D g = (Graphics2D) g2.create();

        // body
        g.setPaint(new GradientPaint(0, 2, BODY_TOP_K, 0, 118, BODY_BOT_K));
        g.fillRoundRect(4, 2, 72, 116, 14, 14);
        g.setPaint(new GradientPaint(4, 0, new Color(255, 255, 255, 60), 26, 0, new Color(255, 255, 255, 0)));
        g.fillRoundRect(4, 2, 24, 116, 14, 14);
        Color rim = broken ? a(Theme.RED, (int)(255 * flickAlpha)) : (busy ? a(VIOLET, 240) : new Color(0x7D82B5));
        g.setColor(rim);
        g.setStroke(new BasicStroke(busy || broken ? 2.2f : 1.3f));
        g.drawRoundRect(4, 2, 72, 116, 14, 14);

        // screen
        g.setColor(new Color(0x0B0D18));
        g.fillRoundRect(9, 7, 62, 40, 9, 9);
        Shape scr = new RoundRectangle2D.Double(11.5, 9.5, 57, 35, 7, 7);
        Graphics2D gs = (Graphics2D) g.create();
        gs.setClip(scr);
        if (broken) {
            gs.setPaint(new GradientPaint(0, 10, new Color(0x3A0C14), 0, 44, new Color(0x1B0509)));
        } else if (busy) {
            gs.setPaint(new GradientPaint(0, 10, new Color(0x3B7BFF), 0, 44, new Color(0x6A3BE0)));
        } else {
            gs.setPaint(new GradientPaint(0, 10, new Color(0x233A7A), 0, 44, new Color(0x2E2166)));
        }
        gs.fill(scr);
        gs.setPaint(new GradientPaint(0, 9, new Color(255, 255, 255, 50), 0, 26, new Color(255, 255, 255, 0)));
        gs.fillRect(11, 9, 58, 18);

        if (broken) {
            gs.setColor(a(Theme.RED, (int)(255 * flickAlpha)));
            gs.setFont(new Font("SansSerif", Font.BOLD, 8));
            centered(gs, "OUT OF", 40, 21);
            centered(gs, "SERVICE", 40, 30);
            // hazard tape across the screen
            Graphics2D gt = (Graphics2D) gs.create();
            gt.rotate(Math.toRadians(-14), 40, 28);
            gt.setColor(new Color(0xF5C84B));
            gt.fillRect(2, 40, 80, 6);
            gt.setColor(new Color(0x201A08));
            for (int x = -4; x < 90; x += 8) {
                Polygon p = new Polygon(new int[]{x, x + 4, x + 8, x + 4}, new int[]{40, 40, 46, 46}, 4);
                gt.fillPolygon(p);
            }
            gt.dispose();
        } else if (busy) {
            gs.setColor(Color.WHITE);
            gs.setFont(new Font("SansSerif", Font.BOLD, 7));
            centered(gs, "PAYING", 40, 19);
            if (!customerLabel.isEmpty()) {
                gs.setFont(new Font("Monospaced", Font.BOLD, 12));
                gs.setColor(new Color(0xB9FFE0));
                centered(gs, customerLabel, 40, 31);
            }
            gs.setColor(new Color(255, 255, 255, 70));
            gs.fillRoundRect(17, 36, 46, 3, 3, 3);
            gs.setColor(new Color(0x7CFFC4));
            gs.fillRoundRect(17, 36, Math.max(3, (int)(46 * progress)), 3, 3, 3);
        } else {
            gs.setColor(new Color(255, 255, 255, 215));
            gs.setFont(new Font("SansSerif", Font.BOLD, 6));
            centered(gs, "SMART LAUNDRY", 40, 19);
            gs.setColor(new Color(0x9FE8FF));
            gs.setFont(new Font("SansSerif", Font.BOLD, 9));
            centered(gs, "TAP TO PAY", 40, 31);
            gs.setColor(new Color(255, 255, 255, (int)(110 + 100 * Math.sin(phase * 1.6))));
            gs.fill(new Ellipse2D.Double(37.5, 35.5, 5, 5));
        }
        gs.dispose();
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(scr);

        // machine id
        g.setFont(new Font("SansSerif", Font.BOLD, 8));
        g.setColor(broken ? Theme.RED : new Color(0xC4C9F2));
        centered(g, machineId, 40, 57);

        // contactless pad
        g.setColor(new Color(0x12142A));
        g.fillRoundRect(16, 61, 48, 22, 9, 9);
        g.setColor(new Color(255, 255, 255, 35));
        g.drawRoundRect(16, 61, 48, 22, 9, 9);
        Color wave = broken ? new Color(0x5A2A30) : (busy ? VIOLET : new Color(0x4A4F86));
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            int alpha = 255;
            if (busy) alpha = (int)(70 + 185 * Math.max(0, Math.sin(phase * 5 - i * 0.9)));
            else if (!broken) alpha = 140;
            g.setColor(a(wave, alpha));
            double rr = 4 + i * 4.2;
            g.draw(new Arc2D.Double(34 - rr * 0.6, 72 - rr, rr * 1.2 + 1, rr * 2, -50, 100, Arc2D.OPEN));
        }
        g.setColor(a(wave, 255));
        g.fill(new Ellipse2D.Double(30.5, 70, 4, 4));
        // status LED
        g.setColor(broken ? a(Theme.RED, (int)(255 * flickAlpha)) : busy ? new Color(0x5CFFB0) : new Color(0x2F7D63));
        g.fill(new Ellipse2D.Double(54, 64, 4, 4));

        // keypad
        int lit = busy ? (int)(spinAngle / 40) % 9 : -1;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                double kx = 19 + col * 14.5, ky = 88 + row * 8;
                g.setColor(idx == lit ? new Color(0xC9B3FF) : new Color(0x3C4170));
                g.fill(new RoundRectangle2D.Double(kx, ky, 12, 5.5, 3, 3));
                g.setColor(new Color(255, 255, 255, idx == lit ? 0 : 35));
                g.draw(new RoundRectangle2D.Double(kx, ky, 12, 5.5, 3, 3));
            }

        // receipt slot + printing paper
        g.setColor(new Color(0x07080F));
        g.fillRoundRect(20, 113, 40, 3, 2, 2);
        if (busy && progress > 0.45f) {
            double len = Math.min(1.0, (progress - 0.45) / 0.5) * 11;
            g.setColor(new Color(0xF4F1FF));
            g.fillRect(24, 114, 32, (int) Math.max(1, len));
            g.setColor(new Color(0x9A93C8));
            for (int ly = 118; ly < 114 + len; ly += 3) g.drawLine(27, ly, 52, ly);
        }
        g.dispose();
    }
}
