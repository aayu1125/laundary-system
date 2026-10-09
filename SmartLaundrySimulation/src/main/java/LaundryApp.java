import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * Smart Laundry Facility — Operations Console
 * CT074-3-2 Concurrent Programming, Asia Pacific University
 *
 * Entry point and MVC controller. Wires the SimulationModel callbacks
 * to all GUI components. All GUI mutations happen on the EDT via
 * SwingUtilities.invokeLater.
 * ════════════════════════════════════════════════════════════════════════════
 */
public class LaundryApp extends JFrame {

    // ── MODEL ─────────────────────────────────────────────────────────────────
    private final SimulationModel model = new SimulationModel();

    // ── UI COMPONENTS ─────────────────────────────────────────────────────────
    private HeaderPanel    header;
    private EventLogPanel  logPanel;

    // Dashboard cards
    private DashboardCard  lobbyCard, washCard, dryCard, payCard;
    private LobbyFloor     lobbyFloor;
    private PayQueueFloor  dryQueueFloor;
    private PayQueueFloor  payQueueFloor;

    private MachineCard[]  washerCards  = new MachineCard[SimulationModel.NUM_WASHERS];
    private MachineCard[]  dryerCards   = new MachineCard[SimulationModel.NUM_DRYERS];
    private MachineCard[]  kioskCards   = new MachineCard[SimulationModel.NUM_KIOSKS];

    // Stats
    private StatCard statServed, statAvg, statPeakW, statPeakD, statFails, statPeakQ;

    // Alert banner (payment outage)
    private JPanel alertBanner;
    private JLabel alertText;

