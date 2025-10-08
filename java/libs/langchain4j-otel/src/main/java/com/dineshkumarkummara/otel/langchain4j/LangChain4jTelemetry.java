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

import static java.util.Objects.requireNonNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Core telemetry engine that turns LangChain4j calls into OpenTelemetry signals.
 */
public final class LangChain4jTelemetry {

    private static final AttributeKey<String> ATTR_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    private static final AttributeKey<String> ATTR_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> ATTR_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<Double> ATTR_REQUEST_TEMPERATURE = AttributeKey.doubleKey("gen_ai.request.temperature");
    private static final AttributeKey<Double> ATTR_REQUEST_TOP_P = AttributeKey.doubleKey("gen_ai.request.top_p");
    private static final AttributeKey<Long> ATTR_REQUEST_MAX_TOKENS = AttributeKey.longKey("gen_ai.request.max_tokens");
    private static final AttributeKey<List<String>> ATTR_REQUEST_STOP_SEQUENCES = AttributeKey.stringArrayKey("gen_ai.request.stop_sequences");
    private static final AttributeKey<List<String>> ATTR_RESPONSE_FINISH_REASONS = AttributeKey.stringArrayKey("gen_ai.response.finish_reasons");
    private static final AttributeKey<Long> ATTR_USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> ATTR_USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<String> ATTR_TOKEN_TYPE = AttributeKey.stringKey("gen_ai.token.type");
    private static final AttributeKey<Boolean> ATTR_RESPONSE_CACHED = AttributeKey.booleanKey("gen_ai.response.cached");
    private static final AttributeKey<String> ATTR_ERROR_TYPE = AttributeKey.stringKey("error.type");
    private static final AttributeKey<String> ATTR_RESPONSE_TEXT = AttributeKey.stringKey("gen_ai.response.content");
    private static final AttributeKey<String> ATTR_PROMPT_TEXT = AttributeKey.stringKey("gen_ai.prompt.content");
    private static final AttributeKey<String> ATTR_TOOL_NAME = AttributeKey.stringKey("tool.name");
    private static final AttributeKey<String> ATTR_RAG_DATASOURCE = AttributeKey.stringKey("datasource");

    private final LangChain4jTelemetryProperties properties;
    private final Tracer tracer;
    private final DoubleHistogram duration;
    private final DoubleHistogram tokenUsage;
    private final LongCounter errorCounter;
    private final DoubleHistogram costHistogram;
    private final LongCounter toolCallCounter;
    private final DoubleHistogram ragLatency;

    public LangChain4jTelemetry(LangChain4jTelemetryProperties properties) {
        this(GlobalOpenTelemetry.get(), properties);
    }

    public LangChain4jTelemetry(OpenTelemetry openTelemetry, LangChain4jTelemetryProperties properties) {
        this.properties = requireNonNull(properties, "properties");
        OpenTelemetry otel = requireNonNull(openTelemetry, "openTelemetry");
        this.tracer = otel.getTracer("otel-genai-bridges/langchain4j");
        this.duration = otel
                .meterBuilder("otel-genai-bridges")
                .setInstrumentationVersion("0.1.0")
                .build()
                .histogramBuilder("gen_ai.client.operation.duration")
                .setUnit("s")
                .build();
        this.tokenUsage = otel
                .meterBuilder("otel-genai-bridges")
                .setInstrumentationVersion("0.1.0")
                .build()
                .histogramBuilder("gen_ai.client.token.usage")
                .setUnit("{token}")
                .build();
        this.errorCounter = otel
                .meterBuilder("otel-genai-bridges")
                .build()
                .counterBuilder("gen_ai.client.operation.errors")
                .build();
        this.costHistogram = otel
                .meterBuilder("otel-genai-bridges")
                .build()
                .histogramBuilder("gen_ai.client.operation.cost")
                .setUnit(properties.getCost().getCurrency().toLowerCase(Locale.ROOT))
                .build();
        this.toolCallCounter = otel
                .meterBuilder("otel-genai-bridges")
                .build()
                .counterBuilder("gen_ai.client.tool.calls")
                .build();
        this.ragLatency = otel
                .meterBuilder("otel-genai-bridges")
                .build()
                .histogramBuilder("gen_ai.rag.retrieval.latency")
                .setUnit("ms")
                .build();
    }

