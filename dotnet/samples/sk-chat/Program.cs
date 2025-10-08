// Copyright 2024 Dinesh Kumar Kummara
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

using System.Text.Json;
using System.Diagnostics;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using Microsoft.SemanticKernel.ChatCompletion;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using SkChat;
using SkOtel;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddSemanticKernelTelemetry(options =>
{
    options.System = "local.semantic-kernel";
    options.Model = "sk-local-mock";
    options.CapturePrompts = true;
    options.CaptureCompletions = true;
    options.Cost.Enabled = true;
    options.Cost.InputPerThousand = 0.0001;
    options.Cost.OutputPerThousand = 0.00015;
});

var otlpEndpoint = builder.Configuration["OTEL_EXPORTER_OTLP_ENDPOINT"] ?? "http://localhost:4317";

builder.Services.AddOpenTelemetry()
    .ConfigureResource(resource => resource.AddService("sk-chat"))
    .WithTracing(tracing => tracing
        .AddSource("otel-genai-bridges/semantic-kernel")
        .AddAspNetCoreInstrumentation()
        .AddOtlpExporter(exporter => exporter.Endpoint = new Uri(otlpEndpoint)))
    .WithMetrics(metrics => metrics
        .AddMeter("otel-genai-bridges/semantic-kernel")
        .AddAspNetCoreInstrumentation()
        .AddOtlpExporter(exporter => exporter.Endpoint = new Uri(otlpEndpoint)));

builder.Services.AddSingleton<IChatCompletionService, MockChatCompletionService>();
builder.Services.AddSingleton<SemanticKernelTelemetryMiddleware>();

var app = builder.Build();

app.UseMiddleware<SemanticKernelTelemetryMiddleware>();

app.MapPost("/chat", async ([FromBody] ChatRequest request, IChatCompletionService chatService, HttpContext httpContext) =>
{
    var history = new Microsoft.SemanticKernel.ChatCompletion.ChatHistory();
    history.AddUserMessage(request.Question);
    var responses = await chatService.GetChatMessageContentsAsync(history);
    var message = responses.FirstOrDefault()?.Content ?? "No response available.";
    var metadata = responses.FirstOrDefault()?.Metadata;
    int? inputTokens = TryGetInt(metadata, "gen_ai.usage.input_tokens");
    int? outputTokens = TryGetInt(metadata, "gen_ai.usage.output_tokens");
    var stopReason = metadata?.TryGetValue("gen_ai.response.finish_reason", out var value) == true
        ? value?.ToString()
        : null;

    httpContext.Items["sk.completion"] = message;
    httpContext.Items["sk.tokens.input"] = inputTokens;
    httpContext.Items["sk.tokens.output"] = outputTokens;
    httpContext.Items["sk.stopReason"] = stopReason;
    httpContext.Items["sk.cached"] = false;

    return Results.Json(new
    {
        answer = message,
        tokens = new { input = inputTokens, output = outputTokens }
    });
});

app.MapPost("/rag", async ([FromBody] ChatRequest request, IChatCompletionService chatService, HttpContext httpContext) =>
{
    var augmentedPrompt = $"Use retrieval augmented generation to answer: {request.Question}";
    var history = new Microsoft.SemanticKernel.ChatCompletion.ChatHistory();
    history.AddUserMessage(augmentedPrompt);
    var ragTimer = Stopwatch.StartNew();
    await FakeRetrieverAsync();
    ragTimer.Stop();
    var responses = await chatService.GetChatMessageContentsAsync(history);
    var message = responses.FirstOrDefault()?.Content ?? "No response available.";
    httpContext.Items["sk.completion"] = message;
    httpContext.Items["sk.stopReason"] = "stop";
    httpContext.Items["sk.rag.latency"] = ragTimer.Elapsed.TotalMilliseconds;
    return Results.Json(new { answer = message });
});

app.Run();

static int? TryGetInt(IReadOnlyDictionary<string, object?>? metadata, string key)
{
    if (metadata is not null && metadata.TryGetValue(key, out var value) && value is int intValue)
    {
        return intValue;
    }
    if (metadata is not null && metadata.TryGetValue(key, out var obj) && obj is JsonElement element && element.ValueKind == JsonValueKind.Number)
    {
        return element.GetInt32();
    }
    return null;
}

static Task FakeRetrieverAsync()
{
    // Simulate retrieving documents and embedding lookups.
    return Task.Delay(TimeSpan.FromMilliseconds(75));
}
