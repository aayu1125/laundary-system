import javax.swing.*;
import java.awt.*;

/**
 * Generic dashboard card: title + SectionIcon + badge + content area.
 */
public class DashboardCard extends RoundedPanel {

    private final JLabel      titleLabel;
    private final StatusBadge badge;
    private final JPanel      contentArea;

    public DashboardCard(String title, String badgeText, SectionIcon.IconType iconType) {
        super(Theme.CARD, Theme.BORDER, Theme.RADIUS);
        setLayout(new BorderLayout(0, 0));

        // ── Header ─────────────────────────────────────────────────────────
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);

        if (iconType != null) {
            SectionIcon icon = new SectionIcon(iconType);
            titleRow.add(icon);
        }

        titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.FONT_SECTION.deriveFont(Font.BOLD, 13f));
        titleLabel.setForeground(Theme.TEXT_SECONDARY);
        titleRow.add(titleLabel);
        headerRow.add(titleRow, BorderLayout.WEST);

        badge = new StatusBadge(badgeText);
        JPanel bw = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bw.setOpaque(false); bw.add(badge);
        headerRow.add(bw, BorderLayout.EAST);

        // ── Content ────────────────────────────────────────────────────────
        contentArea = new JPanel();
        contentArea.setOpaque(false);
        contentArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(headerRow,   BorderLayout.NORTH);
        add(contentArea, BorderLayout.CENTER);
    }

    public DashboardCard(String title, String badgeText) {
        this(title, badgeText, null);
    }

    public JPanel      getContentArea()       { return contentArea; }
    public StatusBadge getBadge()             { return badge; }
    public void        setBadgeText(String t) { badge.setText(t); }
    public void        addSouth(JComponent c) { add(c, BorderLayout.SOUTH); }
}
