---
title: "Sample: OpenTelemetry GenAI instrumentation for SK apps"
---

**Summary**
`otel-genai-bridges` ships a .NET library (`SkOtel`) that provides both a DelegatingHandler and ASP.NET Core middleware to emit OpenTelemetry Generative AI semantic convention telemetry for Semantic Kernel apps. The repo includes a runnable sample (`dotnet/samples/sk-chat`) and dashboards. Surfacing this in the SK docs/samples would give developers an immediate path to observability.

**Key features**
- `AddSemanticKernelTelemetry` extension registers options, middleware, and handler.
- Middleware captures prompts from request bodies, completions/tool data via `HttpContext.Items`, and records latency, token usage, errors, costs, and RAG retrieval durations.
- DelegatingHandler can be attached to outbound SK HTTP clients for hosted model calls.
- Includes Grafana/Tempo/Prometheus stack and CI demonstrating end-to-end OTLP instrumentation.

**Code snippet**
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

Repository: https://github.com/dineshkumarkummara/otel-genai-bridges

![Grafana token throughput panel](https://github.com/dineshkumarkummara/otel-genai-bridges/raw/main/docs/screenshots/grafana-tokens.png)

**Proposal**
Add a community sample entry (or docs page) highlighting this instrumentation and linking to the repo, so SK developers can quickly adopt OTel GenAI telemetry while the ecosystem matures.
