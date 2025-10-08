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

using System.IO;
using System.Text.Json;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Http.Extensions;

namespace SkOtel;

/// <summary>
/// ASP.NET Core middleware that captures Semantic Kernel request-response cycles and decorates them with telemetry.
/// </summary>
public sealed class SemanticKernelTelemetryMiddleware : IMiddleware
{
    private readonly SemanticKernelTelemetry _telemetry;

    public SemanticKernelTelemetryMiddleware(SemanticKernelTelemetry telemetry)
    {
        _telemetry = telemetry;
    }

    public async Task InvokeAsync(HttpContext context, RequestDelegate next)
    {
        string? prompt = await ExtractPromptAsync(context).ConfigureAwait(false);
        var scope = _telemetry.StartChat(prompt);
        scope.Activity?.SetTag("http.route", context.Request.Path);
        scope.Activity?.SetTag("http.request.method", context.Request.Method);
        scope.Activity?.SetTag("url.full", context.Request.GetDisplayUrl());
        context.Items["sk.telemetry.scope"] = scope;

        try
        {
            await next(context);
            string? completion = context.Items.TryGetValue("sk.completion", out var completionObj)
                ? completionObj?.ToString()
                : null;
            int? inputTokens = TryGetInt(context.Items, "sk.tokens.input");
            int? outputTokens = TryGetInt(context.Items, "sk.tokens.output");
            string? stopReason = context.Items.TryGetValue("sk.stopReason", out var stop)
                ? stop?.ToString()
                : context.Response.StatusCode.ToString();
            bool? cached = context.Items.TryGetValue("sk.cached", out var cachedObj) && cachedObj is bool cachedValue
                ? cachedValue
                : null;
            var tools = context.Items.TryGetValue("sk.tools", out var toolsObj) && toolsObj is IEnumerable<string> toolNames
                ? toolNames
                : null;

            _telemetry.CompleteChat(scope, completion, inputTokens, outputTokens, stopReason, cached, tools);

            if (context.Items.TryGetValue("sk.rag.latency", out var ragLatencyObj) &&
                ragLatencyObj is double ragLatencyMs)
            {
                _telemetry.RecordRagLatency(scope, ragLatencyMs);
            }
        }
        catch (Exception ex)
        {
            _telemetry.RecordError(scope, ex);
            throw;
        }
    }

    private static async Task<string?> ExtractPromptAsync(HttpContext context)
    {
        if (!HttpMethods.IsPost(context.Request.Method))
        {
            return null;
        }
        if (context.Request.ContentType is null || !context.Request.ContentType.Contains("application/json", StringComparison.OrdinalIgnoreCase))
        {
            return null;
        }
        context.Request.EnableBuffering();
        using var reader = new StreamReader(context.Request.Body, leaveOpen: true);
        var body = await reader.ReadToEndAsync().ConfigureAwait(false);
        context.Request.Body.Position = 0;
        if (string.IsNullOrWhiteSpace(body))
        {
            return null;
        }
        try
        {
            using var document = JsonDocument.Parse(body);
            if (document.RootElement.TryGetProperty("question", out var questionElement))
            {
                return questionElement.GetString();
            }
        }
        catch (JsonException)
        {
            // ignore malformed bodies - instrumentation remains best effort
        }
        return null;
    }

    private static int? TryGetInt(IDictionary<object, object?> items, string key)
    {
        if (items.TryGetValue(key, out var value))
        {
            if (value is int intValue)
            {
                return intValue;
            }
            if (value is long longValue)
            {
                return (int)longValue;
            }
            if (value is string stringValue && int.TryParse(stringValue, out var parsed))
            {
                return parsed;
            }
        }
        return null;
    }
}
