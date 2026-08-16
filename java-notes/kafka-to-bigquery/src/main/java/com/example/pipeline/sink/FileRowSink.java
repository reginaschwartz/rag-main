package com.example.pipeline.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

/** Local stand-in for BigQuery: appends NDJSON rows (one JSON object per line). */
public final class FileRowSink implements RowSink {

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Object lock = new Object();

    public FileRowSink(String path) throws Exception {
        this.path = Path.of(path);
        Files.createDirectories(this.path.getParent() == null ? Path.of(".") : this.path.getParent());
        if (!Files.exists(this.path)) {
            Files.createFile(this.path);
        }
        System.out.println("File sink → " + this.path.toAbsolutePath());
    }

    @Override
    public void writeBatch(List<SinkRecord> records) throws Exception {
        synchronized (lock) {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
                for (SinkRecord record : records) {
                    ObjectNode row = mapper.createObjectNode();
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
                    row.put("ingested_at", Instant.now().toString());
                    writer.write(mapper.writeValueAsString(row));
                    writer.newLine();
                }
            }
        }
        System.out.printf("file-sink wrote %d row(s)%n", records.size());
    }
}
