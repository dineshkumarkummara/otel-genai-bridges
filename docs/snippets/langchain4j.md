```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.dineshkumarkummara.otel</groupId>
  <artifactId>langchain4j-otel</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
// Application.java
@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
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
    cost:
      enabled: true
      input-per-thousand: 0.0005
      output-per-thousand: 0.0015
```

```java
// Any @Configuration class (optional tweaks)
@Bean
public ChatLanguageModel openAiChatModel() {
  return OpenAiChatModel.withApiKey(System.getenv("OPENAI_API_KEY"));
}
```

With these pieces in place the starter auto-wraps every `ChatLanguageModel` bean, emitting OTLP spans (with prompt/completion events), token metrics, error counters, cost histograms, tool call counts, and optional RAG latency measurements when you call `LangChain4jTelemetry#recordRagLatency`.
