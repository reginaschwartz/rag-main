import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Classic threading: shared mutable state, synchronized, join, and the wait/notify handshake. */
class ThreadingDemo {

    /** The same workload counted twice: once unguarded, once under a lock. */
    static class Counter {
        private int racy;
        private int guarded;

        void increment() {
            racy++;                 // read, add, write - three steps, so updates get lost
            synchronized (this) {
                guarded++;          // only one thread at a time
            }
        }
    }

    /** A bounded buffer: producers wait when it is full, consumers wait when it is empty. */
    static class Buffer {
        private final Deque<Integer> items = new ArrayDeque<>();
        private final int capacity;

        Buffer(int capacity) {
            this.capacity = capacity;
        }

        synchronized void put(int item) throws InterruptedException {
            while (items.size() == capacity) {
                wait();             // releases the lock, unlike sleep
            }
            items.addLast(item);
            notifyAll();            // a consumer may be waiting for data
        }

        synchronized int take() throws InterruptedException {
            while (items.isEmpty()) {
                wait();
            }
            int item = items.removeFirst();
            notifyAll();            // a producer may be waiting for space
            return item;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        countInThreads();
        System.out.println();
        producerConsumer();
    }

    private static void countInThreads() throws InterruptedException {
        int threadCount = 4;
        int incrementsPerThread = 50_000;
        Counter counter = new Counter();
        List<Thread> workers = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Thread worker = new Thread(() -> {
                for (int n = 0; n < incrementsPerThread; n++) {
                    counter.increment();
                }
            }, "counter-" + i);
            workers.add(worker);
            worker.start();         // start() runs it on a new thread; run() would not
        }
        for (Thread worker : workers) {
            worker.join();          // block until finished, so the results below are safe to read
        }

        System.out.printf("expected %d | racy %d | synchronized %d%n",
                threadCount * incrementsPerThread, counter.racy, counter.guarded);
    }

    private static void producerConsumer() throws InterruptedException {
        Buffer buffer = new Buffer(3);
        int itemCount = 8;

        Thread producer = new Thread(() -> {
            try {
                for (int item = 1; item <= itemCount; item++) {
                    buffer.put(item);
                    System.out.println("produced " + item);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                int sum = 0;
                for (int i = 0; i < itemCount; i++) {
                    Thread.sleep(20);           // slow consumer, so the producer hits the capacity limit
                    int item = buffer.take();
                    sum += item;
                    System.out.println("        consumed " + item);
                }
                System.out.println("        sum = " + sum);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}
