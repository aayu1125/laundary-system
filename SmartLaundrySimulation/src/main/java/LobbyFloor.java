import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * Animated lobby floor showing customer icons in a flowing grid.
 * Icons animate into position whenever the set of customers changes.
 */
public class LobbyFloor extends JPanel {

    // Ordered map: customer id → icon component
    private final LinkedHashMap<Integer, CustomerIcon> icons = new LinkedHashMap<>();
    private javax.swing.Timer layoutTimer;
    private final JLabel emptyLabel;

    public LobbyFloor() {
        setOpaque(false);
        setLayout(null); // absolute for animation

        // Empty-state hint so the panel never looks dead/broken before
        // customers arrive or after a reset.
        emptyLabel = new JLabel("Lobby is empty");
        emptyLabel.setFont(Theme.FONT_SMALL);
        emptyLabel.setForeground(Theme.TEXT_INACTIVE);
        emptyLabel.setBounds(8, 8, 320, 20);
        add(emptyLabel);

        // Reposition every 400ms so new customers slide into place
        layoutTimer = new javax.swing.Timer(400, e -> repositionAll());
        layoutTimer.start();
    }

    public synchronized void addCustomer(int id, CustomerIcon.State state) {
        if (icons.containsKey(id)) { icons.get(id).setState(state); return; }
        CustomerIcon ic = new CustomerIcon(id, state);
        icons.put(id, ic);
        add(ic);
        repositionAll();
        refreshEmptyState();
        revalidate(); repaint();
    }

    public synchronized void removeCustomer(int id) {
        CustomerIcon ic = icons.remove(id);
        if (ic != null) { ic.stopAnimation(); remove(ic); repositionAll(); refreshEmptyState(); repaint(); }
    }

    public synchronized void setCustomerState(int id, CustomerIcon.State state) {
        CustomerIcon ic = icons.get(id);
        if (ic != null) ic.setState(state);
    }

    public synchronized int getCount() { return icons.size(); }

    /** Add or update customer as STUCK/red when washer fails. */
    public synchronized void addCustomerStuck(int id) {
        if (icons.containsKey(id)) {
            icons.get(id).setState(CustomerIcon.State.STUCK);
        } else {
            CustomerIcon ic = new CustomerIcon(id, CustomerIcon.State.STUCK);
            icons.put(id, ic);
            add(ic);
            repositionAll();
            refreshEmptyState();
            revalidate(); repaint();
        }
    }

    public synchronized void clear() {
        icons.values().forEach(CustomerIcon::stopAnimation);
        icons.clear();
        removeAll();
        add(emptyLabel);
        refreshEmptyState();
        repaint();
    }

    private void refreshEmptyState() {
        emptyLabel.setVisible(icons.isEmpty());
    }

    private void repositionAll() {
        QueueLayout.layout(icons.values(), getWidth(), getHeight());
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        repositionAll();
    }
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Payment waiting subroom — smaller version of LobbyFloor.
 */
class PayQueueFloor extends JPanel {

    private final LinkedHashMap<Integer, CustomerIcon> icons = new LinkedHashMap<>();
    private final JLabel countBadge;
    private final JLabel emptyLabel;

    public PayQueueFloor(JLabel countBadge) {
        this.countBadge = countBadge;
        setOpaque(false);
        setLayout(null);

        emptyLabel = new JLabel("No one waiting");
        emptyLabel.setFont(Theme.FONT_SMALL);
        emptyLabel.setForeground(Theme.TEXT_INACTIVE);
        emptyLabel.setBounds(8, 8, 200, 20);
        add(emptyLabel);

        new javax.swing.Timer(400, e -> repositionAll()).start();
    }

    public synchronized void addCustomer(int id, CustomerIcon.State state) {
        if (icons.containsKey(id)) { icons.get(id).setState(state); return; }
        CustomerIcon ic = new CustomerIcon(id, state);
        icons.put(id, ic);
        add(ic);
        repositionAll();
        refreshCount();
        revalidate(); repaint();
    }

    public synchronized void removeCustomer(int id) {
        CustomerIcon ic = icons.remove(id);
        if (ic != null) { ic.stopAnimation(); remove(ic); repositionAll(); refreshCount(); repaint(); }
    }

    public synchronized void setCustomerState(int id, CustomerIcon.State state) {
        CustomerIcon ic = icons.get(id);
        if (ic != null) ic.setState(state);
    }

    public synchronized void clear() {
        icons.values().forEach(CustomerIcon::stopAnimation);
        icons.clear(); removeAll(); add(emptyLabel);
        refreshCount(); repaint();
    }

    private void repositionAll() {
        QueueLayout.layout(icons.values(), getWidth(), getHeight());
        emptyLabel.setVisible(icons.isEmpty());
    }

    private void refreshCount() {
        if (countBadge != null) countBadge.setText(String.valueOf(icons.size()));
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        repositionAll();
    }
}

/** Shared "fit everyone on screen" grid layout for the waiting areas. Icons shrink when the room fills up. */
final class QueueLayout {
    private QueueLayout() {}
    static void layout(java.util.Collection<CustomerIcon> icons, int w, int h) {
        int n = icons.size();
        if (n == 0) return;
        if (w <= 0) w = 300;
        if (h <= 0) h = 200;
        final int BW = 44, BH = 56, PAD = 4;
        double k = 1.0;
        int iw = BW, ih = BH, cols = 1;
        for (; k >= 0.40; k -= 0.05) {
            iw = (int) Math.round(BW * k); ih = (int) Math.round(BH * k);
            cols = Math.max(1, (w - PAD * 2) / (iw + PAD));
            int rows = (n + cols - 1) / cols;
            if (rows * (ih + PAD) <= h - PAD) break;
        }
        int idx = 0;
        for (CustomerIcon ic : icons) {
            ic.setBounds(PAD + (idx % cols) * (iw + PAD), PAD + (idx / cols) * (ih + PAD), iw, ih);
            idx++;
        }
    }
}
