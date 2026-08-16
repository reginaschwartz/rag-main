package com.example.pipeline;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Publishes sample analytics events to Kafka. Partition key = {@code userId} so one user's
 * stream stays ordered.
 *
 * <p><b>Debugger tip:</b> set breakpoints to <em>Suspend: Thread</em> (not All). Suspend-All freezes
 * Kafka's network/sender threads, so {@code send().get(...)} blocks forever.
 */
public final class EventProducerApp {

    public static void main(String[] args) throws Exception {
        PipelineConfig config = new PipelineConfig();
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "event-producer");
        // Fail fast when the broker/advertised listener is wrong (common with Docker + host debug).
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 15_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 20_000);

        System.out.printf(
                "Producer → %s topic=%s events=%d intervalMs=%d%n",
                config.bootstrapServers, config.topic, config.producerEvents, config.producerIntervalMs);

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            waitForTopic(producer, config);
            for (int i = 1; i <= config.producerEvents; i++) {
                Event event = Event.random(i);
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(config.topic, event.userId(), event.toJson());
                RecordMetadata meta = sendWithTimeout(producer, record, 20);
                System.out.printf(
                        "sent %-12s user=%-8s -> p%d@%d%n",
                        event.eventType(), event.userId(), meta.partition(), meta.offset());
                Thread.sleep(config.producerIntervalMs);
            }
            producer.flush();
        }
        System.out.println("Producer finished.");
    }

    private static RecordMetadata sendWithTimeout(
            Producer<String, String> producer, ProducerRecord<String, String> record, long seconds)
            throws InterruptedException {
        try {
            return producer.send(record).get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            throw new IllegalStateException(
                    """
                    Kafka send timed out after %d seconds (topic=%s).
                    Checks:
                      1) docker compose --env-file .env up -d kafka
                      2) KAFKA_BOOTSTRAP=localhost:19092 on the host (not kafka:29092)
                      3) Breakpoints: Suspend=Thread (NOT All) — All freezes Kafka I/O threads
                    """.formatted(seconds, record.topic()),
                    timeout);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "Kafka send failed for topic=" + record.topic() + ": " + exception.getCause(),
                    exception.getCause());
        }
    }

    /** Retry until the broker accepts metadata (topic auto-create or already exists). */
    private static void waitForTopic(Producer<String, String> producer, PipelineConfig config)
            throws InterruptedException {
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                producer.partitionsFor(config.topic);
                System.out.println("Kafka topic ready: " + config.topic);
                return;
            } catch (Exception exception) {
                System.out.println(
                        "Waiting for Kafka/topic... attempt " + attempt + "/30 (" + exception.getMessage() + ")");
                Thread.sleep(1000);
            }
        }
        throw new IllegalStateException("Kafka not ready at " + config.bootstrapServers);
    }
}
