-- Create once in your GCP project before SINK_MODE=bigquery.
--   bq query --use_legacy_sql=false < bigquery/schema.sql

CREATE SCHEMA IF NOT EXISTS `${BQ_DATASET}`
OPTIONS (location = 'US');

CREATE TABLE IF NOT EXISTS `${BQ_DATASET}.events` (
  event_id   STRING NOT NULL,
  user_id    STRING NOT NULL,
  event_type STRING NOT NULL,
  event_ts   TIMESTAMP NOT NULL,
  amount     FLOAT64,
  page       STRING,
  kafka_partition INT64,
  kafka_offset    INT64,
  ingested_at TIMESTAMP NOT NULL
)
PARTITION BY DATE(event_ts)
CLUSTER BY user_id, event_type;
