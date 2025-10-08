```csharp
// Program.cs (minimal API)
var builder = WebApplication.CreateBuilder(args);

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
        .AddOtlpExporter())
    .WithMetrics(m => m
        .AddMeter("otel-genai-bridges/semantic-kernel")
        .AddAspNetCoreInstrumentation()
        .AddOtlpExporter());

builder.Services.AddSingleton<IChatCompletionService, MyChatCompletionService>();

builder.Services.AddSingleton<SemanticKernelTelemetryMiddleware>();

var app = builder.Build();
app.UseMiddleware<SemanticKernelTelemetryMiddleware>();

app.MapPost("/chat", async ([FromBody] ChatRequest request, IChatCompletionService chatService, HttpContext httpContext) =>
{
    var history = new ChatHistory();
    history.AddUserMessage(request.Question);
    var responses = await chatService.GetChatMessageContentsAsync(history);

    var message = responses.FirstOrDefault()?.Content ?? string.Empty;
    httpContext.Items["sk.completion"] = message;
    httpContext.Items["sk.tokens.input"] =  responses.FirstOrDefault()?.Metadata?["gen_ai.usage.input_tokens"];
    httpContext.Items["sk.tokens.output"] = responses.FirstOrDefault()?.Metadata?["gen_ai.usage.output_tokens"];
    httpContext.Items["sk.stopReason"] = "stop";

    return Results.Json(new { answer = message });
});

app.Run();
```

Add the DelegatingHandler to outbound SK HTTP clients when you need telemetry for hosted LLMs:

```csharp
builder.Services.AddHttpClient("sk-client")
    .AddHttpMessageHandler<SemanticKernelDelegatingHandler>();
```

The middleware captures prompts (from the JSON request body), completions/tokens from `HttpContext.Items`, and automatically records cost, error, tool-call, and RAG latency metrics.
