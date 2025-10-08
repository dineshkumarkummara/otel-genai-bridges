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

using System.Collections.Generic;
using System.Diagnostics;
using OpenTelemetry;
using OpenTelemetry.Exporter;
using OpenTelemetry.Trace;
using SkOtel;

public class SemanticKernelTelemetryTests
{
    [Fact]
    public void CompleteChat_EmitsSpanWithAttributes()
    {
        var exportedActivities = new List<Activity>();
        using var exporter = new InMemoryExporter<Activity>(exportedActivities);
        using var provider = Sdk.CreateTracerProviderBuilder()
            .AddSource("otel-genai-bridges/semantic-kernel")
            .AddProcessor(new SimpleActivityExportProcessor(exporter))
            .Build();

        var options = new SemanticKernelTelemetryOptions
        {
            System = "azure.ai.openai",
            Model = "gpt-mock",
            CapturePrompts = true,
            CaptureCompletions = true
        };
        var telemetry = new SemanticKernelTelemetry(options);

        var scope = telemetry.StartChat("Hello world");
        telemetry.CompleteChat(scope, "Response", 12, 24, "stop", false, new[] { "search" });

        Assert.Single(exportedActivities);
        var activity = exportedActivities[0];
        Assert.Equal("chat gpt-mock", activity.DisplayName);
        Assert.Equal("azure.ai.openai", activity.GetTagItem("gen_ai.system"));
        Assert.Equal(12, activity.GetTagItem("gen_ai.usage.input_tokens"));
        Assert.Contains(activity.Events, e => e.Name == "gen_ai.user.message");
        Assert.Contains(activity.Events, e => e.Name == "gen_ai.assistant.message");
    }

    [Fact]
    public void RecordError_SetsStatus()
    {
        var exportedActivities = new List<Activity>();
        using var exporter = new InMemoryExporter<Activity>(exportedActivities);
        using var provider = Sdk.CreateTracerProviderBuilder()
            .AddSource("otel-genai-bridges/semantic-kernel")
            .AddProcessor(new SimpleActivityExportProcessor(exporter))
            .Build();

        var telemetry = new SemanticKernelTelemetry(new SemanticKernelTelemetryOptions());
        var scope = telemetry.StartChat("fail");

        telemetry.RecordError(scope, new InvalidOperationException("boom"));

        Assert.Single(exportedActivities);
        var activity = exportedActivities[0];
        Assert.Equal(ActivityStatusCode.Error, activity.Status);
        Assert.Equal("boom", activity.StatusDescription);
        Assert.Equal("System.InvalidOperationException", activity.GetTagItem("error.type"));
    }
}
