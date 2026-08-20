#!/usr/bin/env bash
# Publish from the workstation JVM. The Docker stack must already be up
# (it owns the topic). This process never creates the topic.
#
#   docker compose up --build
#   ./scripts/run-producer.sh
set -euo pipefail
cd "$(dirname "$0")/.."

: "${KAFKA_BOOTSTRAP:=localhost:19092}"
: "${KAFKA_TOPIC:=workstation.events}"
: "${PRODUCER_EVENTS:=12}"
: "${PRODUCER_INTERVAL_MS:=250}"

export KAFKA_BOOTSTRAP KAFKA_TOPIC PRODUCER_EVENTS PRODUCER_INTERVAL_MS


echo "Workstation producer → ${KAFKA_BOOTSTRAP} topic=${KAFKA_TOPIC}"
#exec mvn -q -DskipTests exec:java
