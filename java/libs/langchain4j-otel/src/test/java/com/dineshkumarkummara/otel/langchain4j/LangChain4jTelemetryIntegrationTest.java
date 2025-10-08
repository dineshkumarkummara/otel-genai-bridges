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

import static org.assertj.core.api.Assertions.assertThat;

import com.dineshkumarkummara.otel.langchain4j.internal.LangChain4jModelIntrospector;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers(disabledWithoutDocker = true)
class LangChain4jTelemetryIntegrationTest {

    @Container
    static GenericContainer<?> collector = new GenericContainer<>(DockerImageName.parse("otel/opentelemetry-collector-contrib:0.94.0"))
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("collector-config.yaml"),
                    "/etc/otelcol-config.yaml")
            .withCommand("--config", "/etc/otelcol-config.yaml")
            .withExposedPorts(4317);

    @Test
    void emitsGenAiSpanAndMetrics() throws Exception {
        int mappedPort = collector.getMappedPort(4317);
        String endpoint = "http://" + collector.getHost() + ":" + mappedPort;

        try (OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                        .setEndpoint(endpoint)
                        .setTimeout(Duration.ofSeconds(5))
                        .build();
                OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                        .setEndpoint(endpoint)
                        .setTimeout(Duration.ofSeconds(5))
                        .build();
                SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                        .setResource(Resource.create(
                                Attributes.of(AttributeKey.stringKey("service.name"), "langchain4j-otel-test")))
                        .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                                .setScheduleDelay(Duration.ofMillis(200))
                                .build())
                        .build();
                SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                        .setResource(Resource.create(
                                Attributes.of(AttributeKey.stringKey("service.name"), "langchain4j-otel-test")))
                        .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                                .setInterval(Duration.ofMillis(500))
                                .build())
                        .build()) {
            OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setMeterProvider(meterProvider)
                    .build();

            LangChain4jTelemetryProperties properties = new LangChain4jTelemetryProperties();
            properties.setSystem("openai");
            properties.setCapturePrompts(true);
            properties.setCaptureCompletions(true);
            properties.getCost().setEnabled(true);
            properties.getCost().setInputPerThousand(0.001);
            properties.getCost().setOutputPerThousand(0.002);

            LangChain4jTelemetry telemetry = new LangChain4jTelemetry(sdk, properties);
            ChatLanguageModel stub = messages -> Response.from(
                    AiMessage.from("Hello from LangChain4j"),
                    new TokenUsage(32, 12, 44),
                    FinishReason.STOP);
            ChatLanguageModel instrumented = OtelChatLanguageModel.wrap(
                    stub, telemetry, properties, new LangChain4jModelIntrospector());

            instrumented.generate(List.of(UserMessage.from("ping")));

            tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
            meterProvider.forceFlush().join(5, TimeUnit.SECONDS);

            Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .until(() -> collector.getLogs().contains("gen_ai.request.model"));

            String logs = collector.getLogs();
            assertThat(logs).contains("gen_ai.request.model");
            assertThat(logs).contains("gen_ai.response.content");
            assertThat(logs).contains("gen_ai.usage.input_tokens");
        }
    }
}
