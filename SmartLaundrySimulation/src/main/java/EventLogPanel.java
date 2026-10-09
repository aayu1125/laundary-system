import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

/**
 * Live event log panel docked on the right.
 * Each event is colour-coded and auto-scrolls to newest entry.
 */
public class EventLogPanel extends RoundedPanel {

    public enum LogType { INFO, WASH, DRY, PAY, SUCCESS, WARN, ALARM, OWNER, FIXED }

    private final JTextPane pane;
    private final StyledDocument doc;
    private long simStartMs = 0;

    public EventLogPanel() {
        super(new Color(0xF2,0xEE,0xE3), Theme.BORDER, Theme.RADIUS);
        setLayout(new BorderLayout(0, 0));
        setPreferredSize(new Dimension(420, 0));

        // ── Header ───────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        JLabel title = new JLabel("LIVE EVENT LOG");
        title.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
        title.setForeground(Theme.TEXT_SECONDARY);

        JPanel indicator = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        indicator.setOpaque(false);
        JLabel dot = new JLabel("●") {
            float a = 1f; boolean up = false;
            { new javax.swing.Timer(600, e -> {
                if (up) { a += 0.06f; if (a >= 1f) { a = 1f; up = false; } }
                else     { a -= 0.06f; if (a <= 0.2f){ a = 0.2f; up = true; } }
                setForeground(new Color(Theme.GREEN.getRed(), Theme.GREEN.getGreen(), Theme.GREEN.getBlue(), (int)(a*255)));
            }).start(); }
        };
        dot.setFont(Theme.FONT_NORMAL);
        dot.setForeground(Theme.GREEN);
        JLabel streaming = new JLabel("STREAMING");
        streaming.setFont(Theme.FONT_BADGE);
        streaming.setForeground(Theme.GREEN);
        indicator.add(dot); indicator.add(streaming);

        header.add(title, BorderLayout.WEST);
        header.add(indicator, BorderLayout.EAST);

        // ── Text Pane ────────────────────────────────────────────────────────
        pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(new Color(0xF2,0xEE,0xE3));
        pane.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 8));
        doc = pane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(5, 0));
        scroll.getVerticalScrollBar().setBackground(new Color(0xF2,0xEE,0xE3));

        // ── Footer ───────────────────────────────────────────────────────────
        JLabel footer = new JLabel("Each entry is timestamped per-thread — no thread acts for another");
        footer.setFont(new Font("SansSerif", Font.PLAIN, 9));
        footer.setForeground(Theme.TEXT_INACTIVE);
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(7, 14, 7, 14)));

        add(header, BorderLayout.NORTH);
        add(scroll,  BorderLayout.CENTER);
        add(footer,  BorderLayout.SOUTH);
    }

    public void setSimStart(long ms) { this.simStartMs = ms; }

    private java.util.function.LongSupplier clock;     // simulated elapsed ms (optional)
    public void setClock(java.util.function.LongSupplier c) { this.clock = c; }

    /** Append a coloured log line. Thread-safe via invokeLater. */
    public void log(String message, LogType type) {
        final long el = clock != null ? clock.getAsLong() : System.currentTimeMillis() - simStartMs;
        SwingUtilities.invokeLater(() -> {
            try {
                String ts = simStartMs > 0
                    ? String.format("[%02d:%02d] ", el/60000, el/1000%60)
                    : "[--:--] ";

                // Timestamp style
                Style tsStyle = pane.addStyle("ts", null);
                StyleConstants.setForeground(tsStyle, Theme.TEXT_INACTIVE);
                StyleConstants.setFontFamily(tsStyle, "Consolas");
                StyleConstants.setFontSize(tsStyle, 11);
                doc.insertString(doc.getLength(), ts, tsStyle);

                // Message style
                Style msgStyle = pane.addStyle("msg", null);
                StyleConstants.setFontFamily(msgStyle, "SansSerif");
                StyleConstants.setFontSize(msgStyle, 12);
                StyleConstants.setBold(msgStyle, false);
                switch (type) {
                    case WASH:    StyleConstants.setForeground(msgStyle, Theme.BLUE);        break;
                    case DRY:     StyleConstants.setForeground(msgStyle, Theme.ORANGE);      break;
                    case PAY:     StyleConstants.setForeground(msgStyle, Theme.TURQUOISE);   break;
                    case SUCCESS: StyleConstants.setForeground(msgStyle, Theme.GREEN);       break;
                    case WARN:    StyleConstants.setForeground(msgStyle, Theme.YELLOW);      break;
                    case ALARM:   StyleConstants.setForeground(msgStyle, new Color(0xC1,0x12,0x1F)); StyleConstants.setBold(msgStyle, true); break;   // RED    = failure / alarm
                    case OWNER:   StyleConstants.setForeground(msgStyle, new Color(0x80,0x50,0x00)); StyleConstants.setBold(msgStyle, true); break;   // YELLOW = owner informed / called
                    case FIXED:   StyleConstants.setForeground(msgStyle, new Color(0x0A,0x78,0x39)); StyleConstants.setBold(msgStyle, true); break;   // GREEN  = problem solved
                    default:      StyleConstants.setForeground(msgStyle, Theme.TEXT_SECONDARY);
                }
                doc.insertString(doc.getLength(), message + "\n", msgStyle);

                // Auto-scroll
                pane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}
        });
    }
}
