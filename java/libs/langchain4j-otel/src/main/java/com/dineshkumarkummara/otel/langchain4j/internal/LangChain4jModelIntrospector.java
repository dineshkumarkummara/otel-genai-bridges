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
package com.dineshkumarkummara.otel.langchain4j.internal;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reflectively extracts metadata from LangChain4j model implementations.
 */
public final class LangChain4jModelIntrospector {

    public ModelMetadata introspect(Object model) {
        if (model == null) {
            return ModelMetadata.empty();
        }
        Class<?> type = model.getClass();
        String system = guessSystem(type);
        String modelName = stringResult(model, "modelName", "getModelName", "model", "getModel")
                .orElse(null);
        Double temperature = doubleResult(model, "temperature", "getTemperature").orElse(null);
        Double topP = doubleResult(model, "topP", "getTopP").orElse(null);
        Integer maxTokens = intResult(model, "maxTokens", "getMaxTokens").orElse(null);
        @SuppressWarnings("unchecked")
        List<String> stopSequences = (List<String>) objectResult(model, "stop", "getStop", "stopSequences", "getStopSequences")
                .filter(List.class::isInstance)
                .orElse(null);
        Boolean cached = booleanResult(model, "cache", "isCache", "cached", "isCached").orElse(false);
        Duration timeout = durationResult(model, "timeout", "getTimeout", "requestTimeout", "getRequestTimeout").orElse(null);

        return new ModelMetadata(system, modelName, temperature, topP, maxTokens, stopSequences, cached, timeout);
    }

    private Optional<String> stringResult(Object target, String... methods) {
        return objectResult(target, methods).map(Object::toString);
    }

    private Optional<Double> doubleResult(Object target, String... methods) {
        return objectResult(target, methods)
                .map(obj -> obj instanceof Number ? ((Number) obj).doubleValue() : null);
    }

    private Optional<Integer> intResult(Object target, String... methods) {
        return objectResult(target, methods)
                .map(obj -> obj instanceof Number ? ((Number) obj).intValue() : null);
    }

    private Optional<Boolean> booleanResult(Object target, String... methods) {
        return objectResult(target, methods)
                .map(obj -> obj instanceof Boolean ? (Boolean) obj : null);
    }

    private Optional<Duration> durationResult(Object target, String... methods) {
        return objectResult(target, methods)
                .map(value -> {
                    if (value instanceof Duration duration) {
                        return duration;
                    }
                    if (value instanceof Number number) {
                        return Duration.ofMillis(number.longValue());
                    }
                    return null;
                });
    }

    private Optional<Object> objectResult(Object target, String... methods) {
        for (String method : methods) {
            if (method == null || method.isEmpty()) {
                continue;
            }
            try {
                Method m = target.getClass().getMethod(method);
                if (!m.canAccess(target)) {
                    m.setAccessible(true);
                }
                Object result = m.invoke(target);
                if (result != null) {
                    return Optional.of(result);
                }
            } catch (ReflectiveOperationException ignored) {
                // ignore and continue
            }
        }
        return Optional.empty();
    }

    private String guessSystem(Class<?> type) {
        String name = type.getName().toLowerCase(Locale.ROOT);
        if (name.contains("openai")) {
            return "openai";
        }
        if (name.contains("ollama")) {
            return "ollama";
        }
        if (name.contains("mistral")) {
            return "mistral_ai";
        }
        if (name.contains("azure")) {
            return "azure.ai.openai";
        }
        if (name.contains("bedrock")) {
            return "aws.bedrock";
        }
        return null;
    }

    /** Immutable snapshot of model metadata. */
    public record ModelMetadata(
            String system,
            String model,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stopSequences,
            Boolean cached,
            Duration timeout) {

        public static ModelMetadata empty() {
            return new ModelMetadata(null, null, null, null, null, Collections.emptyList(), false, null);
        }
    }
}
