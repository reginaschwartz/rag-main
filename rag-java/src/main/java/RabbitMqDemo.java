import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RabbitMQ demo contrasting:
 * <ol>
 *   <li><b>Pub/Sub</b> — fanout exchange; every subscriber gets each complete event.</li>
 *   <li><b>Streaming</b> — one logical payload split into ordered chunks on a single queue
 *       (one consumer rebuilds the message; competing consumers would split work, not fan-out).</li>
 * </ol>
 *
 * <pre>{@code
 * docker compose up -d
 * mvn -q compile exec:java
 * }</pre>
 */
public class RabbitMqDemo {

    private static final String HOST = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        factory.setAutomaticRecoveryEnabled(true);

        try (Connection connection = waitForBroker(factory)) {
            System.out.println("Connected to RabbitMQ at " + HOST + ":" + PORT);
            System.out.println();

            System.out.println("========== 1) PUB / SUB (fanout exchange) ==========");
            runPubSub(connection);

            System.out.println();
            System.out.println("========== 2) STREAMING (chunked messages on a queue) ==========");
            runStreaming(connection);

            System.out.println();
            System.out.println("========== What differs on RabbitMQ? ==========");
            System.out.println("""
                    Pub/Sub (fanout)
                      - Exchange copies each published event to every bound queue.
                      - Each consumer gets the full event; classic notification fan-out.
                      - UI: http://localhost:15672  (guest / guest)

                    Streaming (chunked queue)
                      - One logical result is many messages with the same stream-id + sequence.
                      - Usually one consumer (or careful partitioning) rebuilds ordered chunks.
                      - Competing consumers on the same queue share/split messages — not fan-out.
                      - Use basicQos(1) so a slow consumer is not flooded (simple backpressure).
                    """);
        }
    }

    /** Fanout: one publish → every subscriber queue receives a copy. */
    static void runPubSub(Connection connection) throws Exception {
        String exchange = "demo.events";
        CountDownLatch latch = new CountDownLatch(6); // 2 subscribers × 3 events

        try (Channel publisher = connection.createChannel();
                Channel emailListener = connection.createChannel();
                Channel metricsListener = connection.createChannel()) {

            publisher.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, false, true, null);

            String emailQueue = emailListener.queueDeclare().getQueue();
            String metricsQueue = metricsListener.queueDeclare().getQueue();
            emailListener.queueBind(emailQueue, exchange, "");
            metricsListener.queueBind(metricsQueue, exchange, "");

            DeliverCallback emailCb = (tag, delivery) -> {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("  [email-listener] got: " + body);
                latch.countDown();
            };
            DeliverCallback metricsCb = (tag, delivery) -> {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("  [metrics-listener] got: " + body);
                latch.countDown();
            };

            emailListener.basicConsume(emailQueue, true, emailCb, tag -> {});
            metricsListener.basicConsume(metricsQueue, true, metricsCb, tag -> {});

            // Give consumers a moment to attach before publishing.
            Thread.sleep(20000);

            for (String event : List.of("job.scored:rank=82", "job.scored:rank=41", "job.scored:rank=32" , "job.scored:rank=22" ,  "scan.finished")) {
                publisher.basicPublish(exchange, "", null, event.getBytes(StandardCharsets.UTF_8));
                System.out.println("  [publisher] published: " + event);
            }

            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pub/sub demo timed out; received only part of the fan-out");
            }
            System.out.println("  -> Both listeners received every complete event (fan-out).");
        }
    }

    /**
     * Streaming: publish token chunks for one logical answer; a single consumer assembles them.
     * Headers carry stream id + sequence so the consumer can detect completion.
     */
    static void runStreaming(Connection connection) throws Exception {
        String queue = "demo.llm.stream";
        List<String> tokens = List.of(
                "Java", " ", "streams", " ", "on", " ", "RabbitMQ", " ", "arrive", " ", "chunk", " ", "by", " ", "chunk", ".","Java2", " ", "streams", " ", "on", " ", "RabbitMQ", " ", "arrive", " ", "chunk", " ", "by", " ", "chunk", ".","Java1", " ", "streams", " ", "on", " ", "RabbitMQ", " ", "arrive", " ", "chunk", " ", "by", " ", "chunk", ".","Java4", " ", "streams", " ", "on", " ", "RabbitMQ", " ", "arrive", " ", "chunk", " ", "by", " ", "chunk", ".");
        String streamId = UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch done = new CountDownLatch(1);

        try (Channel publisher = connection.createChannel();
                Channel consumer = connection.createChannel()) {

            publisher.queueDeclare(queue, false, false, true, null);
            // Purge leftover messages from a previous run on the exclusive-less durable=false queue name.
            publisher.queuePurge(queue);

            consumer.basicQos(1); // backpressure: unacked limit = 1 in-flight chunk

            StringBuilder assembled = new StringBuilder();
            AtomicInteger nextExpected = new AtomicInteger(0);

            DeliverCallback callback = (tag, delivery) -> {
                try {
                    AMQP.BasicProperties props = delivery.getProperties();
                    int seq = Integer.parseInt(props.getHeaders().get("seq").toString());
                    int total = Integer.parseInt(props.getHeaders().get("total").toString());
                    String id = props.getHeaders().get("stream-id").toString();
                    String chunk = new String(delivery.getBody(), StandardCharsets.UTF_8);

                    if (!streamId.equals(id)) {
                        // Ignore leftovers from another run.
                        consumer.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        return;
                    }
                    if (seq != nextExpected.get()) {
                        throw new IllegalStateException(
                                "out-of-order chunk: expected seq=" + nextExpected.get() + " got " + seq);
                    }

                    assembled.append(chunk);
                    System.out.println(
                            "  [stream-consumer] seq=" + seq + "/" + (total - 1)
                                    + " chunk=\"" + chunk + "\" so-far=\"" + assembled + "\"");

                    // Simulate slow processing (like rendering LLM tokens).
                    Thread.sleep(8000);

                    consumer.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    nextExpected.incrementAndGet();
                    if (seq == total - 1) {
                        System.out.println("  [stream-consumer] complete. full text=\"" + assembled + "\"");
                        done.countDown();
                    }
                } catch (Exception exception) {
                    consumer.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                    exception.printStackTrace();
                    done.countDown();
                }
            };

            consumer.basicConsume(queue, false, callback, tag -> {});

            int total = tokens.size();
            for (int i = 0; i < total; i++) {
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .headers(java.util.Map.of(
                                "stream-id", streamId,
                                "seq", i,
                                "total", total))
                        .contentType("text/plain")
                        .build();
                publisher.basicPublish("", queue, props, tokens.get(i).getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("  [publisher] submitted " + total + " chunks for stream-id=" + streamId);

            if (!done.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("streaming demo timed out");
            }
            System.out.println("  -> One logical message arrived as an ordered chunk stream with QoS backpressure.");
        }
    }

    private static Connection waitForBroker(ConnectionFactory factory) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                return factory.newConnection("rabbitmq-demo");
            } catch (Exception exception) {
                last = exception;
                System.out.println("Waiting for RabbitMQ... attempt " + attempt + "/30");
                Thread.sleep(100000);
            }
        }
        throw new IllegalStateException(
                "Could not connect to RabbitMQ at " + HOST + ":" + PORT
                        + ". Start it with: docker compose up -d",
                last);
    }
}
