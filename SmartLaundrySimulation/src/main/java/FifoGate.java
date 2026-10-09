import java.util.TreeSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Counting semaphore that serves waiting customers strictly in ticket order (lowest ticket first).
 *
 * A customer keeps the SAME ticket for the whole stage. If a machine fails and the customer has to
 * retry, he re-joins the line with his old ticket, so he is served before everybody who arrived
 * after him (true FIFO) instead of going to the back of the queue.
 *
 * Built only from a ReentrantLock + Condition, so every state change is mutually exclusive.
 */
public class FifoGate {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final TreeSet<Long> line = new TreeSet<>();   // tickets of customers waiting in line
    private int permits;

    public FifoGate(int permits) { this.permits = permits; }

    /** Take (or keep) a place in the line without waiting for a permit yet. */
    public void join(long ticket) {
        lock.lock();
        try { line.add(ticket); changed.signalAll(); } finally { lock.unlock(); }
    }

    /** Waits until a permit is free AND this ticket is the first one in line. */
    public void acquire(long ticket) throws InterruptedException {
        lock.lock();
        try {
            line.add(ticket);
            try {
                while (permits <= 0 || line.first().longValue() != ticket) changed.await();
            } catch (InterruptedException e) {
                line.remove(ticket);
                changed.signalAll();
                throw e;
            }
            permits--;
            line.remove(ticket);
            changed.signalAll();      // the next customer in line may be able to go too
        } finally { lock.unlock(); }
    }

    public void release() {
        lock.lock();
        try { permits++; changed.signalAll(); } finally { lock.unlock(); }
    }

    /** Back to a clean state (used by Reset). */
    public void reset(int permits) {
        lock.lock();
        try { this.permits = permits; line.clear(); changed.signalAll(); } finally { lock.unlock(); }
    }
}
