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

using System.Linq;
using System.Net.Http.Headers;
using System.Text.Json;
using Microsoft.Extensions.Options;

namespace SkOtel;

/// <summary>
/// Delegating handler that wraps Semantic Kernel HTTP traffic with OpenTelemetry spans and metrics.
/// </summary>
public sealed class SemanticKernelDelegatingHandler : DelegatingHandler
{
    private readonly SemanticKernelTelemetry _telemetry;
    private readonly SemanticKernelTelemetryOptions _options;

    public SemanticKernelDelegatingHandler(
        SemanticKernelTelemetry telemetry,
        IOptions<SemanticKernelTelemetryOptions> options)
    {
        _telemetry = telemetry;
        _options = options.Value;
    }

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        string? prompt = null;
        if (request.Content is not null && _options.CapturePrompts)
        {
            prompt = await request.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
        }

        var scope = _telemetry.StartChat(prompt);
        scope.Activity?.SetTag("http.method", request.Method.Method);
        scope.Activity?.SetTag("url.full", request.RequestUri?.ToString());

        try
        {
            var response = await base.SendAsync(request, cancellationToken).ConfigureAwait(false);
            string? completion = null;
            if (response.Content is not null && _options.CaptureCompletions)
            {
                completion = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
            }

            var tokens = ParseTokenHeaders(response.Headers);
            bool? cached = ParseCached(response.Headers);

            _telemetry.CompleteChat(
                scope,
                completion,
                tokens.inputTokens,
                tokens.outputTokens,
                response.ReasonPhrase,
                cached,
                toolCalls: null);
            return response;
        }
        catch (Exception ex)
        {
            _telemetry.RecordError(scope, ex);
            throw;
        }
    }

    private static (int? inputTokens, int? outputTokens) ParseTokenHeaders(HttpResponseHeaders headers)
    {
        int? input = TryReadInt(headers, "x-genai-input-tokens");
        int? output = TryReadInt(headers, "x-genai-output-tokens");
        return (input, output);
    }

    private static bool? ParseCached(HttpResponseHeaders headers)
    {
        if (headers.TryGetValues("x-cache-hit", out var values))
        {
            var value = values.FirstOrDefault();
            if (bool.TryParse(value, out var parsed))
            {
                return parsed;
            }
        }
        return null;
    }

    private static int? TryReadInt(HttpResponseHeaders headers, string header)
    {
        if (headers.TryGetValues(header, out var values))
        {
            var value = values.FirstOrDefault();
            if (int.TryParse(value, out var parsed))
            {
                return parsed;
            }
        }
        return null;
    }
}
