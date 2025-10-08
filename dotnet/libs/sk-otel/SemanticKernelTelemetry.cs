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

using System.Diagnostics;
using System.Diagnostics.Metrics;
using OpenTelemetry.Trace;

namespace SkOtel;

/// <summary>
/// Helper responsible for emitting OpenTelemetry spans and metrics around Semantic Kernel operations.
/// </summary>
public sealed class SemanticKernelTelemetry
{
    private static readonly ActivitySource ActivitySource = new("otel-genai-bridges/semantic-kernel", "0.1.0");
    private static readonly Meter Meter = new("otel-genai-bridges/semantic-kernel", "0.1.0");

    private readonly Histogram<double> _operationDuration = Meter.CreateHistogram<double>(
        "gen_ai.client.operation.duration",
        unit: "s");
    private readonly Histogram<long> _tokenUsage = Meter.CreateHistogram<long>(
        "gen_ai.client.token.usage",
        unit: "{token}");
    private readonly Counter<long> _errors = Meter.CreateCounter<long>("gen_ai.client.operation.errors");
    private readonly Counter<double> _cost = Meter.CreateCounter<double>(
        "gen_ai.client.operation.cost",
        unit: "usd");
    private readonly Counter<long> _toolCalls = Meter.CreateCounter<long>("gen_ai.client.tool.calls");
    private readonly Histogram<double> _ragLatency = Meter.CreateHistogram<double>(
        "gen_ai.rag.retrieval.latency",
        unit: "ms");

    private readonly SemanticKernelTelemetryOptions _options;

    public SemanticKernelTelemetry(SemanticKernelTelemetryOptions options)
    {
        _options = options;
    }

    public TelemetryScope StartChat(string? prompt)
    {
        var tags = new ActivityTagsCollection
        {
            { "gen_ai.system", _options.System },
            { "gen_ai.operation.name", _options.OperationName },
            { "gen_ai.request.model", _options.Model }
        };
        if (_options.Temperature.HasValue)
        {
            tags.Add("gen_ai.request.temperature", _options.Temperature.Value);
        }
        if (_options.TopP.HasValue)
        {
            tags.Add("gen_ai.request.top_p", _options.TopP.Value);
        }
        if (_options.MaxTokens.HasValue)
        {
            tags.Add("gen_ai.request.max_tokens", _options.MaxTokens.Value);
        }
        tags.Add("gen_ai.response.cached", _options.DefaultCached);
        var activity = ActivitySource.StartActivity(
            $"{_options.OperationName} {_options.Model}",
            ActivityKind.Client,
            parentContext: default,
            tags: tags);
        if (activity is not null && _options.CapturePrompts && !string.IsNullOrWhiteSpace(prompt))
        {
            var promptTags = new ActivityTagsCollection
            {
                { "gen_ai.prompt.content", Trim(prompt!) }
            };
            activity.AddEvent(new ActivityEvent("gen_ai.user.message", tags: promptTags));
        }
        return new TelemetryScope(activity, tags, Stopwatch.GetTimestamp());
    }

    public void CompleteChat(
        TelemetryScope scope,
        string? completionText,
        int? inputTokens,
        int? outputTokens,
        string? stopReason,
        bool? cached,
        IEnumerable<string>? toolCalls)
    {
        if (scope.Activity is not null)
        {
            if (_options.CaptureCompletions && !string.IsNullOrWhiteSpace(completionText))
            {
                var completionTags = new ActivityTagsCollection
                {
                    { "gen_ai.response.content", Trim(completionText!) }
                };
                scope.Activity.AddEvent(new ActivityEvent("gen_ai.assistant.message", tags: completionTags));
            }
            if (!string.IsNullOrWhiteSpace(stopReason))
            {
                scope.Activity.SetTag("gen_ai.response.finish_reasons", new[] { stopReason });
            }
            scope.Activity.SetTag("gen_ai.response.cached", cached ?? _options.DefaultCached);
            if (inputTokens.HasValue)
            {
                scope.Activity.SetTag("gen_ai.usage.input_tokens", inputTokens.Value);
            }
            if (outputTokens.HasValue)
            {
                scope.Activity.SetTag("gen_ai.usage.output_tokens", outputTokens.Value);
            }
            scope.Activity.Dispose();
        }

        RecordDuration(scope);
        RecordTokenMetrics(scope, inputTokens, outputTokens);
        RecordCost(scope, inputTokens, outputTokens);
        RecordToolCalls(scope, toolCalls);
    }

    public void RecordError(TelemetryScope scope, Exception ex)
    {
        if (scope.Activity is not null)
        {
            scope.Activity.SetStatus(ActivityStatusCode.Error, ex.Message);
            scope.Activity.SetTag("error.type", ex.GetType().FullName);
            scope.Activity.RecordException(ex);
            scope.Activity.Dispose();
        }
        _errors.Add(1, BuildMetricTags(scope.Tags));
        RecordDuration(scope);
    }

    public void RecordRagLatency(TelemetryScope scope, double milliseconds)
    {
        _ragLatency.Record(milliseconds, BuildMetricTags(scope.Tags));
    }

    private void RecordDuration(TelemetryScope scope)
    {
        double seconds = (Stopwatch.GetTimestamp() - scope.StartTimestamp) / (double)Stopwatch.Frequency;
        _operationDuration.Record(seconds, BuildMetricTags(scope.Tags));
    }

    private void RecordTokenMetrics(TelemetryScope scope, int? inputTokens, int? outputTokens)
    {
        if (inputTokens.HasValue)
        {
            _tokenUsage.Record(inputTokens.Value, BuildMetricTags(scope.Tags, "input"));
        }
        if (outputTokens.HasValue)
        {
            _tokenUsage.Record(outputTokens.Value, BuildMetricTags(scope.Tags, "output"));
        }
    }

    private void RecordCost(TelemetryScope scope, int? inputTokens, int? outputTokens)
    {
        if (!_options.Cost.Enabled)
        {
            return;
        }
        double total = 0;
        if (inputTokens.HasValue && _options.Cost.InputPerThousand.HasValue)
        {
            total += inputTokens.Value / 1000d * _options.Cost.InputPerThousand.Value;
        }
        if (outputTokens.HasValue && _options.Cost.OutputPerThousand.HasValue)
        {
            total += outputTokens.Value / 1000d * _options.Cost.OutputPerThousand.Value;
        }
        if (total > 0)
        {
            _cost.Add(total, BuildMetricTags(scope.Tags));
        }
    }

    private void RecordToolCalls(TelemetryScope scope, IEnumerable<string>? toolCalls)
    {
        if (toolCalls is null)
        {
            return;
        }
        foreach (var tool in toolCalls)
        {
            if (string.IsNullOrWhiteSpace(tool))
            {
                continue;
            }
            var tags = BuildMetricTags(scope.Tags);
            tags.Add(new KeyValuePair<string, object?>("tool_name", tool));
            _toolCalls.Add(1, tags);
        }
    }

    private static TagList BuildMetricTags(ActivityTagsCollection sourceTags, string? tokenType = null)
    {
        var tags = new TagList();
        foreach (var tag in sourceTags)
        {
            tags.Add(tag);
        }
        if (tokenType is not null)
        {
            tags.Add("gen_ai.token.type", tokenType);
        }
        return tags;
    }

    private static string Trim(string text)
    {
        if (text.Length <= 4000)
        {
            return text;
        }
        return text[..4000] + "…";
    }

    public readonly record struct TelemetryScope(Activity? Activity, ActivityTagsCollection Tags, long StartTimestamp);
}
