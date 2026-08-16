package com.example.pipeline;

/** Environment-driven settings shared by producer and sink. */
public final class PipelineConfig {

    public final String bootstrapServers;
    public final String topic;
    public final String groupId;
    public final String sinkMode; // file | bigquery
    public final String fileSinkPath;
    public final String bqProject;
    public final String bqDataset;
    public final String bqTable;
    public final int batchSize;
    public final long lingerMs;
    public final int producerEvents;
    public final long producerIntervalMs;

    public PipelineConfig() {
        this.bootstrapServers = env("KAFKA_BOOTSTRAP", "localhost:19092");
        this.topic = env("KAFKA_TOPIC", "analyticsevents");
        this.groupId = env("KAFKA_GROUP_ID", "bq-sink");
        this.sinkMode = env("SINK_MODE", "bigquery").toLowerCase();
        this.fileSinkPath = env("FILE_SINK_PATH", "/out/events.ndjson");
        this.bqProject = env("BQ_PROJECT", "project-791486eb-fb42-4dc8-a22");
        this.bqDataset = env("BQ_DATASET", "analytics");
        this.bqTable = env("BQ_TABLE", "events");
        this.batchSize = Integer.parseInt(env("SINK_BATCH_SIZE", "50"));
        this.lingerMs = Long.parseLong(env("SINK_LINGER_MS", "2000"));
        this.producerEvents = Integer.parseInt(env("PRODUCER_EVENTS", "30"));
        this.producerIntervalMs = Long.parseLong(env("PRODUCER_INTERVAL_MS", "200"));
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
