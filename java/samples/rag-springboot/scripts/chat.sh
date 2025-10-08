#!/usr/bin/env bash
set -euo pipefail

QUESTION=${1:-"What is LangChain4j?"}

curl -sS -X POST "http://localhost:8080/api/chat" \
  -H 'Content-Type: application/json' \
  -d "{\"question\": \"${QUESTION}\"}" | jq
