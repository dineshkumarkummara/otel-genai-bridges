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

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration entry point allowing Spring Boot users to pick up instrumentation automatically.
 */
@AutoConfiguration
@ConditionalOnClass(ChatLanguageModel.class)
@EnableConfigurationProperties(LangChain4jTelemetryProperties.class)
@ConditionalOnProperty(prefix = "otel.langchain4j", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LangChain4jTelemetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LangChain4jTelemetry langChain4jTelemetry(
            LangChain4jTelemetryProperties properties, ObjectProvider<OpenTelemetry> openTelemetry) {
        OpenTelemetry otel = openTelemetry.getIfAvailable(OpenTelemetry::noop);
        if (otel == OpenTelemetry.noop()) {
            return new LangChain4jTelemetry(properties);
        }
        return new LangChain4jTelemetry(otel, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public LangChain4jTelemetryBeanPostProcessor langChain4jTelemetryBeanPostProcessor(
            LangChain4jTelemetry telemetry, LangChain4jTelemetryProperties properties) {
        return new LangChain4jTelemetryBeanPostProcessor(telemetry, properties);
    }
}
