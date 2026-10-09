import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.Calendar;

/**
 * Top navigation bar — custom basket icon, clock, sim timer, start/reset buttons.
 */
public class HeaderPanel extends JPanel {

    private final JLabel dateLabel;
    private final JLabel timeLabel;
    final JLabel simTimerLabel;
    final RoundedButton btnStart;
    final RoundedButton btnPause;
    final RoundedButton btnReset;
    final SegmentedToggle scenarioBox;
    final SegmentedToggle speedBox;
    private final javax.swing.Timer clockTimer;

    public HeaderPanel(Runnable onStart, Runnable onReset, Runnable onPause) {
        setBackground(Theme.HEADER);
        setPreferredSize(new Dimension(0, 75));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        setLayout(new BorderLayout());

        // ── LEFT: Brand ────────────────────────────────────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));

        // Custom basket icon drawn with Java2D (matches image 1 - teal rounded square)
        BasketIcon basketIcon = new BasketIcon();
        basketIcon.setPreferredSize(new Dimension(50, 50));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Smart Laundry Facility");
        title.setFont(Theme.FONT_TITLE.deriveFont(20f));
        title.setForeground(Theme.TEXT_WHITE);

        JLabel sub = new JLabel("Operations Console  ·  6 Washers  ·  4 Dryers  ·  2 Payment Desks");
        sub.setFont(Theme.FONT_SMALL);
        sub.setForeground(Theme.TEXT_SECONDARY);

        brand.add(title);
        brand.add(Box.createVerticalStrut(2));
        brand.add(sub);
        left.add(basketIcon);
        left.add(brand);

        // ── RIGHT: Clock + Buttons ─────────────────────────────────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));

        JPanel clockBox = new JPanel();
        clockBox.setOpaque(false);
        clockBox.setLayout(new BoxLayout(clockBox, BoxLayout.Y_AXIS));

        dateLabel = clockLabel("--/--/----");
        timeLabel = clockLabel("--:--:-- --");
        timeLabel.setFont(Theme.FONT_NORMAL.deriveFont(Font.BOLD, 15f));
        timeLabel.setForeground(Theme.TEXT_WHITE);
        clockBox.add(dateLabel);
        clockBox.add(timeLabel);

        // Pulsing green dot
        JLabel dot = new JLabel("●") {
            float a = 1f; boolean up = false;
            { new javax.swing.Timer(650, e -> {
                if (up) { a = Math.min(1f, a + 0.07f); if (a >= 1f) up = false; }
                else     { a = Math.max(0.2f, a - 0.07f); if (a <= 0.2f) up = true; }
                setForeground(new Color(Theme.GREEN.getRed(), Theme.GREEN.getGreen(), Theme.GREEN.getBlue(), (int)(a*255)));
            }).start(); }
        };
        dot.setFont(Theme.FONT_SMALL);
        dot.setForeground(Theme.GREEN);

        simTimerLabel = new JLabel(" 00:00");
        simTimerLabel.setFont(Theme.FONT_NORMAL.deriveFont(Font.BOLD));
        simTimerLabel.setForeground(Theme.TEXT_SECONDARY);

        // Java2D stopwatch icon
        StopwatchIcon swIcon = new StopwatchIcon();
        swIcon.setPreferredSize(new Dimension(18, 18));

        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 36));
        sep.setForeground(Theme.BORDER);

        scenarioBox = new SegmentedToggle("Normal run", "Bonus: Congested", "Extra: Incidents");
        scenarioBox.setPreferredSize(new Dimension(372, 40));
        speedBox = new SegmentedToggle("1x", "2x", "4x");
        speedBox.setPreferredSize(new Dimension(126, 40));

        btnStart = new RoundedButton("Start Simulation", Theme.TURQUOISE, Color.WHITE, 12, RoundedButton.Glyph.PLAY);
        btnStart.setPreferredSize(new Dimension(170, 40));
        btnStart.addActionListener(e -> onStart.run());

        btnPause = new RoundedButton("Pause", Color.WHITE, Theme.TEXT_WHITE, 12, RoundedButton.Glyph.PAUSE);
        btnPause.setPreferredSize(new Dimension(124, 40));
        btnPause.setEnabled(false);
        btnPause.addActionListener(e -> onPause.run());

        btnReset = new RoundedButton("Reset", Color.WHITE, Theme.TEXT_WHITE, 12, RoundedButton.Glyph.RESET);
        btnReset.setPreferredSize(new Dimension(104, 40));
        btnReset.addActionListener(e -> onReset.run());

        right.add(dot);
        right.add(clockBox);
        right.add(Box.createHorizontalStrut(4));
        right.add(swIcon);
        right.add(simTimerLabel);
        right.add(sep);
        right.add(scenarioBox);
        right.add(speedBox);
        right.add(btnStart);
        right.add(btnPause);
        right.add(btnReset);

        add(left,  BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        clockTimer = new javax.swing.Timer(1000, e -> tickClock());
        clockTimer.start();
        tickClock();
    }

    private void tickClock() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR); if (h == 0) h = 12;
        String ampm = c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";
        dateLabel.setText(String.format("%d/%d/%d",
            c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.YEAR)));
        timeLabel.setText(String.format("%d:%02d:%02d %s", h, c.get(Calendar.MINUTE), c.get(Calendar.SECOND), ampm));
    }

    private JLabel clockLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_SMALL);
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);
        g2.setColor(Theme.HEADER);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(Color.WHITE);
        g2.fillRect(0, getHeight() - 2, getWidth(), 2);
        g2.dispose();
        super.paintComponent(g);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