    public Response<AiMessage> instrumentChat(ChatInvocationContext context, Supplier<Response<AiMessage>> delegate) {
        if (!properties.isEnabled()) {
            return delegate.get();
        }

        Attributes baseAttributes = context.toAttributes();
        String spanName = context.spanName();
        Span span = tracer.spanBuilder(spanName).setSpanKind(SpanKind.CLIENT).setAllAttributes(baseAttributes).startSpan();
        long startNanos = System.nanoTime();
        try (Scope scope = span.makeCurrent()) {
            context.emitPromptEvents(span, properties);
            Response<AiMessage> response = delegate.get();
            context.processResponse(span, response, properties);

            finishSpanSuccessfully(span, response, baseAttributes, startNanos);
            return response;
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            span.setAttribute(ATTR_ERROR_TYPE, ex.getClass().getName());
            recordErrorMetric(baseAttributes);
            duration.record(elapsedSeconds(startNanos), baseAttributes);
            span.end();
            throw ex;
        }
    }

    public void recordRagLatency(String datasource, Duration latency, Attributes baseAttributes) {
        if (!properties.isEnabled()) {
            return;
        }
        Attributes attributes = Attributes.builder()
                .putAll(baseAttributes)
                .put(ATTR_RAG_DATASOURCE, datasource)
                .build();
        ragLatency.record(latency.toMillis(), attributes);
    }

    private void finishSpanSuccessfully(
            Span span, Response<AiMessage> response, Attributes baseAttributes, long startNanos) {
        if (response != null) {
            FinishReason finishReason = response.finishReason();
            if (finishReason != null) {
                span.setAttribute(
                        ATTR_RESPONSE_FINISH_REASONS,
                        Collections.singletonList(finishReason.name().toLowerCase(Locale.ROOT)));
            }
            TokenUsage tokenUsage = response.tokenUsage();
            if (tokenUsage != null) {
                ofNullable(tokenUsage.inputTokenCount())
                        .ifPresent(count -> span.setAttribute(ATTR_USAGE_INPUT_TOKENS, count.longValue()));
                ofNullable(tokenUsage.outputTokenCount())
                        .ifPresent(count -> span.setAttribute(ATTR_USAGE_OUTPUT_TOKENS, count.longValue()));
                recordTokenMetrics(tokenUsage, baseAttributes);
                recordCost(tokenUsage, baseAttributes);
            }
            recordToolMetrics(response, baseAttributes);
        }
        span.end();
        duration.record(elapsedSeconds(startNanos), baseAttributes);
    }

    private void recordTokenMetrics(TokenUsage usage, Attributes baseAttributes) {
        if (usage.inputTokenCount() != null) {
            tokenUsage.record(usage.inputTokenCount(), append(baseAttributes, ATTR_TOKEN_TYPE, "input"));
        }
        if (usage.outputTokenCount() != null) {
            tokenUsage.record(usage.outputTokenCount(), append(baseAttributes, ATTR_TOKEN_TYPE, "output"));
        }
    }

    private void recordCost(TokenUsage usage, Attributes baseAttributes) {
        LangChain4jTelemetryProperties.Cost cost = properties.getCost();
        if (!cost.isEnabled()) {
            return;
        }
        double total = 0d;
        if (usage.inputTokenCount() != null && cost.getInputPerThousand() != null) {
            total += (usage.inputTokenCount() / 1000.0d) * cost.getInputPerThousand();
        }
        if (usage.outputTokenCount() != null && cost.getOutputPerThousand() != null) {
            total += (usage.outputTokenCount() / 1000.0d) * cost.getOutputPerThousand();
        }
        if (total > 0) {
            costHistogram.record(total, baseAttributes);
        }
    }

    private void recordToolMetrics(Response<AiMessage> response, Attributes baseAttributes) {
        AiMessage message = response.content();
        if (message == null || !message.hasToolExecutionRequests()) {
            return;
        }
        List<ToolExecutionRequest> requests = message.toolExecutionRequests();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (ToolExecutionRequest request : requests) {
            Attributes attributes = Attributes.builder()
                    .putAll(baseAttributes)
                    .put(ATTR_TOOL_NAME, request.name())
                    .build();
            toolCallCounter.add(1, attributes);
        }
    }

    private void recordErrorMetric(Attributes baseAttributes) {
        errorCounter.add(1, baseAttributes);
    }

    private double elapsedSeconds(long startNanos) {
        double durationNanos = System.nanoTime() - startNanos;
        return durationNanos / 1_000_000_000d;
    }

    private static Attributes append(Attributes base, AttributeKey<String> key, String value) {
        AttributesBuilder builder = Attributes.builder();
        builder.putAll(base);
        builder.put(key, value);
        return builder.build();
    }

