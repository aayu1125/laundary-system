import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless runner with an automatic SAFETY SELF-CHECK (no GUI needed).
 *
 * Usage:  java ConsoleRunner [normal|congested|incidents] [speed 1|2|4]
 *
 * It runs the same SimulationModel as the GUI and watches every machine-state callback. The run
 * FAILS if a machine is ever started while it is still busy for another customer (mutual-exclusion
 * violation), if not all 50 customers are served, or if a peak exceeds the number of machines.
 * The exit code is 0 only when every check passes, so it can be used as a repeatable test.
 */
public class ConsoleRunner {

    public static void main(String[] args) throws Exception {
        String sc = args.length > 0 ? args[0].toLowerCase() : "normal";
        double speed = args.length > 1 ? Double.parseDouble(args[1]) : 1.0;

        final SimulationModel m = new SimulationModel();
        m.scenario = sc.startsWith("c") ? SimulationModel.Scenario.CONGESTED
                   : sc.startsWith("i") ? SimulationModel.Scenario.INCIDENTS : SimulationModel.Scenario.NORMAL;
        m.setSpeed(speed);

        final int[] violations = { 0 };
        final MachineCard.State[] w = new MachineCard.State[SimulationModel.NUM_WASHERS];
        final MachineCard.State[] d = new MachineCard.State[SimulationModel.NUM_DRYERS];
        final MachineCard.State[] k = new MachineCard.State[SimulationModel.NUM_KIOSKS];
        m.onWasherUpdate = (i, st, who) -> audit(w, i, st, violations, "washer");
        m.onDryerUpdate  = (i, st, who) -> audit(d, i, st, violations, "dryer");
        m.onKioskUpdate  = (i, st, who) -> audit(k, i, st, violations, "kiosk");

        CountDownLatch done = new CountDownLatch(1);
        m.onComplete = done::countDown;
        m.start();
        boolean finished = done.await(300, TimeUnit.SECONDS);
        Thread.sleep(300);

        boolean ok = finished
                  && m.served.get() == SimulationModel.TOTAL_CUSTOMERS
                  && violations[0] == 0
                  && m.peakWashers.get() <= SimulationModel.NUM_WASHERS
                  && m.peakDryers.get()  <= SimulationModel.NUM_DRYERS
                  && m.peakPayQueue.get() <= SimulationModel.PAY_ROOM;
        System.out.println();
        System.out.println("SELF-CHECK  finished=" + finished + "  served=" + m.served.get() + "/" + SimulationModel.TOTAL_CUSTOMERS
                + "  machine double-use=" + violations[0]
                + "  peaks W/D=" + m.peakWashers.get() + "/" + m.peakDryers.get()
                + "  ->  " + (ok ? "PASSED" : "FAILED"));
        System.exit(ok ? 0 : 1);
    }

    private static synchronized void audit(MachineCard.State[] s, int i, MachineCard.State now, int[] v, String what) {
        if (now == MachineCard.State.BUSY && s[i] == MachineCard.State.BUSY) {
            v[0]++;
            System.out.println("!!! VIOLATION: " + what + " " + (i + 1) + " started while already busy");
        }
        s[i] = now;
    }
}
