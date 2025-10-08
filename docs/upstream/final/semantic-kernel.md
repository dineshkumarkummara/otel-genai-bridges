### Summary
**otel-genai-bridges** provides a .NET library (`SkOtel`) that adds both ASP.NET Core middleware and a DelegatingHandler to emit OpenTelemetry Generative AI telemetry for Semantic Kernel apps. Surfacing this as a community sample in the SK docs would give teams a ready-made path to end-to-end GenAI observability.

### Key features
- `AddSemanticKernelTelemetry` extension registers options, middleware, and handler
- Captures prompts/completions/tool invocations, tokens, latency, cost, and RAG retrieval latency
- Bundled sample (`dotnet/samples/sk-chat`) plus Dockerized Collector → Tempo/Prometheus → Grafana stack

### Snippet
```csharp
builder.Services.AddSemanticKernelTelemetry(options =>
{
    options.System = "azure.ai.openai";
    options.Model = "gpt-4o";
    options.CapturePrompts = true;
    options.CaptureCompletions = true;
});

builder.Services.AddOpenTelemetry()
    .ConfigureResource(r => r.AddService("sk-chat"))
    .WithTracing(t => t
        .AddSource("otel-genai-bridges/semantic-kernel")
        .AddAspNetCoreInstrumentation()
        .AddOtlpExporter());
```

![Grafana token throughput panel](https://github.com/dineshkumarkummara/otel-genai-bridges/raw/main/docs/screenshots/grafana-tokens.png)

### Proposal
Add a docs/sample entry referencing this instrumentation so developers can adopt OTEL GenAI semantics today. Repository: https://github.com/dineshkumarkummara/otel-genai-bridges
