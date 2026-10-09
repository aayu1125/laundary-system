import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * SIMULATION MODEL  -  all concurrency logic, zero Swing
 * CT074-3-2 Concurrent Programming - Smart Laundry Facility
 * ════════════════════════════════════════════════════════════════════════════
 *
 * CONCURRENCY FACILITIES USED (and why)
 *
 *  Thread (x50)        Every customer is its own thread (basic req. 1).
 *  Semaphore (fair)    dryerSem(4): counting semaphore = "4 identical dryers".
 *  FifoGate            washerGate(6) and payGate(2): counting semaphores that serve
 *                      customers strictly in TICKET order. A customer whose machine
 *                      failed keeps his ticket and is served before later arrivals.
 *  ReentrantLock       `lock` guards the machine-owner arrays so "find a free
 *                      slot + mark it mine" is ONE atomic step.
 *  Condition           `kiosksOnline` (on gateLock): customers WAIT (no polling)
 *                      while the payment system is down; the owner thread
 *                      signalAll()s when it is repaired.
 *  AtomicInteger/Long  Lock-free statistics counters; atomicMax() is a CAS loop.
 *  AtomicBoolean       Lock-free flags (running, paused, kiosksDown, ownerCalled).
 *  synchronized        log() - lines from different threads never interleave.
 *  Helper threads      Repair-Wxx (4 s), KioskReset-Pxx (2 s), Owner and Monitor run
 *                      independently of the customers; each logs under its own name.
 *
 * SCENARIOS    NORMAL    : the brief's basic + additional requirements. Random 5 % washer faults
 *                         (mid-cycle) and 5 % kiosk faults (retry after 2 s) - report assumption A8.
 *              CONGESTED : bonus. Both kiosks are out of order from the start, the owner is called at
 *                         30 waiting, at most 31 customers wait (A6). The 5 % washer faults still apply.
 *              INCIDENTS : extra. Instead of random faults, four scripted incidents run one at a time
 *                         (single machine, both kiosks, two washers at once, single kiosk) so the owner
 *                         repairs can be shown on demand (A7). A double failure counts as 2 failures.
 *
 * EXTRA STATISTICS  average wait per stage, machine utilisation, throughput, results file.
 *
 * SPEC TIMINGS (exact, no hidden delays):
 *   arrivals 0-3 s | wash 4-6 s | dry 3-5 s | pay 1-2 s | kiosk retry 2 s
 *   washer fail 5 % (mid-cycle) | kiosk fail 5 % | owner called at 30 queued
 */
public class SimulationModel {

    public enum Scenario { NORMAL, CONGESTED, INCIDENTS }

    // ── CONFIG (from the assignment brief) ───────────────────────────────────
    public static final int    NUM_WASHERS      = 6;
    public static final int    NUM_DRYERS       = 4;
    public static final int    NUM_KIOSKS       = 2;
    public static final int    TOTAL_CUSTOMERS  = 50;
    public static final double FAIL_RATE        = 0.05;
    public static final int    OWNER_THRESHOLD  = 30;

    static final int ARRIVE_MIN = 0,    ARRIVE_MAX = 3000;
    static final int WASH_MIN   = 4000, WASH_MAX   = 6000;
    static final int DRY_MIN    = 3000, DRY_MAX    = 5000;
    static final int PAY_MIN    = 1000, PAY_MAX    = 2000;
    static final int KIOSK_FAIL_RETRY = 2000;   // brief: "retry after 2 seconds"

    // ── ASSUMPTIONS (state these in the report) ──────────────────────────────
    static final int WASHER_FIX_DELAY   = 4000; // A4: a failed washer is repaired after 4 s
    static final int WASHER_RETRY_WAIT  = 2000; // A4: the customer waits 2 s (red, in lobby) then retries
    static final int OWNER_ARRIVAL_DELAY= 3000; // A6: the owner needs 3 s to arrive when called
    static final int OWNER_FIX_TIME     = 2000; // A6: and 2 s to repair both kiosks
    static final int PAY_ROOM           = 31;   // A6: the payment waiting area holds 31 customers
    // Assumption numbers below are the SAME as in the report (Section 2):
    // A3: run time ~80-100 s. 49 arrival gaps x avg 1.5 s = ~73 s of arrivals alone, so "about 60 s"
    //     cannot hold together with the 0-3 s arrival rule; the brief's own "1-2 minutes" is followed.
    // A4: a washer fails between 25 % and 75 % of its cycle; the customer waits 2 s and re-queues
    //     (keeping his ticket); the broken washer is repaired after 4 s.
    // A5: a kiosk fails half-way through a payment, restarts after 2 s, the customer retries after 2 s.
    // A6: CONGESTED: owner called at 30 waiting, arrives in 3 s, repairs both kiosks in 2 s; the payment
    //     waiting area holds 31 customers.
    // A7: INCIDENTS scenario (extra) - four incidents, one at a time, each at a different customer
    //     number on every run:
    //       1) customers 15-20 : ONE machine fails - a washer or a kiosk (random per run);
    //       2) customers 22-25 : BOTH kiosks fail; the owner is called when the 4th customer joins the queue;
    //       3) customers 30-36 : TWO washers fail together; owner arrives in 2 s, repairs in 3 s;
    //       4) customers 38-42 : ONE kiosk fails (back after 2 s).
    // A8: NORMAL and CONGESTED use the random 5 % faults of the brief; INCIDENTS replaces them.
    static final int WASHER_OWNER_ARRIVE = 2000;   // two washers down: owner arrives in 2 s ...
    static final int WASHER_OWNER_FIX    = 3000;   // ... and fixes both in 3 s  => back after 5 s in total
    static final int KIOSK_QUEUE_LIMIT   = 4;      // both kiosks down: owner is called when the 4th customer joins the queue
    static final int KIOSK_OWNER_ARRIVE  = 1000;   // ... arrives in 1 s ...
    static final int KIOSK_OWNER_FIX     = 1500;   // ... and fixes both kiosks in 1.5 s
    static final int    FAULT_GRACE        = 1500;  // a scheduled fault is dropped after 1.5 s

