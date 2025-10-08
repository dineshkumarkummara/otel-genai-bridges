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

namespace SkOtel;

/// <summary>
/// Options controlling Semantic Kernel OpenTelemetry enrichment.
/// </summary>
public sealed class SemanticKernelTelemetryOptions
{
    public string System { get; set; } = "openai";

    public string Model { get; set; } = "gpt-mock";

    public string OperationName { get; set; } = "chat";

    public bool CapturePrompts { get; set; }

    public bool CaptureCompletions { get; set; }

    public bool DefaultCached { get; set; }

    public double? Temperature { get; set; }

    public double? TopP { get; set; }

    public int? MaxTokens { get; set; }

    public CostOptions Cost { get; set; } = new();

    public sealed class CostOptions
    {
        public bool Enabled { get; set; }

        public double? InputPerThousand { get; set; }

        public double? OutputPerThousand { get; set; }

        public string Currency { get; set; } = "USD";
    }
}
