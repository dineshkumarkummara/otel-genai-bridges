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
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Automatically wraps LangChain4j models registered as Spring beans.
 */
public final class LangChain4jTelemetryBeanPostProcessor implements BeanPostProcessor {

    private final LangChain4jTelemetry telemetry;
    private final LangChain4jTelemetryProperties properties;
    private final LangChain4jModelIntrospector introspector = new LangChain4jModelIntrospector();

    public LangChain4jTelemetryBeanPostProcessor(
            LangChain4jTelemetry telemetry, LangChain4jTelemetryProperties properties) {
        this.telemetry = telemetry;
        this.properties = properties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!properties.isEnabled()) {
            return bean;
        }
        if (bean instanceof OtelChatLanguageModel) {
            return bean;
        }
        if (bean instanceof ChatLanguageModel chatModel) {
            return OtelChatLanguageModel.wrap(chatModel, telemetry, properties, introspector);
        }
        return bean;
    }
}
