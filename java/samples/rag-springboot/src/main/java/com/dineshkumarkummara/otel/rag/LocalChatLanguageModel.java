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

import com.dineshkumarkummara.otel.langchain4j.LangChain4jTelemetry;
import com.dineshkumarkummara.otel.langchain4j.LangChain4jTelemetryProperties;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lightweight local model that simulates LLM behaviour for demos and tests.
 */
@Component
public class LocalChatLanguageModel implements ChatLanguageModel {

    private final RagKnowledgeBase knowledgeBase;
    private final LangChain4jTelemetry telemetry;
    private final LangChain4jTelemetryProperties telemetryProperties;
    private final String modelName = "local-mock-gpt";
    private final double temperature = 0.1d;
    private final double topP = 0.9d;
    private final int maxTokens = 512;
    private final List<String> stopSequences = List.of("###");

    public LocalChatLanguageModel(
            RagKnowledgeBase knowledgeBase,
            LangChain4jTelemetry telemetry,
            LangChain4jTelemetryProperties telemetryProperties) {
        this.knowledgeBase = knowledgeBase;
        this.telemetry = telemetry;
        this.telemetryProperties = telemetryProperties;
    }

    public String modelName() {
        return modelName;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getTopP() {
        return topP;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String prompt = messages == null || messages.isEmpty() ? "" : messages.get(messages.size() - 1).text();
        boolean ragMode = prompt != null && prompt.startsWith("RAG:");
        String question = prompt != null ? prompt.replaceFirst("^(RAG:|CHAT:)", "").trim() : "";
        RagResponse ragResponse = ragMode ? answerWithKnowledge(question) : answerConversation(question);
        TokenUsage usage = new TokenUsage(countTokens(prompt), countTokens(ragResponse.answer()), countTokens(prompt) + countTokens(ragResponse.answer()));
        return Response.from(AiMessage.from(ragResponse.answer()), usage, FinishReason.STOP);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages);
    }

    private RagResponse answerWithKnowledge(String question) {
        long start = System.nanoTime();
        List<RagKnowledgeBase.RagDocument> docs = knowledgeBase.search(question, 3);
        Duration latency = Duration.ofNanos(System.nanoTime() - start);
        recordRagLatency(latency);
        String context = docs.stream()
                .map(doc -> "- " + doc.title() + " (score=" + String.format(Locale.US, "%.2f", doc.score()) + "): " + doc.content())
                .collect(Collectors.joining("\n"));
        String answer = "Grounded answer:\n" + context + "\nSummary: " + buildSummary(question, docs);
        return new RagResponse(answer, docs);
    }

    private RagResponse answerConversation(String question) {
        String answer = "This is a chat response from " + modelName + ". You asked: " + question;
        return new RagResponse(answer, List.of());
    }

    private String buildSummary(String question, List<RagKnowledgeBase.RagDocument> docs) {
        if (docs.isEmpty()) {
            return "I could not find supporting documents, but I recommend checking the OpenTelemetry docs.";
        }
        return "Based on " + docs.get(0).title() + ", " + question + " relates to OpenTelemetry GenAI best practices.";
    }

    private int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) java.util.Arrays.stream(text.split("\\s+")).filter(token -> !token.isBlank()).count();
    }

    private void recordRagLatency(Duration latency) {
        if (telemetry == null) {
            return;
        }
        Attributes attributes = Attributes.builder()
                .put(AttributeKey.stringKey("gen_ai.system"),
                        telemetryProperties.getSystem() != null ? telemetryProperties.getSystem() : "local.mock")
                .put(AttributeKey.stringKey("gen_ai.request.model"), modelName())
                .put(AttributeKey.stringKey("gen_ai.operation.name"), telemetryProperties.getOperationName())
                .build();
        telemetry.recordRagLatency("local-knowledge-base", latency, attributes);
    }

    public record RagResponse(String answer, List<RagKnowledgeBase.RagDocument> documents) {}
}
