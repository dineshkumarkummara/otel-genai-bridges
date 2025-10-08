---
title: "Docs: Add OpenTelemetry GenAI instrumentation guide"
---

**Summary**
An external project (`otel-genai-bridges`) provides a LangChain4j-focused library `langchain4j-otel` that auto-instruments ChatLanguageModel usage following the OpenTelemetry Generative AI semantic conventions (spans + metrics + events). It would be helpful for LangChain4j users if the official docs pointed to this library as a ready-made solution until native instrumentation lands.

**What this includes**
- Drop-in Spring Boot starter (`com.dineshkumarkummara.otel:langchain4j-otel`) that wraps any `ChatLanguageModel` bean.
- Emits spans with `gen_ai.*` attributes, prompt/assistant events, tool-call annotations, and metrics for latency, token usage, cost, errors, and RAG retrieval latency.
- Works out of the box with OTLP exporters (Collector, Tempo, Prometheus).

**Code snippet**
```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.dineshkumarkummara.otel</groupId>
  <artifactId>langchain4j-otel</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
# application.yaml
otel:
  langchain4j:
    enabled: true
    system: openai
    default-model: gpt-4o
    capture-prompts: true
    capture-completions: true
```

Full repository (libraries, samples, dashboards): https://github.com/dineshkumarkummara/otel-genai-bridges

**Proposal**
Add a docs page (or section under the observability guide) that highlights this library as a community-driven instrumentation option and links back to the repo. This gives users immediate instrumentation while the project evaluates deeper OTel integration.
