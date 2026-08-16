# Kafka → BigQuery streaming pipeline

## Goal

A small end-to-end streaming path:

```text
EventProducer ──JSON──► Kafka topic `analytics.events`
                              │
                              ▼
                     BigQuerySink (consumer)
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
     File sink (local demo)          BigQuery streaming inserts
     `./out/events.ndjson`           `project.dataset.events`
```

## Design choices

| Choice | Decision | Why |
|--------|----------|-----|
| Delivery | At-least-once | Manual Kafka commit only after a successful sink flush |
| Write API | BigQuery `insertAll` (streaming inserts) | Simple for demos; low latency; no staging files |
| Batching | Max rows **or** max linger | Fewer BQ RPCs without adding a full Flink/Spark stack |
| Local mode | `SINK_MODE=file` | Pipeline is testable without a GCP project |
| Serialization | JSON strings on Kafka | Easy to inspect; no Avro registry required |

## Failure / ordering notes

- A crash after BigQuery accepts rows but before `commitSync` can redeliver → possible **duplicate rows** in BQ. Production would add an `event_id` dedupe key or use the Storage Write API with offsets.
- Partition key = `user_id` so one user’s events stay ordered within a partition.
- `basic`-style consumer `max.poll.records` + flush batch size keep memory bounded.

## Run

```bash
cd java-notes/kafka-to-bigquery
cp .env.example .env   # already present as .env with file-sink defaults
docker compose --env-file .env up --build
cat out/events.ndjson
```

- Default `SINK_MODE=file` writes NDJSON to `./out/events.ndjson` (no GCP needed)
- Logs: `docker compose logs -f sink producer`
- Real BigQuery: put `secrets/gcp.json`, set `SINK_MODE=bigquery` + `BQ_PROJECT` in `.env`, create table from `bigquery/schema.sql`
