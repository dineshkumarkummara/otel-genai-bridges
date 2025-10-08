# Contributing to otel-genai-bridges

Thanks for your interest in extending OpenTelemetry GenAI Bridges! This project exists so Java and .NET AI teams can adopt the OpenTelemetry Generative AI semantic conventions with minimal ceremony. Contributions of all kinds are welcome—bug reports, instrumentation improvements, documentation, dashboards, and samples.

## Project structure

- `java/libs/langchain4j-otel` — LangChain4j + Spring Boot instrumentation library
- `java/samples/rag-springboot` — Java sample application exercising the library
- `dotnet/libs/sk-otel` — Semantic Kernel instrumentation (middleware + delegating handler)
- `dotnet/samples/sk-chat` — Minimal API sample for Semantic Kernel
- `collector` and `dashboards` — Dockerized observability stack assets
- `docs` — Quickstart, upstream drafts, social copy, and media

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). Please read it before engaging with the community.

## Getting started

1. Fork the repository and clone your fork.
2. Install prerequisites:
   - Docker Desktop 4.28+
   - JDK 17+
   - .NET 8 SDK
3. Run `./scripts/run_all.sh` (or `./scripts/run_all.ps1`) to build the libraries, start the observability stack, and launch both sample apps.
4. Verify Grafana (http://localhost:3000) and the API endpoints (`http://localhost:8080/api/chat`, `http://localhost:7080/chat`).

## Development workflow

- Java:
  ```bash
  ./mvnw -f java/pom.xml -pl libs/langchain4j-otel test
  ./mvnw -f java/pom.xml -pl samples/rag-springboot package
  ```
- .NET:
  ```bash
  dotnet test dotnet/libs/sk-otel.Tests/SkOtel.Tests.csproj
  dotnet build dotnet/samples/sk-chat/SkChat.csproj
  ```
- Observability stack: `docker compose -f collector/docker-compose.yaml up -d`

Before opening a pull request:

1. Format your code (IDE defaults are fine; keep imports tidy).
2. Ensure unit tests pass for the languages you touched.
3. Update dashboards/docs/scripts if telemetry shape changed.
4. Run the lints/tests in CI locally where practical.
5. Provide screenshots of Grafana or traces when changing observability UX.

## Commit & PR guidelines

- Keep commits focused on a single logical change.
- Reference related issues in the description (e.g., `Fixes #123`).
- Include before/after screenshots for UI/dashboard changes.
- When adding dependencies, explain why they are necessary.
- Mark Testcontainers-based tests with `@Testcontainers(disabledWithoutDocker = true)` to keep CI stable.

## Reporting issues

Use the templates in `.github/ISSUE_TEMPLATE`. Include steps to reproduce, expected vs actual behaviour, and environment details (OS, JDK, .NET SDK versions, Docker version).

## Release cadence

The project targets iterative releases as the OpenTelemetry GenAI spec stabilises. If you need a tagged build, open an issue describing the requirement.

Happy hacking! 🎉
