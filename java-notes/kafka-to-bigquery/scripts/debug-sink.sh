#!/usr/bin/env bash
# Debug BigQuerySinkApp on the host (JDWP). Attach IDE to localhost:DEBUG_PORT.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

: "${KAFKA_BOOTSTRAP:=localhost:19092}"
: "${FILE_SINK_PATH:=out/events.ndjson}"
: "${DEBUG_PORT:=5005}"
: "${DEBUG_SUSPEND:=y}"

if [[ "${SINK_MODE:-file}" == "bigquery" ]]; then
  if [[ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" && ! -f "${GOOGLE_APPLICATION_CREDENTIALS}" ]]; then
    echo "WARN: GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS} missing — unsetting and using ADC"
    unset GOOGLE_APPLICATION_CREDENTIALS
  fi
  if [[ -z "${GOOGLE_APPLICATION_CREDENTIALS:-}" && -f secrets/gcp.json ]]; then
    export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/secrets/gcp.json"
  fi
  if [[ -z "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]]; then
    echo "SINK_MODE=bigquery with no key file — using Application Default Credentials."
    echo "If auth fails, run:"
    echo "  gcloud auth application-default login"
    echo "  gcloud auth application-default set-quota-project ${BQ_PROJECT:-YOUR_PROJECT_ID}"
  fi
  if [[ -z "${BQ_PROJECT:-}" ]]; then
    echo "ERROR: BQ_PROJECT must be set in .env"
    exit 1
  fi
fi

mkdir -p out
#mvn -q -DskipTests package

echo "Starting BigQuerySinkApp with JDWP on :${DEBUG_PORT} (suspend=${DEBUG_SUSPEND})"
echo "Attach a Remote JVM Debug configuration to localhost:${DEBUG_PORT}, then resume."
echo "Kafka bootstrap: ${KAFKA_BOOTSTRAP}"
echo "SINK_MODE=${SINK_MODE:-file} BQ_PROJECT=${BQ_PROJECT:-} creds=${GOOGLE_APPLICATION_CREDENTIALS:-ADC}"

exec java \
  -agentlib:jdwp=transport=dt_socket,server=y,suspend="${DEBUG_SUSPEND}",address="*:${DEBUG_PORT}" \
  -cp target/kafka-to-bigquery-1.0.0.jar \
  com.example.pipeline.BigQuerySinkApp
