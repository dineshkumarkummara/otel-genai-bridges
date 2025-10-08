/*
 * Copyright 2024 Dinesh Kumar Kummara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dineshkumarkummara.otel.rag;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record ChatView(String answer, List<Citation> citations, Usage usage) {

    public static ChatView from(Response<AiMessage> response, List<RagKnowledgeBase.RagDocument> docs) {
        AiMessage content = response.content();
        String answer = content != null ? Objects.toString(content.text(), "") : "";
        List<Citation> mapped = docs.stream()
                .map(doc -> new Citation(doc.title(), doc.score()))
                .collect(Collectors.toList());
        TokenUsage tokenUsage = response.tokenUsage();
        Usage usage = new Usage(
                tokenUsage != null ? tokenUsage.inputTokenCount() : null,
                tokenUsage != null ? tokenUsage.outputTokenCount() : null,
                tokenUsage != null ? tokenUsage.totalTokenCount() : null);
        return new ChatView(answer, mapped, usage);
    }

    public record Citation(String title, double score) {}

    public record Usage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {}
}
