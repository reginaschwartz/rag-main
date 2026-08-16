package com.example.pipeline;

import com.example.pipeline.sink.BigQueryStreamingSink;
import com.example.pipeline.sink.RowSink;
import com.example.pipeline.sink.RowSink.SinkRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * Inserts a few synthetic rows straight into BigQuery (no Kafka).
 *
 * <pre>{@code
 * # from java-notes/kafka-to-bigquery with .env loaded
 * mvn -q -DskipTests package
 * set -a && source .env && set +a
 * java -cp target/kafka-to-bigquery-1.0.0.jar com.example.pipeline.BigQuerySmokeInsertApp
 * }</pre>
 */
public final class BigQuerySmokeInsertApp {

    public static void main(String[] args) throws Exception {
        PipelineConfig config = new PipelineConfig();
        if (!"bigquery".equalsIgnoreCase(config.sinkMode)) {
            System.out.println("Tip: set SINK_MODE=bigquery in .env (still required for BQ_* settings).");
        }
        System.out.printf(
                "Smoke-inserting into %s.%s.%s%n",
                config.bqProject, config.bqDataset, config.bqTable);

        try (RowSink sink = new BigQueryStreamingSink(config.bqProject, config.bqDataset, config.bqTable)) {
            List<SinkRecord> batch = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                Event event = Event.random(i);
                batch.add(new SinkRecord(event, 0, i));
                System.out.printf("  prepared %s user=%s id=%s%n", event.eventType(), event.userId(), event.eventId());
            }
            sink.writeBatch(batch);
        }

        System.out.println(
                """
                Done. Verify with:
                  bq query --nouse_legacy_sql \\
                    'SELECT event_id, event_type, user_id, event_ts FROM `%s.%s.%s` ORDER BY ingested_at DESC LIMIT 10'
                """.formatted(config.bqProject, config.bqDataset, config.bqTable));
    }
}