    /** Playback speed chosen in the GUI (1x, 2x or 4x). 1.0 = exact spec timings. */
    public volatile double speed = 1.0;

    // Simulation clock: advances `speed` times faster than the wall clock and stands still while paused.
    // All reported times (customer time, run time, log stamps) are in this simulated time, so the
    // statistics are the same whether the run is watched at 1x, 2x or 4x.
    private final Object clockLock = new Object();
    private double virtMs = 0;
    private long   lastReal = System.currentTimeMillis();
    public volatile long runMs = 0;     // total simulated run time, fixed when the run completes

    public long simNowMs() {
        synchronized (clockLock) {
            long r = System.currentTimeMillis();
            if (running.get() && !paused.get()) virtMs += (r - lastReal) * speed;
            lastReal = r;
            return (long) virtMs;
        }
    }
    private void clockZero() {
        synchronized (clockLock) { virtMs = 0; lastReal = System.currentTimeMillis(); }
    }
    public void setSpeed(double s) {
        simNowMs();            // bring the clock up to date at the old speed first
        speed = s;
    }

    // ── CONCURRENCY PRIMITIVES ───────────────────────────────────────────────
    final FifoGate  washerGate = new FifoGate(NUM_WASHERS);   // ticket = customer number
    final Semaphore dryerSem   = new Semaphore(NUM_DRYERS,  true);
    final FifoGate  payGate    = new FifoGate(NUM_KIOSKS);    // ticket = order of joining the payment queue
    final ReentrantLock lock  = new ReentrantLock();
    final ReentrantLock gateLock = new ReentrantLock();
    final Condition kiosksOnline = gateLock.newCondition();

    final String[]  washerOwner  = new String[NUM_WASHERS];
    final boolean[] washerFailed = new boolean[NUM_WASHERS];
    final String[]  dryerOwner   = new String[NUM_DRYERS];
    final String[]  kioskOwner   = new String[NUM_KIOSKS];
    final boolean[] kioskDown    = new boolean[NUM_KIOSKS];

    final AtomicInteger served       = new AtomicInteger(0);
    final AtomicLong    totalTimeMs  = new AtomicLong(0);
    final AtomicInteger peakWashers  = new AtomicInteger(0);
    final AtomicInteger peakDryers   = new AtomicInteger(0);
    final AtomicInteger peakPayQueue = new AtomicInteger(0);
    final AtomicInteger washerFails  = new AtomicInteger(0);
    final AtomicInteger kioskFails   = new AtomicInteger(0);
    // extra statistics (simulated ms): waiting time per stage and machine busy time
    final AtomicLong washWaitMs = new AtomicLong(0), dryWaitMs = new AtomicLong(0), payWaitMs = new AtomicLong(0);
    final AtomicLong washBusyMs = new AtomicLong(0), dryBusyMs  = new AtomicLong(0), payBusyMs  = new AtomicLong(0);
    final AtomicInteger curWashers   = new AtomicInteger(0);
    final AtomicInteger curDryers    = new AtomicInteger(0);
    final AtomicInteger curKiosks    = new AtomicInteger(0);
    final AtomicInteger payQueueSize = new AtomicInteger(0);
    final AtomicBoolean running      = new AtomicBoolean(false);
    final AtomicBoolean paused       = new AtomicBoolean(false);
    final AtomicBoolean kiosksDown   = new AtomicBoolean(false);
    final AtomicBoolean ownerCalled  = new AtomicBoolean(false);
    // time (ms) at which a shared-cause fault will hit this machine; 0 = none pending
    final AtomicLong[]  washerFaultAt = newAtomicLongs(NUM_WASHERS);
    final AtomicLong[]  kioskFaultAt  = newAtomicLongs(NUM_KIOSKS);
    final AtomicLong[]  washerEndAt   = newAtomicLongs(NUM_WASHERS);   // when each running cycle ends
    final boolean[]     washerOwnFault = new boolean[NUM_WASHERS];     // guarded by lock
    final boolean[]     washerPair     = new boolean[NUM_WASHERS];     // guarded by lock
    final AtomicInteger washCyclesStarted = new AtomicInteger(0);
    final AtomicInteger payJoins          = new AtomicInteger(0);
    final AtomicInteger pairFailed        = new AtomicInteger(0);
    final AtomicBoolean washerPairDone    = new AtomicBoolean(false);
    final AtomicBoolean kioskPairDone     = new AtomicBoolean(false);
    final AtomicBoolean kioskOwnerPending = new AtomicBoolean(false);   // both kiosks down, owner not yet called
    // Normal-run incident script: which incident is next, and the customer number it starts at.
    final AtomicInteger stage    = new AtomicInteger(1);      // 1..4 = next incident, 5 = all done
    final AtomicBoolean incident = new AtomicBoolean(false);  // an incident is in progress
    private volatile int n1 = 17, n2 = 23, n3 = 33, n4 = 40;
    private volatile boolean firstIsKiosk = false;
    private volatile Semaphore payRoom = new Semaphore(PAY_ROOM, true);
    private volatile int[] pairWashers = new int[] { -1, -1 };
    private volatile long pairFailedAt = 0;      // sim time (ms) when the two washers broke
    final AtomicLong    pausedTotalMs = new AtomicLong(0);
    private volatile long pauseStartedAt = 0;
    final AtomicLong    startTime    = new AtomicLong(0);
    private final Object pauseLock   = new Object();
    private final Object logLock     = new Object();

    public volatile Scenario scenario = Scenario.NORMAL;

