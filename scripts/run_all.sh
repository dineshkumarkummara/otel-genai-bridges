#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_JAR="$ROOT_DIR/java/samples/rag-springboot/target/rag-springboot-0.1.0-SNAPSHOT.jar"
COMPOSE_FILE="$ROOT_DIR/collector/docker-compose.yaml"
JAVA_PID=""
DOTNET_PID=""

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DOCKER_COMPOSE=(docker-compose)
else
  echo "Docker Compose is required. Install Docker Desktop or docker-compose." >&2
  exit 1
fi

command -v java >/dev/null 2>&1 || { echo "Java 17+ is required." >&2; exit 1; }
command -v dotnet >/dev/null 2>&1 || { echo ".NET 8 SDK is required." >&2; exit 1; }

cleanup() {
  local exit_code=$?
  if [[ -n "$JAVA_PID" ]] && ps -p "$JAVA_PID" >/dev/null 2>&1; then
    kill "$JAVA_PID" 2>/dev/null || true
  fi
  if [[ -n "$DOTNET_PID" ]] && ps -p "$DOTNET_PID" >/dev/null 2>&1; then
    kill "$DOTNET_PID" 2>/dev/null || true
  fi
  "${DOCKER_COMPOSE[@]}" -f "$COMPOSE_FILE" down --remove-orphans >/dev/null 2>&1 || true
  exit $exit_code
}
trap cleanup EXIT INT TERM

pushd "$ROOT_DIR" >/dev/null

./mvnw -pl libs/langchain4j-otel -am package
./mvnw -pl samples/rag-springboot -am package

dotnet build dotnet/libs/sk-otel/SkOtel.csproj
dotnet build dotnet/samples/sk-chat/SkChat.csproj

"${DOCKER_COMPOSE[@]}" -f "$COMPOSE_FILE" up -d --remove-orphans
sleep 5

if [[ ! -f "$JAVA_JAR" ]]; then
  echo "Unable to find Spring Boot executable JAR at $JAVA_JAR" >&2
  exit 1
fi

java -jar "$JAVA_JAR" &
JAVA_PID=$!

dotnet run --project dotnet/samples/sk-chat/SkChat.csproj --urls http://localhost:7080 &
DOTNET_PID=$!

GRAFANA_URL="http://localhost:3000/d/genai-overview"
if command -v open >/dev/null 2>&1; then
  open "$GRAFANA_URL" >/dev/null 2>&1 || true
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$GRAFANA_URL" >/dev/null 2>&1 || true
fi

printf "\n🚀 OpenTelemetry GenAI Bridges stack is live\n"
printf "  • Spring Boot RAG service: http://localhost:8080\n"
printf "  • Semantic Kernel chat:   http://localhost:7080\n"
printf "  • Grafana dashboards:     %s\n" "$GRAFANA_URL"
printf "Press Ctrl+C to stop everything.\n"

wait