    // Simulation clock ticker
    private javax.swing.Timer simClock;
    private boolean isPaused = false;
    private boolean completed = false;   // a run has finished and nothing has been reset since
    private final java.util.Map<Integer, Long> lobbyShownAt = new java.util.HashMap<>(); // EDT only

    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new LaundryApp().setVisible(true);
        });
    }

    public LaundryApp() {
        setTitle("Smart Laundry Facility — Operations Console");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1360, 720));
        setContentPane(new Backdrop());
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        buildUI();
        wireModel();

        // Everything time-related in the GUI follows the simulation clock (so 2x / 4x look right)
        MachineCard.clock = model::simNowMs;
        logPanel.setClock(model::simNowMs);

        // 1x / 2x / 4x playback speed: applies immediately, also in the middle of a run
        header.speedBox.setOnChange(() -> model.setSpeed(new double[] { 1.0, 2.0, 4.0 }[header.speedBox.getSelectedIndex()]));

        // After a run has completed, switching Normal <-> Congested resets the console by itself
        header.scenarioBox.setOnChange(() -> { if (completed) resetSim(); });

        // Stats refresh timer (10 fps)
        new javax.swing.Timer(100, e -> refreshStats()).start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UI CONSTRUCTION
    // ═════════════════════════════════════════════════════════════════════════

    private void buildUI() {
        header   = new HeaderPanel(this::startSim, this::resetSim, this::togglePause);
        logPanel = new EventLogPanel();

        JPanel center = buildDashboard();

        add(header,   BorderLayout.NORTH);
        add(center,   BorderLayout.CENTER);
        add(logPanel, BorderLayout.EAST);
    }

    private JPanel buildDashboard() {
        JPanel dash = new JPanel(new BorderLayout(0, Theme.GAP));
        dash.setOpaque(false);
        dash.setBorder(BorderFactory.createEmptyBorder(Theme.GAP, Theme.GAP, Theme.GAP, Theme.GAP));

        // 2×2 grid of cards
        JPanel grid = new JPanel(new GridLayout(2, 2, Theme.GAP, Theme.GAP));
        grid.setOpaque(false);
        grid.add(buildLobbyCard());
        grid.add(buildWashCard());
        grid.add(buildDryCard());
        grid.add(buildPayCard());

        dash.add(grid,            BorderLayout.CENTER);
        dash.add(buildStatsRow(), BorderLayout.SOUTH);
        return dash;
    }

    // ── LOBBY ─────────────────────────────────────────────────────────────────
    private DashboardCard buildLobbyCard() {
        lobbyCard = new DashboardCard("LOBBY & QUEUE", "0 here", SectionIcon.IconType.LOBBY);
        JPanel content = lobbyCard.getContentArea();
        content.setLayout(new BorderLayout());
        lobbyFloor = new LobbyFloor();
        content.add(lobbyFloor, BorderLayout.CENTER);
        return lobbyCard;
    }

    // ── WASH ROOM ─────────────────────────────────────────────────────────────
    private DashboardCard buildWashCard() {
        washCard = new DashboardCard("WASH ROOM", "0/" + SimulationModel.NUM_WASHERS + " in use", SectionIcon.IconType.WASHER);
        JPanel content = washCard.getContentArea();
        content.setLayout(new GridLayout(2, 3, 8, 8));
        for (int i = 0; i < SimulationModel.NUM_WASHERS; i++) {
            washerCards[i] = new MachineCard("W" + String.format("%02d", i+1), MachineCard.MachineType.WASHER);
            content.add(washerCards[i]);
        }
        return washCard;
    }

    // ── DRY ROOM ──────────────────────────────────────────────────────────────
    private DashboardCard buildDryCard() {
        dryCard = new DashboardCard("DRY ROOM", "0/" + SimulationModel.NUM_DRYERS + " in use", SectionIcon.IconType.DRYER);
        JPanel content = dryCard.getContentArea();
        content.setLayout(new BorderLayout(0, 8));

        JPanel dryerRow = new JPanel(new GridLayout(1, SimulationModel.NUM_DRYERS, 8, 8));
        dryerRow.setOpaque(false);
        dryerRow.setPreferredSize(new Dimension(0, 190));
        for (int i = 0; i < SimulationModel.NUM_DRYERS; i++) {
            dryerCards[i] = new MachineCard("D" + String.format("%02d", i+1), MachineCard.MachineType.DRYER);
            dryerRow.add(dryerCards[i]);
        }

        // Dry queue subroom — customers waiting for a free dryer.
        // Without this, customers between "finished washing" and "got a
        // dryer" were invisible, even though the spec requires that wait
        // to be handled (and visible) the same way the wash queue is.
        RoundedPanel subroom = new RoundedPanel(new Color(255, 255, 255, 170), Color.WHITE, 12);
        subroom.setLayout(new BorderLayout());

        JPanel subroomHead = new JPanel(new BorderLayout());
        subroomHead.setOpaque(false);
        subroomHead.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(255, 255, 255, 220)),
            BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        JPanel subroomLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        subroomLeft.setOpaque(false);
        MiniPersonIcon dqIcon = new MiniPersonIcon();
        dqIcon.setPreferredSize(new Dimension(13, 15));
        JLabel dqTitle = new JLabel("WAITING FOR DRYER");
        dqTitle.setFont(Theme.FONT_BADGE);
        dqTitle.setForeground(Theme.TEXT_SECONDARY);
        subroomLeft.add(dqIcon); subroomLeft.add(dqTitle);
        JLabel dqCount = new JLabel("0");
        dqCount.setFont(Theme.FONT_BADGE);
        dqCount.setForeground(Theme.TEXT_WHITE);
        subroomHead.add(subroomLeft, BorderLayout.WEST);
        subroomHead.add(dqCount, BorderLayout.EAST);

        dryQueueFloor = new PayQueueFloor(dqCount);

        subroom.add(subroomHead,  BorderLayout.NORTH);
        subroom.add(dryQueueFloor, BorderLayout.CENTER);

        content.add(dryerRow, BorderLayout.NORTH);
        content.add(subroom,  BorderLayout.CENTER);

        return dryCard;
    }

    // ── PAYMENT DESK ──────────────────────────────────────────────────────────
    private DashboardCard buildPayCard() {
        payCard = new DashboardCard("PAYMENT DESK", "0/" + SimulationModel.NUM_KIOSKS + " in use", SectionIcon.IconType.PAYMENT);
        JPanel content = payCard.getContentArea();
        content.setLayout(new BorderLayout(0, 8));

        // Alert banner (hidden initially) — matches image 4: dark red bg, red dot, monospace text
        alertBanner = new RoundedPanel(new Color(0xFF, 0xF1, 0xF4), new Color(0xD6, 0x28, 0x39), 8);
        alertBanner.setLayout(new BorderLayout(8, 0));
        alertBanner.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        alertBanner.setVisible(false);
        JLabel alertDot = new JLabel("●");
        alertDot.setFont(Theme.FONT_SMALL);
        alertDot.setForeground(Theme.RED);
        alertText = new JLabel("Payment system failed - owner has been informed and is on the way.");
        alertText.setFont(new Font("SansSerif", Font.BOLD, 12));
        alertText.setForeground(new Color(0x8A, 0x0F, 0x22));
        alertBanner.add(alertDot,  BorderLayout.WEST);
        alertBanner.add(alertText, BorderLayout.CENTER);

        // Kiosk row
        JPanel kioskRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        kioskRow.setOpaque(false);
        for (int i = 0; i < SimulationModel.NUM_KIOSKS; i++) {
            kioskCards[i] = new MachineCard("P" + String.format("%02d", i+1), MachineCard.MachineType.KIOSK);
            kioskRow.add(kioskCards[i]);
        }

        // Payment queue subroom
        RoundedPanel subroom = new RoundedPanel(new Color(255, 255, 255, 170), Color.WHITE, 12);
        subroom.setLayout(new BorderLayout());

        JPanel subroomHead = new JPanel(new BorderLayout());
        subroomHead.setOpaque(false);
        subroomHead.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(255, 255, 255, 220)),
            BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        JPanel subroomLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        subroomLeft.setOpaque(false);
        MiniPersonIcon sqIcon = new MiniPersonIcon();
        sqIcon.setPreferredSize(new Dimension(13, 15));
        JLabel sqTitle = new JLabel("WAITING FOR DESK");
        sqTitle.setFont(Theme.FONT_BADGE);
        sqTitle.setForeground(Theme.TEXT_SECONDARY);
        subroomLeft.add(sqIcon); subroomLeft.add(sqTitle);
        JLabel sqCount = new JLabel("0");
        sqCount.setFont(Theme.FONT_BADGE);
        sqCount.setForeground(Theme.TEXT_WHITE);
        subroomHead.add(subroomLeft, BorderLayout.WEST);
        subroomHead.add(sqCount, BorderLayout.EAST);

        payQueueFloor = new PayQueueFloor(sqCount);

        subroom.add(subroomHead, BorderLayout.NORTH);
        subroom.add(payQueueFloor, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(alertBanner, BorderLayout.NORTH);
        top.add(kioskRow,    BorderLayout.CENTER);

        content.add(top,     BorderLayout.NORTH);
        content.add(subroom, BorderLayout.CENTER);

        return payCard;
    }

    // ── STATS ROW ─────────────────────────────────────────────────────────────
    private JPanel buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 6, Theme.GAP, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(0, 90));

        statServed = new StatCard("SERVED",       "0 / " + SimulationModel.TOTAL_CUSTOMERS, Theme.TURQUOISE);
        statAvg    = new StatCard("AVG TIME",      "0.0s",    Theme.TEXT_WHITE);
        statPeakW  = new StatCard("PEAK WASHERS", "0 / " + SimulationModel.NUM_WASHERS,    Theme.TEXT_WHITE);
        statPeakD  = new StatCard("PEAK DRYERS",  "0 / " + SimulationModel.NUM_DRYERS,     Theme.TEXT_WHITE);
        statFails  = new StatCard("FAILURES (W / K)","0 / 0",  Theme.YELLOW);
        statPeakQ  = new StatCard("PEAK PAY QUEUE","0",       Theme.TEXT_WHITE);

        row.add(statServed); row.add(statAvg); row.add(statPeakW);
        row.add(statPeakD);  row.add(statFails); row.add(statPeakQ);
        return row;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MODEL → UI WIRING
    // ═════════════════════════════════════════════════════════════════════════

    private void wireModel() {
        // Log
        model.onLog = (msg, type) -> logPanel.log(msg, type);

        // Washer updates
        model.onWasherUpdate = (idx, state, who) -> SwingUtilities.invokeLater(() -> {
            washerCards[idx].setState(state, who, SimulationModel.WASH_MAX);
        });

        // Dryer updates
        model.onDryerUpdate = (idx, state, who) -> SwingUtilities.invokeLater(() -> {
            dryerCards[idx].setState(state, who, SimulationModel.DRY_MAX);
        });

        // Kiosk updates
        model.onKioskUpdate = (idx, state, who) -> SwingUtilities.invokeLater(() -> {
            kioskCards[idx].setState(state, who, SimulationModel.PAY_MAX);
        });

        // Lobby
        // The model has NO artificial delay. Purely for the eye, the lobby ICON stays
        // visible for at least 700 ms (UI only; simulation timings are untouched).
        model.onAddLobby      = id -> SwingUtilities.invokeLater(() -> {
            lobbyShownAt.put(id, System.currentTimeMillis());
            lobbyFloor.addCustomer(id, CustomerIcon.State.WAITING);
        });
        model.onAddLobbyStuck = id -> SwingUtilities.invokeLater(() -> {
            lobbyShownAt.put(id, System.currentTimeMillis());
            lobbyFloor.addCustomerStuck(id);
        });
        model.onRemoveLobby   = id -> SwingUtilities.invokeLater(() -> {
            Long t = lobbyShownAt.remove(id);
            long age = t == null ? 1000 : System.currentTimeMillis() - t;
            if (age >= 700) { lobbyFloor.removeCustomer(id); return; }
            javax.swing.Timer d = new javax.swing.Timer((int)(700 - age), e -> lobbyFloor.removeCustomer(id));
            d.setRepeats(false); d.start();
        });

        // Dry queue (waiting for a free dryer)
        model.onAddDryQueue    = id -> SwingUtilities.invokeLater(() -> dryQueueFloor.addCustomer(id, CustomerIcon.State.WAITING));
        model.onRemoveDryQueue = id -> SwingUtilities.invokeLater(() -> dryQueueFloor.removeCustomer(id));

        // Pay queue
        model.onAddPayQueue    = id -> SwingUtilities.invokeLater(() -> payQueueFloor.addCustomer(id, CustomerIcon.State.WAITING));
        model.onRemovePayQueue = id -> SwingUtilities.invokeLater(() -> payQueueFloor.removeCustomer(id));
        model.onPayQueueState  = (id, s) -> SwingUtilities.invokeLater(() -> {
            CustomerIcon.State cs = "serving".equals(s) ? CustomerIcon.State.SERVING : "stuck".equals(s) ? CustomerIcon.State.STUCK : CustomerIcon.State.WAITING;
            payQueueFloor.setCustomerState(id, cs);
        });

        // Kiosk outage / restore
        model.onKioskOutage = msg -> SwingUtilities.invokeLater(() -> {
            alertText.setText(msg == null || msg.isEmpty() ? "Payment system failed - owner has been informed and is on the way." : msg);
            alertBanner.setVisible(true);
            for (MachineCard c : kioskCards) c.setState(MachineCard.State.OUT_OF_SERVICE, null, 0);
        });
        model.onKioskRestored = () -> SwingUtilities.invokeLater(() -> {
            alertBanner.setVisible(false);
            for (MachineCard c : kioskCards) c.setState(MachineCard.State.IDLE, null, 0);
        });

        // Completion
        model.onComplete = () -> SwingUtilities.invokeLater(() -> {
            completed = true;
            header.btnStart.setText("✓ Complete");
            header.scenarioBox.setEnabled(true);
            header.btnPause.setEnabled(false);
            if (simClock != null) simClock.stop();
            showResults();
        });
    }

    /** Designed results window shown when the simulation finishes. */
    private void showResults() {
        ResultsDialog.Data d = new ResultsDialog.Data();
        int s = model.served.get();
        d.scenario    = new String[] { "Normal run", "Bonus: Congested", "Extra: Incidents" }[header.scenarioBox.getSelectedIndex()];
        d.avgWashWait = model.avgWashWaitSec(); d.avgDryWait = model.avgDryWaitSec(); d.avgPayWait = model.avgPayWaitSec();
        d.utilW = model.utilWash(); d.utilD = model.utilDry(); d.utilK = model.utilPay(); d.throughput = model.throughputPerMin();
        d.served      = s;
        d.total       = SimulationModel.TOTAL_CUSTOMERS;
        d.avgSec      = s > 0 ? model.totalTimeMs.get() / 1000.0 / s : 0;
        d.runSec      = model.runMs / 1000.0;
        d.peakW       = model.peakWashers.get();  d.maxW = SimulationModel.NUM_WASHERS;
        d.peakD       = model.peakDryers.get();   d.maxD = SimulationModel.NUM_DRYERS;
        d.peakQ       = model.peakPayQueue.get();
        d.washerFails = model.washerFails.get();
        d.kioskFails  = model.kioskFails.get();
        d.ownerCalled = model.ownerCalled.get();
        javax.swing.Timer t = new javax.swing.Timer(700, e -> { if (completed) new ResultsDialog(this, d, this::resetSim).setVisible(true); });
        t.setRepeats(false);
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ACTIONS
    // ═════════════════════════════════════════════════════════════════════════

    private void startSim() {
        if (model.running.get()) return;
        completed = false;
        logPanel.clear();
        logPanel.setSimStart(System.currentTimeMillis());
        model.scenario = SimulationModel.Scenario.values()[header.scenarioBox.getSelectedIndex()];
        header.scenarioBox.setEnabled(false);
        model.start();
        header.btnStart.setEnabled(false);
        header.btnStart.setText("● Running…");
        isPaused = false;
        header.btnPause.setEnabled(true);
        header.btnPause.setText("Pause");
            header.btnPause.setGlyph(RoundedButton.Glyph.PAUSE);

        // Sim clock
        if (simClock != null) simClock.stop();
        simClock = new javax.swing.Timer(250, e -> {
            long elapsed = model.simNowMs();
            header.simTimerLabel.setText(String.format(" %02d:%02d", elapsed/60000, (elapsed/1000)%60));
        });
        simClock.start();
    }

    private void togglePause() {
        if (!model.running.get()) return;
        if (!isPaused) {
            isPaused = true;
            model.pause();
            if (simClock != null) simClock.stop();
            header.btnPause.setText("Resume");
            header.btnPause.setGlyph(RoundedButton.Glyph.PLAY);
        } else {
            isPaused = false;
            model.resume();
            if (simClock != null) simClock.start();
            header.btnPause.setText("Pause");
            header.btnPause.setGlyph(RoundedButton.Glyph.PAUSE);
        }
    }

    private void resetSim() {
        completed = false;
        model.stop();
        if (simClock != null) simClock.stop();
        isPaused = false;
        SwingUtilities.invokeLater(() -> {
            logPanel.clear();
            header.btnStart.setEnabled(true);
            header.btnStart.setText("Start Simulation");
            header.scenarioBox.setEnabled(true);
            lobbyShownAt.clear();
            header.btnPause.setEnabled(false);
            header.btnPause.setText("Pause");
            header.btnPause.setGlyph(RoundedButton.Glyph.PAUSE);
            header.simTimerLabel.setText(" 00:00");
            alertBanner.setVisible(false);
            lobbyFloor.clear();
            dryQueueFloor.clear();
            payQueueFloor.clear();
            for (MachineCard c : washerCards) c.setState(MachineCard.State.IDLE);
            for (MachineCard c : dryerCards)  c.setState(MachineCard.State.IDLE);
            for (MachineCard c : kioskCards)  c.setState(MachineCard.State.IDLE);
        });
        model.reset();
    }

    // ── STATS REFRESH ─────────────────────────────────────────────────────────
    private void refreshStats() {
        int s  = model.served.get();
        int cw = model.curWashers.get();
        int cd = model.curDryers.get();
        int ck = model.curKiosks.get();
        int pq = model.payQueueSize.get();

        statServed.setValue(s + " / " + SimulationModel.TOTAL_CUSTOMERS);
        statAvg.setValue(s > 0 ? String.format("%.1fs", model.totalTimeMs.get()/1000.0/s) : "0.0s");
        statPeakW.setValue(model.peakWashers.get() + " / " + SimulationModel.NUM_WASHERS);
        statPeakD.setValue(model.peakDryers.get()  + " / " + SimulationModel.NUM_DRYERS);
        statFails.setValue(model.washerFails.get() + " / " + model.kioskFails.get());
        statPeakQ.setValue(String.valueOf(model.peakPayQueue.get()));

        washCard.setBadgeText(cw + "/" + SimulationModel.NUM_WASHERS + " in use");
        dryCard.setBadgeText(cd  + "/" + SimulationModel.NUM_DRYERS  + " in use");
        payCard.setBadgeText(ck  + "/" + SimulationModel.NUM_KIOSKS  + " in use");
        lobbyCard.setBadgeText(lobbyFloor.getCount() + " here");
    }
}

/**
 * Tiny Java2D person glyph used next to "WAITING FOR DESK" — replaces an
 * emoji character that depended on a specific system font and could render
 * as a missing-glyph box on machines without it.
 */
class MiniPersonIcon extends JPanel {
    MiniPersonIcon() { setOpaque(false); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);
        int w = getWidth(), h = getHeight();
        int cx = w / 2;
        g2.setColor(Theme.TEXT_SECONDARY);
        g2.fillOval(cx - 3, 0, 6, 6);                 // head
        g2.fillRoundRect(cx - 4, 6, 8, 8, 4, 4);       // torso
        g2.dispose();
    }
}


/** Window backdrop: white-to-sky-blue vertical gradient. */
class Backdrop extends JPanel {
    Backdrop() { setOpaque(true); }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.applyHints(g2);
        int w = getWidth(), h = getHeight();
        // vertical gradient: pure white at the top to light sky-blue at the bottom
        g2.setPaint(new GradientPaint(0, 0, Color.WHITE, 0, h, new Color(0xA9, 0xD8, 0xF2)));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }
}
