package com.example.pipeline;

import com.example.pipeline.sink.BigQueryStreamingSink;
import com.example.pipeline.sink.FileRowSink;
import com.example.pipeline.sink.RowSink;
import com.example.pipeline.sink.RowSink.SinkRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Consumes Kafka events, batches them, and flushes to a row sink (file or BigQuery).
 * Commits offsets only after a successful flush (at-least-once).
 */
public final class BigQuerySinkApp {

    public static void main(String[] args) throws Exception {
        PipelineConfig config = new PipelineConfig();
        int maxIdleAfterData =
                Integer.parseInt(System.getenv().getOrDefault("SINK_IDLE_STOP_POLLS", "8"));

        try (RowSink sink = createSink(config);
                Consumer<String, String> consumer = createConsumer(config)) {

            consumer.subscribe(List.of(config.topic));
            System.out.printf(
                    "Sink listening on %s topic=%s mode=%s batchSize=%d lingerMs=%d%n",
                    config.bootstrapServers,
                    config.topic,
                    config.sinkMode,
                    config.batchSize,
                    config.lingerMs);

            List<SinkRecord> batch = new ArrayList<>();
            Instant batchStarted = Instant.now();
            int idlePolls = 0;
            boolean sawData = false;

            // Before data: wait longer for producer. After data: stop when idle.
            while ((!sawData && idlePolls < 60) || (sawData && idlePolls < maxIdleAfterData)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) {
                    if (!batch.isEmpty() && elapsedMs(batchStarted) >= config.lingerMs) {
                        flush(sink, consumer, batch);
                        batchStarted = Instant.now();
                    }
                    idlePolls++;
                    continue;
                }

                sawData = true;
                idlePolls = 0;
                if (batch.isEmpty()) {
                    batchStarted = Instant.now();
                }

                for (ConsumerRecord<String, String> record : records) {
                    Event event = Event.fromJson(record.value());
                    batch.add(new SinkRecord(event, record.partition(), record.offset()));
                    System.out.printf(
                            "buffered %-12s user=%-8s p%d@%d (batch=%d)%n",
                            event.eventType(),
                            event.userId(),
                            record.partition(),
                            record.offset(),
                            batch.size());

                    if (batch.size() >= config.batchSize) {
                        flush(sink, consumer, batch);
                        batchStarted = Instant.now();
                    }
                }

                if (!batch.isEmpty() && elapsedMs(batchStarted) >= config.lingerMs) {
                    flush(sink, consumer, batch);
                    batchStarted = Instant.now();
                }
            }

            if (!batch.isEmpty()) {
                flush(sink, consumer, batch);
            }
            System.out.println("Sink finished (idle timeout).");
        }
    }

    private static void flush(RowSink sink, Consumer<String, String> consumer, List<SinkRecord> batch)
            throws Exception {
        sink.writeBatch(List.copyOf(batch));
        consumer.commitSync();
        batch.clear();
    }

    private static long elapsedMs(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }

    private static RowSink createSink(PipelineConfig config) throws Exception {
        return switch (config.sinkMode) {
            case "bigquery" -> new BigQueryStreamingSink(config.bqProject, config.bqDataset, config.bqTable);
            case "file" -> new FileRowSink(config.fileSinkPath);
            default -> throw new IllegalArgumentException(
                    "Unknown SINK_MODE=" + config.sinkMode + " (use file|bigquery)");
        };
    }

    private static Consumer<String, String> createConsumer(PipelineConfig config) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.batchSize);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "bq-sink");
        return new KafkaConsumer<>(props);
    }
}
