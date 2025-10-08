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

import io.opentelemetry.api.OpenTelemetry;
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
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TelemetryConfiguration {

    private SdkTracerProvider tracerProvider;
    private SdkMeterProvider meterProvider;

    @Bean
    public OtlpGrpcSpanExporter otlpGrpcSpanExporter(
            @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}") String endpoint) {
        return OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).setTimeout(Duration.ofSeconds(5)).build();
    }

    @Bean
    public OtlpGrpcMetricExporter otlpGrpcMetricExporter(
            @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}") String endpoint) {
        return OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).setTimeout(Duration.ofSeconds(5)).build();
    }

    @Bean(destroyMethod = "close")
    public SdkTracerProvider sdkTracerProvider(OtlpGrpcSpanExporter spanExporter) {
        tracerProvider = SdkTracerProvider.builder()
                .setResource(serviceResource())
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setScheduleDelay(Duration.ofMillis(250))
                        .build())
                .build();
        return tracerProvider;
    }

    @Bean(destroyMethod = "close")
    public SdkMeterProvider sdkMeterProvider(OtlpGrpcMetricExporter metricExporter) {
        meterProvider = SdkMeterProvider.builder()
                .setResource(serviceResource())
                .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                        .setInterval(Duration.ofSeconds(1))
                        .build())
                .build();
        return meterProvider;
    }

    @Bean
    @Primary
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider, SdkMeterProvider meterProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();
    }

    private Resource serviceResource() {
        return Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "genai-rag-service"));
    }

    @PreDestroy
    public void shutdown() {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        if (meterProvider != null) {
            meterProvider.close();
        }
    }
}