/**
 * Custom basket icon — teal rounded square with woven basket drawn via Java2D.
 * Matches the app icon style in image 1.
 */
class BasketIcon extends JPanel {

    BasketIcon() { setOpaque(false); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);

        int W = getWidth(), H = getHeight();
        int pad = 3;
        int r = 12; // corner radius

        // Teal rounded square background (like image 1)
        GradientPaint bg = new GradientPaint(0, 0, new Color(0x28, 0xD4, 0xB8),
                                              0, H, new Color(0x1A, 0x9E, 0x8C));
        g2.setPaint(bg);
        g2.fillRoundRect(pad, pad, W-pad*2, H-pad*2, r, r);

        // Draw a basket: base arc + handle + weave lines
        int cx = W/2, cy = H/2 + 2;
        int bw = W - 16, bh = (H - 16) / 2;

        // Basket body - rounded bottom
        g2.setColor(new Color(0xFF, 0xE0, 0x8A)); // warm straw
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Body outline
        GeneralPath body = new GeneralPath();
        body.moveTo(cx - bw/2, cy - bh/2);
        body.lineTo(cx - bw/2 + 2, cy + bh/2 - 2);
        body.quadTo(cx - bw/2 + 4, cy + bh/2 + 4, cx, cy + bh/2 + 4);
        body.quadTo(cx + bw/2 - 4, cy + bh/2 + 4, cx + bw/2 - 2, cy + bh/2 - 2);
        body.lineTo(cx + bw/2, cy - bh/2);
        g2.setColor(new Color(0xFF, 0xE0, 0x8A, 230));
        g2.fill(body);
        g2.setColor(new Color(0xD4, 0xA0, 0x40));
        g2.draw(body);

        // Weave horizontal lines
        g2.setColor(new Color(0xC8, 0x90, 0x30, 180));
        g2.setStroke(new BasicStroke(1.2f));
        for (int i = 1; i <= 3; i++) {
            int ly = cy - bh/2 + i * (bh + 4) / 4;
            int indent = i * 2;
            g2.drawLine(cx - bw/2 + indent, ly, cx + bw/2 - indent, ly);
        }

        // Handle arc
        g2.setColor(new Color(0xD4, 0xA0, 0x40));
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(cx - bw/4, cy - bh - 4, bw/2, bh - 2, 0, 180);

        // Top rim line
        g2.setColor(new Color(0xFF, 0xE0, 0x8A));
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx - bw/2, cy - bh/2, cx + bw/2, cy - bh/2);

        g2.dispose();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
/**
 * Java2D stopwatch icon for the header timer.
 */
class StopwatchIcon extends JPanel {
    StopwatchIcon() { setOpaque(false); }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);
        int W = getWidth(), H = getHeight();
        int cx = W/2, cy = H/2 + 1;
        int r = Math.min(W,H)/2 - 2;
        Color col = new Color(0x3C, 0x46, 0x55);

        // Main circle
        g2.setColor(col);
        g2.setStroke(new BasicStroke(1.8f));
        g2.drawOval(cx-r, cy-r, r*2, r*2);

        // Crown at top
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx-3, cy-r, cx+3, cy-r);
        g2.drawLine(cx, cy-r, cx, cy-r-3);

        // Start button
        g2.fillOval(cx+r-3, cy-r-1, 4, 4);

        // Clock hand
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx, cy, cx+2, cy-r+4);

        // Centre dot
        g2.fillOval(cx-2, cy-2, 4, 4);
        g2.dispose();
    }
}
