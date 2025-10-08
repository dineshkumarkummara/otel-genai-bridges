### Summary
The community project **otel-genai-bridges** ships a LangChain4j-focused starter (`langchain4j-otel`) that automatically instruments ChatLanguageModel calls using the OpenTelemetry Generative AI semantic conventions (spans + metrics + prompt/completion events). It would be helpful if the official docs referenced this integration so users can enable end-to-end GenAI telemetry today.

### What’s included
- Spring Boot starter: `com.dineshkumarkummara.otel:langchain4j-otel`
- Emits spans with `gen_ai.*` attributes, prompt/assistant/tool events, and metrics for latency, tokens, errors, cost, and RAG retrieval latency
- Works out of the box with OTLP collectors (Collector → Tempo/Prometheus → Grafana)

### Snippet
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

![Grafana latency panel](https://github.com/dineshkumarkummara/otel-genai-bridges/raw/main/docs/screenshots/grafana-latency.png)

### Proposal
Add a docs page (or section in the observability guide) that points to this starter as a community-supported solution while deeper instrumentation work is considered. Repository with libraries, samples, and dashboards: https://github.com/dineshkumarkummara/otel-genai-bridges
