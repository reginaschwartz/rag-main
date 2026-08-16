package com.example.pipeline.sink;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lands batches with BigQuery streaming inserts ({@code tabledata.insertAll}).
 *
 * <p>Auth (in order):
 * <ol>
 *   <li>{@code GOOGLE_APPLICATION_CREDENTIALS} JSON key file (if present)</li>
 *   <li>{@code secrets/gcp.json} (if present)</li>
 *   <li>Application Default Credentials — e.g. {@code gcloud auth application-default login}</li>
 * </ol>
 */
public final class BigQueryStreamingSink implements RowSink {

    private final BigQuery bigQuery;
    private final TableId tableId;

    public BigQueryStreamingSink(String project, String dataset, String table) {
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException(
                    "BQ_PROJECT is required when SINK_MODE=bigquery (set it in .env)");
        }
        if (dataset == null || dataset.isBlank() || table == null || table.isBlank()) {
            throw new IllegalArgumentException("BQ_DATASET and BQ_TABLE are required when SINK_MODE=bigquery");
        }

        CredentialsAndSource auth = loadCredentials();
        this.bigQuery = BigQueryOptions.newBuilder()
                .setProjectId(project)
                .setCredentials(auth.credentials())
                .build()
                .getService();
        this.tableId = TableId.of(project, dataset, table);
        System.out.printf("BigQuery sink → %s.%s.%s (auth=%s)%n", project, dataset, table, auth.source());
    }

    private static CredentialsAndSource loadCredentials() {
        Path fromEnv = pathIfFile(System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));
        if (fromEnv != null) {
            return new CredentialsAndSource(fromStream(fromEnv), "file:" + fromEnv.toAbsolutePath());
        }
        Path local = pathIfFile("secrets/gcp.json");
        if (local != null) {
            return new CredentialsAndSource(fromStream(local), "file:" + local.toAbsolutePath());
        }
        try {
            GoogleCredentials adc = GoogleCredentials.getApplicationDefault();
            return new CredentialsAndSource(adc, "application-default-credentials");
        } catch (IOException exception) {
            throw new IllegalStateException(
                    """
                    No BigQuery credentials found (org policy often blocks SA key download).
                    For local/dev, use user ADC (no key file):
                      gcloud auth login
                      gcloud config set project YOUR_PROJECT_ID
                      gcloud auth application-default login
                      gcloud auth application-default set-quota-project YOUR_PROJECT_ID
                    Then ensure your user has BigQuery Data Editor on the dataset/table.
                    """,
                    exception);
        }
    }

    private static Path pathIfFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path resolved = Path.of(path);
        return Files.isRegularFile(resolved) ? resolved : null;
    }

    private static GoogleCredentials fromStream(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return GoogleCredentials.fromStream(in);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read credentials from " + path.toAbsolutePath(), exception);
        }
    }

    @Override
    public void writeBatch(List<SinkRecord> records) {
        InsertAllRequest.Builder request = InsertAllRequest.newBuilder(tableId);
        for (SinkRecord record : records) {
            request.addRow(record.event().eventId(), toRow(record));
        }
        InsertAllResponse response = bigQuery.insertAll(request.build());
        if (response.hasErrors()) {
            Map<Long, List<BigQueryError>> errors = response.getInsertErrors();
            throw new IllegalStateException("BigQuery insertAll errors: " + errors);
        }
        System.out.printf("bigquery-sink wrote %d row(s)%n", records.size());
    }

    private static Map<String, Object> toRow(SinkRecord record) {
        Map<String, Object> row = new HashMap<>();
        row.put("event_id", record.event().eventId());
        row.put("user_id", record.event().userId());
        row.put("event_type", record.event().eventType());
        row.put("event_ts", record.event().eventTs().toString());
        if (record.event().amount() != null) {
            row.put("amount", record.event().amount());
        }
        if (record.event().page() != null) {
            row.put("page", record.event().page());
        }
        row.put("kafka_partition", record.partition());
        row.put("kafka_offset", record.offset());
        row.put("ingested_at", java.time.Instant.now().toString());
        return row;
    }

    private record CredentialsAndSource(Credentials credentials, String source) {}
}
