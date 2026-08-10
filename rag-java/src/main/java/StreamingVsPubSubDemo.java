import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Tiny demo: classic pub/sub vs streaming.
 *
 * <p>Run:
 * <pre>{@code
 * javac StreamingVsPubSubDemo.java
 * java StreamingVsPubSubDemo
 * }</pre>
 */
public class StreamingVsPubSubDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1) PUB / SUB ==========");
        runPubSub();

        System.out.println();
        System.out.println("========== 2) STREAMING ==========");
        runStreaming();

        System.out.println();
        System.out.println("========== What differs? ==========");
        System.out.println("""
                Pub/Sub
                  - Discrete, complete messages ("Job scored", "Email received").
                  - Publisher broadcasts; usually does not care how fast subscribers work.
                  - Each event stands alone; order/progress across events is optional.
                  - Typical use: fan-out notifications to many listeners.

                Streaming
                  - One logical result delivered as an ordered sequence of chunks over time.
                  - Consumer often processes incrementally (tokens, bytes, rows) as they arrive.
                  - Backpressure matters: subscriber can request N items (Flow.Subscription.request).
                  - Typical use: LLM token output, file/HTTP download, Kafka consumer loops.

                Same wiring (producer -> consumer) can look similar; the contract is different:
                pub/sub = "notify me of events", streaming = "push this ongoing flow until done".
                """);
    }

    // -------------------------------------------------------------------------
    // Pub/Sub: many subscribers get the same complete events; no backpressure.
    // -------------------------------------------------------------------------
    static void runPubSub() throws InterruptedException {
        SimpleEventBus bus = new SimpleEventBus();

        bus.subscribe(event -> System.out.println("  [email-listener] got: " + event));
        bus.subscribe(event -> System.out.println("  [metrics-listener] got: " + event));

        bus.publish("job.scored:rank=82");
        bus.publish("job.scored:rank=41");
        bus.publish("scan.finished");

        // Give async delivery a moment (this bus is sync; sleep keeps output readable).
        Thread.sleep(50);
        System.out.println("  -> Each subscriber received whole events independently (fan-out).");
    }

    /** Minimal in-process pub/sub (no Flow, no backpressure). */
    static final class SimpleEventBus {
        private final List<Consumer<String>> subscribers = new CopyOnWriteArrayList<>();

        void subscribe(Consumer<String> subscriber) {
            subscribers.add(subscriber);
        }

        void publish(String event) {
            for (Consumer<String> subscriber : subscribers) {
                subscriber.accept(event);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Streaming: one response broken into chunks; subscriber pulls via request(n).
    // -------------------------------------------------------------------------
    static void runStreaming() throws Exception {
        // One logical LLM answer, published as tokens over time.
        List<String> tokens = List.of("Java", " ", "streams", " ", "deliver", " ", "chunks", " ", "over", " ", "time", ".");

        try (ExecutorService executor = Executors.newSingleThreadExecutor();
                SubmissionPublisher<String> publisher = new SubmissionPublisher<>(executor, 1)) {

            CountDownLatch done = new CountDownLatch(1);
            StringBuilder assembled = new StringBuilder();

            publisher.subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;
                private final AtomicInteger outstanding = new AtomicInteger();

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    // Backpressure: ask for only 1 chunk at a time (slow consumer).
                    outstanding.set(1);
                    subscription.request(1);
                }

                @Override
                public void onNext(String token) {
                    assembled.append(token);
                    System.out.println("  [stream-consumer] chunk=\"" + token + "\"  so-far=\"" + assembled + "\"");
                    // Simulate slow processing, then ask for the next chunk.
                    try {
                        Thread.sleep(80);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    if (outstanding.decrementAndGet() == 0) {
                        outstanding.set(1);
                        subscription.request(1);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    System.out.println("  [stream-consumer] complete. full text=\"" + assembled + "\"");
                    done.countDown();
                }
            });

            for (String token : tokens) {
                publisher.submit(token); // may block briefly when buffer is full (backpressure)
            }
            publisher.close(); // signals onComplete after buffered items drain

            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("streaming demo timed out");
            }
            System.out.println("  -> One logical message arrived as an ordered chunk stream with backpressure.");
        }
    }
}
