# Quickstart

This guide walks through running the full OpenTelemetry GenAI Bridges stack locally and poking at the telemetry it emits.

## 1. Prerequisites

- Docker Desktop 4.28+ (or a Docker Engine with the `docker compose` plugin)
- JDK 17+
- .NET 8 SDK
- `curl` and `jq` for quick demos (optional but handy)

## 2. Clone and Bootstrap

```bash
git clone https://github.com/dineshkumarkummara/otel-genai-bridges.git
cd otel-genai-bridges
```

## 3. Launch Everything

Use the helper script to compile the libraries, package the samples, start the Docker stack, and run both applications:

```bash
./scripts/run_all.sh
```

PowerShell users can run `./scripts/run_all.ps1`.

When the script completes you should have:

| Component | URL |
|-----------|-----|
| Spring Boot RAG sample | http://localhost:8080 |
| Semantic Kernel sample | http://localhost:7080 |
| Grafana dashboards | http://localhost:3000/d/genai-overview |
| Tempo | http://localhost:3200 |
| Prometheus | http://localhost:9090 |

> **Tip:** The script traps `Ctrl+C`, tears down the Docker services, and terminates the background app processes automatically.

## 4. Exercise the Samples

```bash
# LangChain4j chat completion
curl -s http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"How do GenAI semantic conventions work?"}' | jq

# LangChain4j RAG endpoint
curl -s http://localhost:8080/api/rag \
  -H 'Content-Type: application/json' \
  -d '{"question":"Explain retrieval augmented generation"}' | jq

# Semantic Kernel chat
curl -s http://localhost:7080/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"Give me observability best practices"}' | jq
```

The Semantic Kernel middleware captures prompts/completions automatically, so no telemetry-specific code is required in the handlers.

## 5. Explore Grafana & Tempo

- The **GenAI Observability Overview** dashboard shows latency (P50/P95/P99), token throughput, tool call breakdown, error rates, cost per second, and RAG retrieval latency.
- Use the **TraceQL search** panel at the bottom to jump into recent spans (service name `genai-rag-service` for LangChain4j, `sk-chat` for Semantic Kernel).
- Tempo surfaces prompt/response events (`gen_ai.user.message`, `gen_ai.assistant.message`) and tool call events when present.

## 6. Shut Down

Hit `Ctrl+C` in the terminal that launched `run_all.sh`/`.ps1`. All background processes and Docker services will be stopped.

## 7. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Could not connect to docker daemon` | Ensure Docker Desktop is running and `docker ps` works before starting the stack. |
| Ports already in use | Adjust the exposed ports in `collector/docker-compose.yaml` or stop the conflicting services. |
| Grafana shows “Data source not found” | The provisioning process may take a few seconds. Refresh after 5–10 seconds. |
| No traces appear | Verify the apps are running (requests to ports 8080/7080 return responses) and check Collector logs with `docker compose logs otel-collector`. |

Happy hacking! 🎉
