import javax.swing.*;
import java.awt.*;

/**
 * Bottom statistics card — large number, small label.
 */
public class StatCard extends RoundedPanel {

    private final JLabel valueLabel;
    private final JLabel titleLabel;

    public StatCard(String title, String initialValue, Color valueColor) {
        super(Theme.CARD, Theme.BORDER, Theme.RADIUS);
        setBorder(BorderFactory.createEmptyBorder(Theme.PAD, Theme.PAD, Theme.PAD, Theme.PAD));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.FONT_SMALL);
        titleLabel.setForeground(Theme.TEXT_SECONDARY);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);

        valueLabel = new JLabel(initialValue);
        valueLabel.setFont(Theme.FONT_STAT);
        valueLabel.setForeground(valueColor);
        valueLabel.setAlignmentX(LEFT_ALIGNMENT);

        add(titleLabel);
        add(Box.createVerticalStrut(6));
        add(valueLabel);
    }

    public void setValue(String v) {
        valueLabel.setText(v);
    }
}
