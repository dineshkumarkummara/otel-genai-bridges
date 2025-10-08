#!/usr/bin/env bash
set -euo pipefail

QUESTION=${1:-"Explain OpenTelemetry GenAI semantic conventions"}

curl -sS -X POST "http://localhost:8080/api/rag" \
  -H 'Content-Type: application/json' \
  -d "{\"question\": \"${QUESTION}\"}" | jq