    private static <T> Optional<T> ofNullable(T value) {
        return Optional.ofNullable(value);
    }

    public static final class ChatInvocationContext {

        private final List<ChatMessage> messages;
        private final String model;
        private final String system;
        private final String operation;
        private final Double temperature;
        private final Double topP;
        private final Integer maxTokens;
        private final List<String> stopSequences;
        private final boolean cached;
        private final Duration timeout;

        public ChatInvocationContext(
                LangChain4jTelemetryProperties properties,
                List<ChatMessage> messages,
                String model,
                String system,
                String operation,
                Double temperature,
                Double topP,
                Integer maxTokens,
                List<String> stopSequences,
                boolean cached,
                Duration timeout) {
            this.messages = messages != null ? messages : List.of();
            this.model = model != null ? model : properties.getDefaultModel();
            this.system = system != null ? system : properties.getSystem();
            this.operation = operation != null ? operation : properties.getOperationName();
            this.temperature = temperature != null ? temperature : properties.getTuning().getTemperature();
            this.topP = topP != null ? topP : properties.getTuning().getTopP();
            this.maxTokens = maxTokens != null ? maxTokens : properties.getTuning().getMaxTokens();
            this.stopSequences = stopSequences != null ? stopSequences : properties.getTuning().getStopSequences();
            this.cached = cached || properties.isDefaultCached();
            this.timeout = timeout != null ? timeout : properties.getTuning().getTimeout();
        }

        public String spanName() {
            return operation + " " + (model != null ? model : "unknown-model");
        }

        public Attributes toAttributes() {
            AttributesBuilder builder = Attributes.builder()
                    .put(ATTR_SYSTEM, system)
                    .put(ATTR_OPERATION_NAME, operation);
            if (model != null) {
                builder.put(ATTR_REQUEST_MODEL, model);
            }
            ofNullable(temperature).ifPresent(value -> builder.put(ATTR_REQUEST_TEMPERATURE, value));
            ofNullable(topP).ifPresent(value -> builder.put(ATTR_REQUEST_TOP_P, value));
            ofNullable(maxTokens).ifPresent(value -> builder.put(ATTR_REQUEST_MAX_TOKENS, value.longValue()));
            ofNullable(stopSequences).ifPresent(value -> builder.put(ATTR_REQUEST_STOP_SEQUENCES, value));
            builder.put(ATTR_RESPONSE_CACHED, cached);
            ofNullable(timeout)
                    .map(Duration::toMillis)
                    .ifPresent(value -> builder.put(AttributeKey.longKey("gen_ai.request.timeout_ms"), value));
            return builder.build();
        }

        public void emitPromptEvents(Span span, LangChain4jTelemetryProperties properties) {
            if (!properties.isCapturePrompts()) {
                return;
            }
            for (ChatMessage message : messages) {
                if (message == null) {
                    continue;
                }
                if (message.type() == ChatMessageType.SYSTEM) {
                    span.addEvent(
                            "gen_ai.system.message",
                            Attributes.of(ATTR_SYSTEM, system, ATTR_PROMPT_TEXT, safeText(message.text())));
                } else if (message.type() == ChatMessageType.USER) {
                    String role = message instanceof UserMessage && ((UserMessage) message).name() != null
                            ? ((UserMessage) message).name()
                            : "user";
                    AttributesBuilder builder = Attributes.builder()
                            .put(ATTR_SYSTEM, system)
                            .put(ATTR_PROMPT_TEXT, safeText(message.text()))
                            .put(AttributeKey.stringKey("role"), role);
                    span.addEvent("gen_ai.user.message", builder.build());
                }
            }
        }

        public void processResponse(Span span, Response<AiMessage> response, LangChain4jTelemetryProperties properties) {
            if (response == null) {
                return;
            }
            AiMessage content = response.content();
            if (content != null) {
                if (properties.isCaptureCompletions()) {
                    span.addEvent(
                            "gen_ai.assistant.message",
                            Attributes.of(ATTR_SYSTEM, system, ATTR_RESPONSE_TEXT, safeText(content.text())));
                }
                if (content.hasToolExecutionRequests()) {
                    for (ToolExecutionRequest request : content.toolExecutionRequests()) {
                        span.addEvent(
                                "gen_ai.tool.message",
                                Attributes.of(ATTR_SYSTEM, system, ATTR_TOOL_NAME, request.name()));
                    }
                }
            }
        }

        private String safeText(String text) {
            if (text == null) {
                return "";
            }
            if (text.length() > 4000) {
                return text.substring(0, 4000) + "…";
            }
            return text;
        }
    }
}
