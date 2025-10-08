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
package com.dineshkumarkummara.otel.langchain4j;

import com.dineshkumarkummara.otel.langchain4j.internal.LangChain4jModelIntrospector;
import com.dineshkumarkummara.otel.langchain4j.internal.LangChain4jModelIntrospector.ModelMetadata;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import java.util.List;

/**
 * ChatLanguageModel decorator that emits OpenTelemetry signals.
 */
public final class OtelChatLanguageModel implements ChatLanguageModel {

    private final ChatLanguageModel delegate;
    private final LangChain4jTelemetry telemetry;
    private final LangChain4jTelemetryProperties properties;
    private final LangChain4jModelIntrospector introspector;

    private OtelChatLanguageModel(
            ChatLanguageModel delegate,
            LangChain4jTelemetry telemetry,
            LangChain4jTelemetryProperties properties,
            LangChain4jModelIntrospector introspector) {
        this.delegate = delegate;
        this.telemetry = telemetry;
        this.properties = properties;
        this.introspector = introspector;
    }

    public static ChatLanguageModel wrap(
            ChatLanguageModel delegate,
            LangChain4jTelemetry telemetry,
            LangChain4jTelemetryProperties properties,
            LangChain4jModelIntrospector introspector) {
        if (delegate instanceof OtelChatLanguageModel) {
            return delegate;
        }
        return new OtelChatLanguageModel(delegate, telemetry, properties, introspector);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        LangChain4jTelemetry.ChatInvocationContext context = buildContext(messages);
        return telemetry.instrumentChat(context, () -> delegate.generate(messages));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        LangChain4jTelemetry.ChatInvocationContext context = buildContext(messages);
        return telemetry.instrumentChat(context, () -> delegate.generate(messages, toolSpecifications));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        LangChain4jTelemetry.ChatInvocationContext context = buildContext(messages);
        return telemetry.instrumentChat(context, () -> delegate.generate(messages, toolSpecification));
    }

    private LangChain4jTelemetry.ChatInvocationContext buildContext(List<ChatMessage> messages) {
        ModelMetadata metadata = introspector.introspect(delegate);
        return new LangChain4jTelemetry.ChatInvocationContext(
                properties,
                messages,
                metadata.model(),
                metadata.system(),
                properties.getOperationName(),
                metadata.temperature(),
                metadata.topP(),
                metadata.maxTokens(),
                metadata.stopSequences(),
                Boolean.TRUE.equals(metadata.cached()),
                metadata.timeout());
    }
}
