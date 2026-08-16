#!/usr/bin/env bash
# Run EventProducerApp on the host.
#   ./scripts/run-producer.sh           # normal
#   ./scripts/run-producer.sh --debug   # JDWP; attach IDE to localhost:PRODUCER_DEBUG_PORT
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

: "${KAFKA_BOOTSTRAP:=localhost:19092}"
: "${PRODUCER_DEBUG_PORT:=5006}"
: "${DEBUG_SUSPEND:=n}"

DEBUG=0
for arg in "$@"; do
  case "$arg" in
    --debug|-d) DEBUG=1 ;;
    --help|-h)
      echo "Usage: $0 [--debug]"
      echo "  --debug  enable JDWP on :\${PRODUCER_DEBUG_PORT:-5006} (suspend=\${DEBUG_SUSPEND:-n})"
      exit 0
      ;;
  esac
done

#mvn -q -DskipTests package

JAVA_OPTS=()
if [[ "${DEBUG}" == "1" ]]; then
  JAVA_OPTS+=(
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${DEBUG_SUSPEND},address=*:${PRODUCER_DEBUG_PORT}"
  )
  echo "Starting EventProducerApp with JDWP on :${PRODUCER_DEBUG_PORT} (suspend=${DEBUG_SUSPEND})"
  echo "Attach a Remote JVM Debug configuration to localhost:${PRODUCER_DEBUG_PORT}, then resume."
fi

echo "Producer → ${KAFKA_BOOTSTRAP}"
exec java "${JAVA_OPTS[@]}" -cp target/kafka-to-bigquery-1.0.0.jar com.example.pipeline.EventProducerApp
