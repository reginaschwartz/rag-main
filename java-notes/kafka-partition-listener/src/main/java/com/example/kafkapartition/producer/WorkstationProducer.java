package com.example.kafkapartition.producer;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Runs on the client workstation. This JVM does not create or own the topic;
 * it only publishes to a topic that already exists on the Dockerized broker.
 *
 * <p>Each record is sent to a randomly chosen partition via the {@code ProducerRecord}
 * partition field (not by hashing a key).
 */
public final class WorkstationProducer {

    public static void main(String[] args) throws Exception {
        String bootstrap = env("KAFKA_BOOTSTRAP", "localhost:19092");
        String topic = env("KAFKA_TOPIC", "workstation.events");
        int events = Integer.parseInt(env("PRODUCER_EVENTS", "12"));
        long intervalMs = Long.parseLong(env("PRODUCER_INTERVAL_MS", "250"));

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "workstation-producer");
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 15_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 20_000);

        System.out.printf("Workstation producer → %s topic=%s events=%d%n", bootstrap, topic, events);

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            List<PartitionInfo> partitions = requireExistingTopic(producer, topic, bootstrap);
            System.out.printf(
                    "Broker already has topic %s with %d partitions. This JVM will not create topics.%n",
                    topic, partitions.size());

            for (int i = 1; i <= events; i++) {
                int partition = ThreadLocalRandom.current().nextInt(partitions.size());
                String payload = "workstation-msg-" + i;
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(topic, partition, "workstation", payload);
                RecordMetadata meta = send(record, producer);
                System.out.printf(
                        "sent %-20s -> partition %d offset %d%n",
                        payload, meta.partition(), meta.offset());
                Thread.sleep(intervalMs);
            }
            producer.flush();
        }
        System.out.println("Producer finished.");
    }

    /**
     * Reads partition metadata only. If the topic is missing, fail instead of creating it.
     * Topic auto-create is disabled on the broker for this demo.
     */
    private static List<PartitionInfo> requireExistingTopic(
            Producer<String, String> producer, String topic, String bootstrap) {
        try {
            List<PartitionInfo> partitions = producer.partitionsFor(topic);
            if (partitions == null || partitions.isEmpty()) {
                throw missingTopic(topic, bootstrap, "broker returned no partitions");
            }
            return partitions;
        } catch (RuntimeException exception) {
            throw missingTopic(topic, bootstrap, exception.getMessage());
        }
    }

    private static IllegalStateException missingTopic(String topic, String bootstrap, String detail) {
        return new IllegalStateException(
                """
                Topic '%s' is not available at %s (%s).
                The workstation producer does not create topics. Start the Docker stack first:
                  cd java-notes/kafka-partition-listener && docker compose up --build
                Host bootstrap must be localhost:19092 (not kafka:29092).
                """
                        .formatted(topic, bootstrap, detail));
    }

    private static RecordMetadata send(ProducerRecord<String, String> record, Producer<String, String> producer)
            throws InterruptedException {
        try {
            return producer.send(record).get(20, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            throw new IllegalStateException(
                    "Kafka send timed out for topic=" + record.topic() + " partition=" + record.partition(),
                    timeout);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "Kafka send failed for topic=" + record.topic() + ": " + exception.getCause(),
                    exception.getCause());
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
