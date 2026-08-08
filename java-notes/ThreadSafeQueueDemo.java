import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Four ways to hand work between threads: no safety at all, a hand-rolled lock-and-condition queue,
 * the JDK's blocking queue, and a lock-free non-blocking queue.
 */
class ThreadSafeQueueDemo {

    private static final int PRODUCERS = 4;
    private static final int CONSUMERS = 3;
    private static final int ITEMS_PER_PRODUCER = 25_000;
    private static final int TOTAL = PRODUCERS * ITEMS_PER_PRODUCER;
    private static final int CAPACITY = 64;
    private static final Integer POISON = Integer.MIN_VALUE;

    /** The two operations every hand-off needs, so one harness can drive every implementation. */
    interface Handoff {
        void put(Integer item) throws InterruptedException;

        Integer take() throws InterruptedException;
    }

    /**
     * A bounded queue built on an explicit lock. The gain over synchronized/wait/notifyAll is two
     * separate wait sets: a producer freeing space can wake a consumer specifically, instead of
     * waking every waiter and hoping the right one wins.
     */
    static final class LockedQueue implements Handoff {
        private final Queue<Integer> items = new ArrayDeque<>();
        private final int capacity;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        LockedQueue(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public void put(Integer item) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (items.size() == capacity) {
                    notFull.await();        // while, not if: waking up is not proof the state still holds
                }
                items.add(item);
                notEmpty.signal();
            } finally {
                lock.unlock();              // in a finally block, or an exception strands every other thread
            }
        }

        @Override
        public Integer take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (items.isEmpty()) {
                    notEmpty.await();
                }
                Integer item = items.remove();
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }
    }

    /** The JDK's bounded queue. Same semantics as above, already written and tested. */
    record BlockingHandoff(BlockingQueue<Integer> queue) implements Handoff {
        @Override
        public void put(Integer item) throws InterruptedException {
            queue.put(item);                // blocks while full - this is the backpressure
        }

        @Override
        public Integer take() throws InterruptedException {
            return queue.take();            // blocks while empty
        }
    }

    /**
     * Lock-free and unbounded: offer/poll never block, so poll returns null on empty and the caller
     * has to decide what to do about it. No capacity means no backpressure.
     */
    record NonBlockingHandoff(ConcurrentLinkedQueue<Integer> queue) implements Handoff {
        @Override
        public void put(Integer item) {
            queue.offer(item);
        }

        @Override
        public Integer take() {
            Integer item;
            while ((item = queue.poll()) == null) {
                Thread.onSpinWait();        // burning CPU, which is what blocking would have avoided
            }
            return item;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        unsafeQueue();
        run("ReentrantLock + Condition", new LockedQueue(CAPACITY));
        run("ArrayBlockingQueue       ", new BlockingHandoff(new ArrayBlockingQueue<>(CAPACITY)));
        run("ConcurrentLinkedQueue    ", new NonBlockingHandoff(new ConcurrentLinkedQueue<>()));
    }

    /** No lock at all: concurrent writes interleave mid-resize and updates are simply lost. */
    private static void unsafeQueue() throws InterruptedException {
        Queue<Integer> queue = new ArrayDeque<>();
        AtomicLong failures = new AtomicLong();
        List<Thread> threads = new ArrayList<>();

        for (int p = 0; p < PRODUCERS; p++) {
            threads.add(Thread.ofPlatform().start(() -> {
                for (int i = 0; i < ITEMS_PER_PRODUCER; i++) {
                    try {
                        queue.add(i);
                    } catch (RuntimeException corrupted) {
                        failures.incrementAndGet();
                    }
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.printf("unsynchronized ArrayDeque : expected %d, holds %d  (%d lost, %d exceptions)%n",
                TOTAL, queue.size(), TOTAL - queue.size(), failures.get());
    }

    /** Producers feed the queue, consumers drain it, and a poison pill each tells consumers to stop. */
    private static void run(String label, Handoff handoff) throws InterruptedException {
        AtomicLong consumed = new AtomicLong();
        List<Thread> threads = new ArrayList<>();
        long start = System.nanoTime();

        for (int p = 0; p < PRODUCERS; p++) {
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    for (int i = 1; i <= ITEMS_PER_PRODUCER; i++) {
                        handoff.put(i);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        for (int c = 0; c < CONSUMERS; c++) {
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    while (!POISON.equals(handoff.take())) {
                        consumed.incrementAndGet();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        for (int p = 0; p < PRODUCERS; p++) {
            threads.get(p).join();          // every item is queued before any pill goes in
        }
        for (int c = 0; c < CONSUMERS; c++) {
            handoff.put(POISON);            // one pill per consumer; a consumer that takes one stops
        }
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.printf("%s : expected %d, consumed %d  (%d ms)%n",
                label, TOTAL, consumed.get(), (System.nanoTime() - start) / 1_000_000);
    }
}