    private final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());

    // ── CALLBACKS ────────────────────────────────────────────────────────────
    public TriConsumer<Integer, MachineCard.State, String> onWasherUpdate;
    public TriConsumer<Integer, MachineCard.State, String> onDryerUpdate;
    public TriConsumer<Integer, MachineCard.State, String> onKioskUpdate;
    public BiConsumer<String, EventLogPanel.LogType> onLog;
    public Consumer<String> onKioskOutage;
    public Runnable onKioskRestored;
    public Runnable onComplete;
    public Consumer<Integer> onAddLobby;
    public Consumer<Integer> onAddLobbyStuck;
    public Consumer<Integer> onRemoveLobby;
    public Consumer<Integer> onAddDryQueue;
    public Consumer<Integer> onRemoveDryQueue;
    public Consumer<Integer> onAddPayQueue;
    public Consumer<Integer> onRemovePayQueue;
    public BiConsumer<Integer, String> onPayQueueState;

    @FunctionalInterface public interface TriConsumer<A,B,C> { void accept(A a, B b, C c); }

    private static AtomicLong[] newAtomicLongs(int n) {
        AtomicLong[] a = new AtomicLong[n];
        for (int i = 0; i < n; i++) a[i] = new AtomicLong(0);
        return a;
    }

    private static String two(int n) { return String.format("%02d", n); }

    // ═════════════════════════════════════════════════════════════════════════
    public void start() {
        if (running.get()) return;
        reset();
        ThreadLocalRandom r0 = ThreadLocalRandom.current();
        n1 = 15 + r0.nextInt(6);              // 15-20
        n2 = 22 + r0.nextInt(4);              // 22-25
        n3 = 30 + r0.nextInt(7);              // 30-36
        n4 = 38 + r0.nextInt(5);              // 38-42
        firstIsKiosk = r0.nextBoolean();      // 1st incident: a kiosk on some runs, a washer on others
        clockZero();
        running.set(true);
        startTime.set(System.currentTimeMillis());

        log("=== SMART LAUNDRY FACILITY SIMULATION STARTING ===", EventLogPanel.LogType.INFO);
        log(NUM_WASHERS + " washers | " + NUM_DRYERS + " dryers | " + NUM_KIOSKS
            + " kiosks | " + TOTAL_CUSTOMERS + " customers | scenario: " + scenario, EventLogPanel.LogType.INFO);

        if (scenario == Scenario.CONGESTED) {
            lock.lock();
            try { Arrays.fill(kioskDown, true); } finally { lock.unlock(); }
            setKiosksDown(true);
            kioskFails.addAndGet(NUM_KIOSKS);      // two kiosks are broken
            log("CONGESTED SCENARIO: both payment kiosks are out of order for the day.", EventLogPanel.LogType.ALARM);
            if (onKioskOutage != null)
                onKioskOutage.accept("Both payment kiosks are OUT OF ORDER - customers queue until the owner is called (at "
                                     + OWNER_THRESHOLD + " waiting).");
        }

        spawn("Arrival-Spawner", () -> {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            List<Thread> customers = new ArrayList<>();
            for (int i = 1; i <= TOTAL_CUSTOMERS && running.get(); i++) {
                final int cid = i;
                customers.add(spawn("Customer-" + two(cid), () -> runCustomer(cid)));
                if (i == TOTAL_CUSTOMERS) break;
                try { pausableSleep(ARRIVE_MIN + rng.nextInt(ARRIVE_MAX - ARRIVE_MIN + 1)); }
                catch (InterruptedException e) { return; }
            }
            // A separate Monitor thread waits for every customer and prints the final report,
            // so the summary is not printed "on behalf of" the arrival thread.
            spawn("Monitor", () -> {
                for (Thread t : customers) { try { t.join(); } catch (InterruptedException e) { return; } }
                if (running.get()) finish();
            });
        });
    }

    private void finish() {
        int s = served.get();
        double avg = s > 0 ? totalTimeMs.get() / 1000.0 / s : 0;
        runMs = simNowMs();
        double runSec = runMs / 1000.0;
        log("=== SIMULATION COMPLETE ===", EventLogPanel.LogType.SUCCESS);
        log(String.format("Total customers served: %d / %d", s, TOTAL_CUSTOMERS), EventLogPanel.LogType.SUCCESS);
        log(String.format("Average total time per customer: %.1f s", avg), EventLogPanel.LogType.SUCCESS);
        log(String.format("Max concurrent washers: %d / %d   |   dryers: %d / %d",
            peakWashers.get(), NUM_WASHERS, peakDryers.get(), NUM_DRYERS), EventLogPanel.LogType.SUCCESS);
        log(String.format("Washer failures: %d  |  Kiosk failures: %d  |  Peak payment queue: %d  |  Run time: %.0f s",
            washerFails.get(), kioskFails.get(), peakPayQueue.get(), runSec), EventLogPanel.LogType.SUCCESS);
        log(String.format("Average wait: washer %.1f s | dryer %.1f s | kiosk %.1f s   |   Utilisation: washers %.0f%% | dryers %.0f%% | kiosks %.0f%%   |   Throughput: %.1f customers/min",
            avgWashWaitSec(), avgDryWaitSec(), avgPayWaitSec(), 100 * utilWash(), 100 * utilDry(), 100 * utilPay(), throughputPerMin()),
            EventLogPanel.LogType.SUCCESS);
        saveResults();
        if (onComplete != null) onComplete.run();
    }

    // ── extra statistics (all in simulated time) ─────────────────────────────
    private double perServed(AtomicLong total) { int s = served.get(); return s > 0 ? total.get() / 1000.0 / s : 0; }
    public double avgWashWaitSec() { return perServed(washWaitMs); }
    public double avgDryWaitSec()  { return perServed(dryWaitMs); }
    public double avgPayWaitSec()  { return perServed(payWaitMs); }
    private double util(AtomicLong busy, int machines) { return runMs > 0 ? Math.min(1.0, busy.get() / (double) (machines * runMs)) : 0; }
    public double utilWash() { return util(washBusyMs, NUM_WASHERS); }
    public double utilDry()  { return util(dryBusyMs,  NUM_DRYERS); }
    public double utilPay()  { return util(payBusyMs,  NUM_KIOSKS); }
    public double throughputPerMin() { return runMs > 0 ? served.get() * 60000.0 / runMs : 0; }

    /** Saves the final statistics next to the program (simulation_results.txt). Failure to write is harmless. */
    private void saveResults() {
        try {
            String txt = String.format(
                "Smart Laundry Facility Simulation - results (%s)%n"
              + "Customers served: %d / %d%nAverage time per customer: %.1f s%nRun time: %.1f s%n"
              + "Max concurrent washers: %d / %d%nMax concurrent dryers: %d / %d%n"
              + "Washer failures: %d%nKiosk failures: %d%nPeak payment queue: %d%nOwner called: %s%n"
              + "Average wait (washer / dryer / kiosk): %.1f / %.1f / %.1f s%n"
              + "Utilisation (washers / dryers / kiosks): %.0f%% / %.0f%% / %.0f%%%nThroughput: %.1f customers/min%n",
                scenario, served.get(), TOTAL_CUSTOMERS, totalTimeMs.get() / 1000.0 / Math.max(1, served.get()), runMs / 1000.0,
                peakWashers.get(), NUM_WASHERS, peakDryers.get(), NUM_DRYERS,
                washerFails.get(), kioskFails.get(), peakPayQueue.get(), ownerCalled.get() ? "yes" : "no",
                avgWashWaitSec(), avgDryWaitSec(), avgPayWaitSec(), 100 * utilWash(), 100 * utilDry(), 100 * utilPay(), throughputPerMin());
            java.nio.file.Files.write(java.nio.file.Paths.get("simulation_results.txt"), txt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            log("Results saved to simulation_results.txt", EventLogPanel.LogType.INFO);
        } catch (Exception ignored) { }
    }

    private Thread spawn(String name, Runnable r) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        threads.add(t);
        t.start();
        return t;
    }

    public void stop() {
        running.set(false);
        paused.set(false);
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        gateLock.lock();
        try { kiosksOnline.signalAll(); } finally { gateLock.unlock(); }
        List<Thread> copy;
        synchronized (threads) { copy = new ArrayList<>(threads); threads.clear(); }
        for (Thread t : copy) t.interrupt();
        final long deadline = System.currentTimeMillis() + 500;   // never block the caller (GUI) long
        for (Thread t : copy) {
            if (t == Thread.currentThread()) continue;
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) break;
            try { t.join(left); } catch (InterruptedException ignored) {}
        }
    }

    public void pause() {
        if (running.get() && !paused.get()) simNowMs();      // freeze the sim clock at this moment
        if (running.get() && paused.compareAndSet(false, true)) {
            pauseStartedAt = System.currentTimeMillis();
            log("Simulation paused.", EventLogPanel.LogType.INFO);
        }
    }
    public void resume() {
        if (running.get() && paused.get()) synchronized (clockLock) { lastReal = System.currentTimeMillis(); }
        if (running.get() && paused.compareAndSet(true, false)) {
            long ps = pauseStartedAt;
            if (ps > 0) pausedTotalMs.addAndGet(System.currentTimeMillis() - ps);
            pauseStartedAt = 0;
            synchronized (pauseLock) { pauseLock.notifyAll(); }
            log("Simulation resumed.", EventLogPanel.LogType.INFO);
        }
    }

    /** Total time the simulation has spent paused so far (so pauses do not inflate customer times). */
    private long pausedSoFar() {
        long ps = pauseStartedAt;
        return pausedTotalMs.get() + (ps > 0 ? System.currentTimeMillis() - ps : 0);
    }

    /**
     * Sleeps for ms (pausable). Returns true as soon as a shared-cause fault scheduled in
     * `fault` strikes while sleeping, false if the full time elapsed without one.
     */
    private boolean sleepUntilFault(long ms, AtomicLong fault) throws InterruptedException {
        long remaining = ms;
        final long SLICE = 50;
        while (remaining > 0) {
            long slice = Math.min(SLICE, remaining);
            pausableSleep(slice);
            remaining -= slice;
            long at = fault.get();
            if (at > 0) {
                long now = System.currentTimeMillis();
                if (now > at + FAULT_GRACE) fault.compareAndSet(at, 0);          // stale: drop it
                else if (now >= at && fault.compareAndSet(at, 0)) return true;   // fault strikes now
            }
        }
        return false;
    }

    private void pausableSleep(long ms) throws InterruptedException {
        // `ms` is simulated time. We sleep in short real-time slices and re-read the speed on every
        // slice, so changing 1x/2x/4x takes effect immediately, even in the middle of a wash cycle.
        double remaining = ms;
        while (remaining > 0) {
            synchronized (pauseLock) {
                while (paused.get() && running.get()) pauseLock.wait();
            }
            if (!running.get()) throw new InterruptedException("stopped");
            double sp = speed;
            long real = (long) Math.max(1, Math.min(50, Math.ceil(remaining / sp)));
            Thread.sleep(real);
            remaining -= real * sp;
        }
    }

    public void reset() {
        stop();
        served.set(0); totalTimeMs.set(0);
        peakWashers.set(0); peakDryers.set(0); peakPayQueue.set(0);
        washerFails.set(0); kioskFails.set(0);
        curWashers.set(0);  curDryers.set(0);  curKiosks.set(0);
        payQueueSize.set(0); ownerCalled.set(false);
        for (AtomicLong f : washerFaultAt) f.set(0);
        for (AtomicLong f : kioskFaultAt)  f.set(0);
        pausedTotalMs.set(0); pauseStartedAt = 0;
        clockZero(); runMs = 0;
        for (AtomicLong e : washerEndAt) e.set(0);
        washWaitMs.set(0); dryWaitMs.set(0); payWaitMs.set(0);
        washBusyMs.set(0); dryBusyMs.set(0);  payBusyMs.set(0);
        washCyclesStarted.set(0); payJoins.set(0); pairFailed.set(0);
        washerPairDone.set(false); kioskPairDone.set(false); kioskOwnerPending.set(false);
        pairWashers = new int[] { -1, -1 };
        stage.set(1); incident.set(false);
        payRoom = new Semaphore(PAY_ROOM, true);
        paused.set(false);
        setKiosksDown(false);
        lock.lock();
        try {
            Arrays.fill(washerOwner, null); Arrays.fill(washerFailed, false);
            Arrays.fill(washerOwnFault, false); Arrays.fill(washerPair, false);
            Arrays.fill(dryerOwner, null);
            Arrays.fill(kioskOwner, null);  Arrays.fill(kioskDown, false);
        } finally { lock.unlock(); }
        washerGate.reset(NUM_WASHERS);
        dryerSem.drainPermits();  dryerSem.release(NUM_DRYERS);
        payGate.reset(NUM_KIOSKS);
    }

    // ═════════════════════════════════════════════════════════════════════════
    private void runCustomer(int id) {
        final long t0 = simNowMs();
        try {
            if (onAddLobby != null) onAddLobby.accept(id);
            log("arrived - waiting in lobby.", EventLogPanel.LogType.INFO);
            washStage(id);
            dryStage(id);
            payStage(id);
            long elapsed = simNowMs() - t0;
            served.incrementAndGet();
            totalTimeMs.addAndGet(elapsed);
            log(String.format("DONE - total time %.1f s", elapsed / 1000.0), EventLogPanel.LogType.SUCCESS);
        } catch (InterruptedException e) {
            // simulation stopped / reset
        }
    }

    private void washStage(int id) throws InterruptedException {
        final String tag = "Customer-" + two(id);
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        boolean washed = false;
        boolean counted = false;

        while (!washed) {
            log("waiting for a washer...", EventLogPanel.LogType.WASH);
            final long w0 = simNowMs();
            washerGate.acquire(id);        // ticket = customer number; a retry keeps the old place in line
            washWaitMs.addAndGet(simNowMs() - w0);
            int wi = -1;
            lock.lock();
            try {
                for (int i = 0; i < NUM_WASHERS; i++)
                    if (washerOwner[i] == null && !washerFailed[i]) { washerOwner[i] = tag; wi = i; break; }
                atomicMax(peakWashers, curWashers.incrementAndGet());
            } finally { lock.unlock(); }

            if (onRemoveLobby != null) onRemoveLobby.accept(id);
            final String wLabel = "W" + two(wi + 1);
            final int dur = WASH_MIN + rng.nextInt(WASH_MAX - WASH_MIN + 1);
            final int started = counted ? washCyclesStarted.get() : washCyclesStarted.incrementAndGet();
            counted = true;
            boolean scripted = false;
            boolean fault;
            if (scenario != Scenario.INCIDENTS) fault = rng.nextDouble() < FAIL_RATE;      // brief: 5 % chance
            else { scripted = scriptedWasherFault(started); fault = scripted; }
            final boolean ownFault = fault, scriptedF = scripted;

            if (onWasherUpdate != null) onWasherUpdate.accept(wi, MachineCard.State.BUSY, "C" + two(id));
            log(String.format("washing on %s (%.1f s)", wLabel, dur / 1000.0), EventLogPanel.LogType.WASH);
            lock.lock();
            try { washerEndAt[wi].set(System.currentTimeMillis() + (long) (dur / speed)); washerOwnFault[wi] = ownFault; }
            finally { lock.unlock(); }
            maybeTripWasherPair(started);
            final long b0 = simNowMs();

            // The washer breaks mid-cycle either by its own 5 % fault, or because the power trip
            // of the normal-run fault profile (A7) takes it down together with a second washer.
            boolean broke;
            if (ownFault) {
                pausableSleep((long) (dur * (0.25 + 0.5 * rng.nextDouble())));
                broke = true;
            } else {
                broke = sleepUntilFault(dur, washerFaultAt[wi]);
            }
            washBusyMs.addAndGet(simNowMs() - b0);
            if (!broke) {
                log("finished washing.", EventLogPanel.LogType.SUCCESS);
                releaseWasher(wi);
                washed = true;
                continue;
            }

            boolean inPair;
            lock.lock(); try { washerFailed[wi] = true; inPair = washerPair[wi]; } finally { lock.unlock(); }
            if (!inPair) washerFails.incrementAndGet();      // a double failure is counted as 2 when it happens
            curWashers.decrementAndGet();
            if (onWasherUpdate != null) onWasherUpdate.accept(wi, MachineCard.State.OUT_OF_SERVICE, null);
            log(wLabel + " FAILED mid-cycle while washing me!", EventLogPanel.LogType.ALARM);
            log("I reported " + wLabel + " to the owner.", EventLogPanel.LogType.OWNER);

            if (inPair) {
                // Double failure: the owner repairs BOTH washers in one visit (see callOwnerForWashers).
                if (pairFailed.incrementAndGet() == 2) callOwnerForWashers();
            } else {
                final int wiF = wi;
                spawn("Repair-" + wLabel, () -> {
                    log("owner's repair crew started on " + wLabel + " (" + WASHER_FIX_DELAY / 1000 + " s).",
                        EventLogPanel.LogType.OWNER);
                    try { pausableSleep(WASHER_FIX_DELAY); } catch (InterruptedException e) { return; }
                    lock.lock(); try { washerFailed[wiF] = false; washerOwner[wiF] = null; } finally { lock.unlock(); }
                    if (onWasherUpdate != null) onWasherUpdate.accept(wiF, MachineCard.State.IDLE, null);
                    log(wLabel + " repaired - machine is OK again.", EventLogPanel.LogType.FIXED);
                    washerGate.release();
                    if (scriptedF) endIncident();
                });
            }

            if (onAddLobbyStuck != null) onAddLobbyStuck.accept(id);
            log(String.format("my wash was interrupted - waiting %.0f s, then I will retry.",
                WASHER_RETRY_WAIT / 1000.0), EventLogPanel.LogType.WARN);
            pausableSleep(WASHER_RETRY_WAIT);
        }
    }

    private void dryStage(int id) throws InterruptedException {
        final String tag = "Customer-" + two(id);
        final ThreadLocalRandom rng = ThreadLocalRandom.current();

        if (onAddDryQueue != null) onAddDryQueue.accept(id);
        log("waiting for a dryer...", EventLogPanel.LogType.DRY);
        final long w0 = simNowMs();
        dryerSem.acquire();
        dryWaitMs.addAndGet(simNowMs() - w0);
        int di = -1;
        lock.lock();
        try {
            for (int i = 0; i < NUM_DRYERS; i++)
                if (dryerOwner[i] == null) { dryerOwner[i] = tag; di = i; break; }
            atomicMax(peakDryers, curDryers.incrementAndGet());
        } finally { lock.unlock(); }

        if (onRemoveDryQueue != null) onRemoveDryQueue.accept(id);
        int dur = DRY_MIN + rng.nextInt(DRY_MAX - DRY_MIN + 1);
        if (onDryerUpdate != null) onDryerUpdate.accept(di, MachineCard.State.BUSY, "C" + two(id));
        log(String.format("drying on D%s (%.1f s)", two(di + 1), dur / 1000.0), EventLogPanel.LogType.DRY);
        pausableSleep(dur);
        dryBusyMs.addAndGet(dur);
        log("finished drying.", EventLogPanel.LogType.SUCCESS);
        // The payment waiting area holds 31 people. acquire() on a FAIR semaphore keeps first-come-first-served
        // order (tryAcquire() would barge ahead of waiting customers, so it is deliberately not used).
        if (payRoom.availablePermits() == 0)
            log("payment waiting area is full (" + PAY_ROOM + ") - staying at the dryer for now.", EventLogPanel.LogType.PAY);
        payRoom.acquire();
        releaseDryer(di);
    }

    private void payStage(int id) throws InterruptedException {
        final String tag = "Customer-" + two(id);
        final ThreadLocalRandom rng = ThreadLocalRandom.current();

        final long pw0 = simNowMs();
        boolean waitCounted = false;
        int q = payQueueSize.incrementAndGet();
        final long ticket = payJoins.incrementAndGet();     // place in line (FIFO); kept for every retry
        payGate.join(ticket);
        atomicMax(peakPayQueue, q);
        if (onAddPayQueue != null) onAddPayQueue.accept(id);
        log("joined the payment queue (" + q + " at the desk).", EventLogPanel.LogType.PAY);

        if (q >= OWNER_THRESHOLD && kiosksDown.get() && ownerCalled.compareAndSet(false, true)) callOwner(q);

        if (scenario == Scenario.INCIDENTS && ticket >= n2 && stage.get() == 2 && beginIncident(2))
            tripBothKiosks();
        maybeCallOwnerForKiosks();

        boolean paid = false;
        while (!paid) {
            awaitKiosksOnline(id);
            if (onPayQueueState != null) onPayQueueState.accept(id, "waiting");
            log("waiting for a kiosk...", EventLogPanel.LogType.PAY);

            payGate.acquire(ticket);
            int ki = -1;
            lock.lock();
            try {
                for (int i = 0; i < NUM_KIOSKS; i++)
                    if (kioskOwner[i] == null && !kioskDown[i]) { kioskOwner[i] = tag; ki = i; break; }
                if (ki >= 0) curKiosks.incrementAndGet();
            } finally { lock.unlock(); }
            if (ki < 0) { payGate.release(); payGate.join(ticket); pausableSleep(50); continue; }

            if (!waitCounted) { payWaitMs.addAndGet(simNowMs() - pw0); waitCounted = true; }
            final long k0 = simNowMs();
            final String pLabel = "P" + two(ki + 1);
            final int dur = PAY_MIN + rng.nextInt(PAY_MAX - PAY_MIN + 1);
            boolean scripted = false;
            boolean fault;
            if (scenario != Scenario.INCIDENTS) fault = !kiosksDown.get() && rng.nextDouble() < FAIL_RATE;   // brief: 5 % chance
            else { scripted = scriptedKioskFault(); fault = scripted; }
            final boolean ownFault = fault, scriptedF = scripted;

            if (onKioskUpdate != null) onKioskUpdate.accept(ki, MachineCard.State.BUSY, "C" + two(id));
            if (onPayQueueState != null) onPayQueueState.accept(id, "serving");
            log(String.format("paying at %s (%.1f s)", pLabel, dur / 1000.0), EventLogPanel.LogType.PAY);

            boolean broke;
            if (ownFault) { pausableSleep(dur / 2); broke = true; }
            else          { broke = sleepUntilFault(dur, kioskFaultAt[ki]); }
            payBusyMs.addAndGet(simNowMs() - k0);
            if (!broke) {
                log("payment done.", EventLogPanel.LogType.SUCCESS);
                releaseKiosk(ki);
                paid = true;
                if (onRemovePayQueue != null) onRemovePayQueue.accept(id);
                continue;
            }

            final boolean outage = kiosksDown.get();   // both kiosks down together (A7b)
            if (!outage) kioskFails.incrementAndGet(); // a double failure is counted as 2 when it happens
            lock.lock(); try { kioskDown[ki] = true; kioskOwner[ki] = null; } finally { lock.unlock(); }
            curKiosks.decrementAndGet();
            if (onKioskUpdate != null) onKioskUpdate.accept(ki, MachineCard.State.OUT_OF_SERVICE, null);
            if (onPayQueueState != null) onPayQueueState.accept(id, "stuck");
            log(pLabel + " FAILED during my payment! "
                + (outage ? "Waiting for the owner..." : "Retrying in 2 s..."), EventLogPanel.LogType.ALARM);

            if (outage) {
                payGate.release();     // the gate (awaitKiosksOnline) keeps everyone out until the owner is done
                payGate.join(ticket);  // ... and he keeps his place in line
            } else {
                final int kiF = ki;
                spawn("KioskReset-" + pLabel, () -> {
                    try { pausableSleep(KIOSK_FAIL_RETRY); } catch (InterruptedException e) { return; }
                    boolean wide = kiosksDown.get();   // a wider outage began meanwhile: owner restores it
                    if (!wide) { lock.lock(); try { kioskDown[kiF] = false; } finally { lock.unlock(); } }
                    if (onKioskUpdate != null && !wide) onKioskUpdate.accept(kiF, MachineCard.State.IDLE, null);
                    if (!wide) log(pLabel + " restarted - back online.", EventLogPanel.LogType.FIXED);
                    payGate.release();
                    if (scriptedF) endIncident();
                });
                pausableSleep(KIOSK_FAIL_RETRY);
                payGate.join(ticket);  // retry with the OLD ticket: first in line, not last
            }
        }
        payQueueSize.decrementAndGet();
        payRoom.release();
    }

    // ── incident script helpers ──────────────────────────────────────────────
    private boolean beginIncident(int s) {
        return stage.get() == s && incident.compareAndSet(false, true);
    }
    private void endIncident() {
        stage.incrementAndGet();
        incident.set(false);
    }
    /** Incident 1 (washer variant): the n1-th customer's wash fails. */
    private boolean scriptedWasherFault(int started) {
        return !firstIsKiosk && started >= n1 && stage.get() == 1 && beginIncident(1);
    }
    /** Incident 1 (kiosk variant) and incident 3: a single kiosk fails during a payment. */
    private boolean scriptedKioskFault() {
        if (kiosksDown.get()) return false;
        int pj = payJoins.get(), st = stage.get();
        if (st == 1 && firstIsKiosk && pj >= n1) return beginIncident(1);
        if (st == 4 && pj >= n4)                 return beginIncident(4);
        return false;
    }

    /**
     * Incident 3: once the n3-th customer has started washing, two washers that are in the middle
     * of a cycle fail at the same instant (counted as 2 failures; each washing customer thread
     * notices and logs its own failure), the owner is called, and after 5 s both machines are back.
     */
    private void maybeTripWasherPair(int started) {
        if (scenario != Scenario.INCIDENTS || washerPairDone.get() || stage.get() != 3 || started < n3 || incident.get()) return;
        final long now = System.currentTimeMillis();
        final long simNow = simNowMs();      // read BEFORE taking `lock`: locks are never nested
        lock.lock();
        try {
            int a = -1, b = -1;
            for (int j = 0; j < NUM_WASHERS; j++) {
                boolean ok = washerOwner[j] != null && !washerFailed[j] && !washerOwnFault[j]
                          && washerEndAt[j].get() - now > (long) (1500 / speed);      // still mid-cycle for 1.5 s+
                if (!ok) continue;
                if (a < 0) a = j; else if (b < 0) b = j;
            }
            if (a >= 0 && b >= 0 && beginIncident(3)) {
                washerPairDone.set(true);
                washerFails.addAndGet(2);               // both washers fail at the same instant
                pairFailedAt = simNow;
                washerPair[a] = washerPair[b] = true;
                pairWashers = new int[] { a, b };
                washerFaultAt[a].set(now);
                washerFaultAt[b].set(now);
            }
        } finally { lock.unlock(); }
    }

    /** Called by the second affected customer thread once both washers are down. */
    private void callOwnerForWashers() {
        final int[] p = pairWashers;
        final String la = "W" + two(p[0] + 1), lb = "W" + two(p[1] + 1);
        log("both " + la + " and " + lb + " are down - CALLING THE OWNER!", EventLogPanel.LogType.ALARM);
        spawn("Owner", () -> {
            try {
                log("Owner called - on the way (" + WASHER_OWNER_ARRIVE / 1000 + " s)...", EventLogPanel.LogType.OWNER);
                pausableSleep(WASHER_OWNER_ARRIVE);
                log("Owner arrived - repairing " + la + " and " + lb + "...", EventLogPanel.LogType.OWNER);
                // total outage is exactly ARRIVE + FIX (5 s) counted from the moment the washers broke
                long left = (WASHER_OWNER_ARRIVE + WASHER_OWNER_FIX) - (simNowMs() - pairFailedAt);
                pausableSleep(Math.max(0, Math.min(WASHER_OWNER_FIX, left)));
            } catch (InterruptedException e) { return; }
            lock.lock();
            try {
                for (int j : p) { washerFailed[j] = false; washerOwner[j] = null; washerPair[j] = false; }
            } finally { lock.unlock(); }
            log("OWNER FIXED " + la + " and " + lb + " - both washers are running again. Owner leaving.",
                EventLogPanel.LogType.FIXED);       // logged BEFORE the machines are handed back
            for (int j : p) {
                if (onWasherUpdate != null) onWasherUpdate.accept(j, MachineCard.State.IDLE, null);
                washerGate.release();
            }
            endIncident();
        });
    }

    /**
     * Incident 2: both kiosks fail together (counted as 2 failures). Payments in progress fail and
     * customers wait at the desk. The owner is NOT called yet: he is called when the 4th customer
     * joins the queue (3 wait, the 4th triggers the call), see maybeCallOwnerForKiosks().
     */
    private void tripBothKiosks() {
        final long now = System.currentTimeMillis();
        lock.lock();
        try {
            for (int i = 0; i < NUM_KIOSKS; i++) {
                kioskDown[i] = true;
                if (kioskOwner[i] != null) kioskFaultAt[i].set(now);   // payment in progress fails too
            }
        } finally { lock.unlock(); }
        kioskFails.addAndGet(NUM_KIOSKS);
        setKiosksDown(true);
        ownerCalled.set(true);
        kioskOwnerPending.set(true);
        log("P01 and P02 both FAILED at the same time - the payment system is DOWN!",
            EventLogPanel.LogType.ALARM);
        if (onKioskOutage != null)
            onKioskOutage.accept("Payment system failed - both kiosks are down, customers are waiting at the desk.");
    }

    /** Called by every customer that joins the payment queue: the 4th waiting customer calls the owner. */
    private void maybeCallOwnerForKiosks() {
        if (!kioskOwnerPending.get()) return;
        // safety net: if every customer has already joined, nobody else can arrive, so call now
        if (payQueueSize.get() < KIOSK_QUEUE_LIMIT && payJoins.get() < TOTAL_CUSTOMERS) return;
        if (!kioskOwnerPending.compareAndSet(true, false)) return;
        log(payQueueSize.get() + " customers are waiting at the payment desk - CALLING THE OWNER!",
            EventLogPanel.LogType.ALARM);
        spawn("Owner", () -> {
            try {
                log("Owner called - on the way (" + String.format("%.1f", KIOSK_OWNER_ARRIVE / 1000.0) + " s)...",
                    EventLogPanel.LogType.OWNER);
                pausableSleep(KIOSK_OWNER_ARRIVE);
                log("Owner arrived - repairing both kiosks...", EventLogPanel.LogType.OWNER);
                pausableSleep(KIOSK_OWNER_FIX);
            } catch (InterruptedException e) { return; }
            log("OWNER FIXED IT - both kiosks are back online, queue is moving. Owner leaving.",
                EventLogPanel.LogType.FIXED);       // logged BEFORE customers are let back in
            lock.lock(); try { Arrays.fill(kioskDown, false); } finally { lock.unlock(); }
            setKiosksDown(false);
            if (onKioskRestored != null) onKioskRestored.run();
            endIncident();
        });
    }

    private void awaitKiosksOnline(int id) throws InterruptedException {
        if (!kiosksDown.get()) return;
        if (onPayQueueState != null) onPayQueueState.accept(id, "stuck");
        log("STUCK - payment kiosks are DOWN, waiting for the owner...", EventLogPanel.LogType.WARN);
        gateLock.lock();
        try {
            while (kiosksDown.get() && running.get()) kiosksOnline.await();
        } finally { gateLock.unlock(); }
        if (!running.get()) throw new InterruptedException("stopped");
    }

    private void setKiosksDown(boolean down) {
        gateLock.lock();
        try {
            kiosksDown.set(down);
            if (!down) kiosksOnline.signalAll();
        } finally { gateLock.unlock(); }
    }

    private void callOwner(int queued) {
        log(queued + " customers are stuck at the payment queue - CALLING THE OWNER!", EventLogPanel.LogType.ALARM);
        spawn("Owner", () -> {
            try {
                log("Owner called - on the way (" + OWNER_ARRIVAL_DELAY / 1000 + " s)...", EventLogPanel.LogType.OWNER);
                pausableSleep(OWNER_ARRIVAL_DELAY);
                log("Owner arrived - repairing both kiosks...", EventLogPanel.LogType.OWNER);
                pausableSleep(OWNER_FIX_TIME);
            } catch (InterruptedException e) { return; }
            lock.lock(); try { Arrays.fill(kioskDown, false); } finally { lock.unlock(); }
            setKiosksDown(false);
            log("OWNER FIXED IT - both kiosks are back online, queue is moving.", EventLogPanel.LogType.FIXED);
            if (onKioskRestored != null) onKioskRestored.run();
        });
    }

    private void releaseWasher(int i) {
        lock.lock(); try { washerOwner[i] = null; washerFailed[i] = false; } finally { lock.unlock(); }
        curWashers.decrementAndGet();
        if (onWasherUpdate != null) onWasherUpdate.accept(i, MachineCard.State.IDLE, null);
        washerGate.release();
    }
    private void releaseDryer(int i) {
        lock.lock(); try { dryerOwner[i] = null; } finally { lock.unlock(); }
        curDryers.decrementAndGet();
        if (onDryerUpdate != null) onDryerUpdate.accept(i, MachineCard.State.IDLE, null);
        dryerSem.release();
    }
    private void releaseKiosk(int i) {
        lock.lock(); try { kioskOwner[i] = null; } finally { lock.unlock(); }
        curKiosks.decrementAndGet();
        if (onKioskUpdate != null)
            onKioskUpdate.accept(i, kiosksDown.get() ? MachineCard.State.OUT_OF_SERVICE : MachineCard.State.IDLE, null);
        payGate.release();
    }

    private void atomicMax(AtomicInteger target, int candidate) {
        int cur;
        do { cur = target.get(); } while (cur < candidate && !target.compareAndSet(cur, candidate));
    }

    /** Every line is prefixed with the NAME OF THE THREAD that produced it. */
    void log(String msg, EventLogPanel.LogType type) {
        String line = "[" + Thread.currentThread().getName() + "] " + msg;
        String ts = simTs();                 // clock read outside logLock: no lock is held while taking another
        synchronized (logLock) {
            System.out.println(ts + " " + line);
            if (onLog != null) onLog.accept(line, type);
        }
    }

    public String simTs() {
        long e = simNowMs();
        return String.format("[%02d:%02d.%d]", e / 60000, (e / 1000) % 60, (e / 100) % 10);
    }
}
