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

using System.Collections.Immutable;
using System.Runtime.CompilerServices;
using System.Linq;
using Microsoft.SemanticKernel;
using Microsoft.SemanticKernel.ChatCompletion;

namespace SkChat;

/// <summary>
/// Deterministic in-memory chat completion service that mimics an LLM for demo purposes.
/// </summary>
public sealed class MockChatCompletionService : IChatCompletionService
{
    public string ModelId => "sk-local-mock";

    public IReadOnlyDictionary<string, object?> Attributes => new Dictionary<string, object?>();

    public Task<IReadOnlyList<ChatMessageContent>> GetChatMessageContentsAsync(
        ChatHistory chatHistory,
        PromptExecutionSettings? executionSettings = null,
        Kernel? kernel = null,
        CancellationToken cancellationToken = default)
    {
        string question = chatHistory.LastOrDefault()?.Content ?? "";
        string answer = $"Telemetry friendly response for: {question}";
        var metadata = new Dictionary<string, object?>
        {
            ["gen_ai.usage.input_tokens"] = CountTokens(question),
            ["gen_ai.usage.output_tokens"] = CountTokens(answer),
            ["gen_ai.response.finish_reason"] = "stop"
        };
        ChatMessageContent content = new(AuthorRole.Assistant, answer, metadata: metadata.ToImmutableDictionary());
        return Task.FromResult<IReadOnlyList<ChatMessageContent>>(new List<ChatMessageContent> { content });
    }

    public async IAsyncEnumerable<StreamingChatMessageContent> GetStreamingChatMessageContentsAsync(
        ChatHistory chatHistory,
        PromptExecutionSettings? executionSettings = null,
        Kernel? kernel = null,
        [EnumeratorCancellation] CancellationToken cancellationToken = default)
    {
        var responses = await GetChatMessageContentsAsync(chatHistory, executionSettings, kernel, cancellationToken)
            .ConfigureAwait(false);
        foreach (var response in responses)
        {
            yield return new StreamingChatMessageContent(response.Role, response.Content ?? string.Empty);
        }
    }

    private static int CountTokens(string text)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            return 0;
        }
        return text.Split(' ', StringSplitOptions.RemoveEmptyEntries).Length;
    }
}
