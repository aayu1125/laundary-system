import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * "Simulation complete" results window. Replaces the plain monospaced text pop-up with a
 * designed summary: success badge, headline, six result tiles, failure chips and actions.
 */
public class ResultsDialog extends JDialog {

    public static class Data {
        String scenario;
        int served, total, peakW, maxW, peakD, maxD, peakQ, washerFails, kioskFails;
        double avgSec, runSec;
        boolean ownerCalled;
        double avgWashWait, avgDryWait, avgPayWait, utilW, utilD, utilK, throughput;
    }

    public ResultsDialog(Frame owner, Data d, Runnable onReset) {
        super(owner, "Simulation complete", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, Color.WHITE, 0, getHeight(), new Color(0xA9, 0xD8, 0xF2)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setContentPane(root);

        RoundedPanel card = new RoundedPanel(Theme.CARD, Color.WHITE, 24);
        card.setLayout(new BorderLayout(0, 16));
        card.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        root.add(card, BorderLayout.CENTER);

        // ── header ──────────────────────────────────────────────────────────
        JPanel head = new JPanel(new BorderLayout(16, 0));
        head.setOpaque(false);
        CheckBadge badge = new CheckBadge();
        badge.setPreferredSize(new Dimension(56, 56));
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel t = new JLabel("Simulation complete");
        t.setFont(Theme.FONT_TITLE.deriveFont(Font.BOLD, 24f));
        t.setForeground(Theme.TEXT_WHITE);
        JLabel st = new JLabel(String.format("%s  ·  %d of %d customers served in %.1f s",
                d.scenario, d.served, d.total, d.runSec));
        st.setFont(Theme.FONT_NORMAL.deriveFont(13.5f));
        st.setForeground(Theme.TEXT_SECONDARY);
        titles.add(Box.createVerticalStrut(4));
        titles.add(t);
        titles.add(Box.createVerticalStrut(4));
        titles.add(st);
        head.add(badge, BorderLayout.WEST);
        head.add(titles, BorderLayout.CENTER);

        // served progress bar
        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(new ProgressStrip(d.total == 0 ? 0 : d.served / (float) d.total), BorderLayout.SOUTH);
        card.add(top, BorderLayout.NORTH);

        // ── tiles ───────────────────────────────────────────────────────────
        JPanel grid = new JPanel(new GridLayout(2, 3, 14, 14));
        grid.setOpaque(false);
        Color teal = Theme.TURQUOISE, blue = Theme.BLUE, violet = new Color(0x7A58D6),
              orange = Theme.ORANGE, rose = new Color(0xD6336C), green = Theme.GREEN;
        grid.add(new Tile("CUSTOMERS SERVED", d.served + " / " + d.total, teal));
        grid.add(new Tile("AVERAGE TIME", String.format("%.1f s", d.avgSec), blue));
        grid.add(new Tile("TOTAL RUN TIME", String.format("%.1f s", d.runSec), violet));
        grid.add(new Tile("PEAK WASHERS", d.peakW + " / " + d.maxW, green));
        grid.add(new Tile("PEAK DRYERS", d.peakD + " / " + d.maxD, orange));
        grid.add(new Tile("PEAK PAYMENT QUEUE", String.valueOf(d.peakQ), rose));
        card.add(grid, BorderLayout.CENTER);

        // ── chips + actions ─────────────────────────────────────────────────
        JPanel south = new JPanel(new BorderLayout(0, 14));
        south.setOpaque(false);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.setOpaque(false);
        chips.add(chip("Washer failures: " + d.washerFails, Theme.YELLOW));
        chips.add(chip("Kiosk failures: " + d.kioskFails, Theme.YELLOW));
        if (d.ownerCalled) chips.add(chip("Owner was called to the payment desk", Theme.RED));
        JPanel extra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        extra.setOpaque(false);
        extra.add(chip(String.format("Avg wait: washer %.1f s · dryer %.1f s · kiosk %.1f s", d.avgWashWait, d.avgDryWait, d.avgPayWait), Theme.BLUE));
        extra.add(chip(String.format("Throughput: %.1f customers/min", d.throughput), Theme.BLUE));
        JPanel extra2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        extra2.setOpaque(false);
        extra2.add(chip(String.format("Utilisation: washers %.0f%% · dryers %.0f%% · kiosks %.0f%%", 100 * d.utilW, 100 * d.utilD, 100 * d.utilK), Theme.GREEN));
        JPanel chipRows = new JPanel(new GridLayout(0, 1, 0, 6));
        chipRows.setOpaque(false);
        chipRows.add(chips); chipRows.add(extra); chipRows.add(extra2);
        south.add(chipRows, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        RoundedButton reset = new RoundedButton("Reset", Color.WHITE, Theme.TEXT_WHITE, 12, RoundedButton.Glyph.RESET);
        reset.setPreferredSize(new Dimension(110, 40));
        reset.addActionListener(e -> { dispose(); if (onReset != null) onReset.run(); });
        RoundedButton close = new RoundedButton("Close", Theme.TURQUOISE, Color.WHITE, 12);
        close.setPreferredSize(new Dimension(120, 40));
        close.addActionListener(e -> dispose());
        actions.add(reset);
        actions.add(close);
        south.add(actions, BorderLayout.SOUTH);
        card.add(south, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(close);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private static StatusBadge chip(String text, Color accent) {
        StatusBadge b = new StatusBadge(text, new Color(255, 255, 255, 235), accent);
        b.setFont(Theme.FONT_BADGE.deriveFont(12f));
        return b;
    }

    /** One result tile: small caption, large value, coloured accent bar on the left. */
    private static class Tile extends RoundedPanel {
        private final Color accent;
        Tile(String caption, String value, Color accent) {
            super(new Color(255, 255, 255, 215), Color.WHITE, 16);
            this.accent = accent;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(14, 26, 14, 12));
            setPreferredSize(new Dimension(190, 112));
            JLabel c = new JLabel(caption);
            c.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD, 11.5f));
            c.setForeground(Theme.TEXT_INACTIVE);
            JLabel v = new JLabel(value);
            v.setFont(Theme.FONT_STAT.deriveFont(Font.BOLD, 28f));
            v.setForeground(accent);
            add(Box.createVerticalGlue());
            add(c);
            add(Box.createVerticalStrut(8));
            add(v);
            add(Box.createVerticalGlue());
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.applyHints(g2);
            g2.setColor(accent);
            g2.fillRoundRect(11, 14, 5, getHeight() - 28, 5, 5);
            g2.dispose();
        }
    }

    /** Teal circle with a white check mark. */
    private static class CheckBadge extends JComponent {
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.applyHints(g2);
            int s = Math.min(getWidth(), getHeight()) - 6, x = 3, y = 3;
            for (int i = 6; i > 0; i--) {
                g2.setColor(new Color(Theme.TURQUOISE.getRed(), Theme.TURQUOISE.getGreen(), Theme.TURQUOISE.getBlue(), 14));
                g2.fill(new Ellipse2D.Double(x - i, y - i, s + 2 * i, s + 2 * i));
            }
            g2.setPaint(new GradientPaint(0, y, new Color(0x2CCDB3), 0, y + s, Theme.TURQUOISE.darker()));
            g2.fill(new Ellipse2D.Double(x, y, s, s));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double p = new Path2D.Double();
            p.moveTo(x + s * 0.28, y + s * 0.52);
            p.lineTo(x + s * 0.44, y + s * 0.68);
            p.lineTo(x + s * 0.74, y + s * 0.34);
            g2.draw(p);
            g2.dispose();
        }
    }

    /** Thin rounded bar showing served / total. */
    private static class ProgressStrip extends JComponent {
        private final float frac;
        ProgressStrip(float frac) { this.frac = Math.max(0, Math.min(1, frac)); setPreferredSize(new Dimension(10, 10)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.applyHints(g2);
            int w = getWidth(), h = 8, y = (getHeight() - h) / 2;
            g2.setColor(new Color(0xE0D9C8));
            g2.fillRoundRect(0, y, w, h, h, h);
            g2.setPaint(new GradientPaint(0, 0, Theme.TURQUOISE, w, 0, Theme.GREEN));
            g2.fillRoundRect(0, y, Math.max(h, (int) (w * frac)), h, h, h);
            g2.dispose();
        }
    }
}
