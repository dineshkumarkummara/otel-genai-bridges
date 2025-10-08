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

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Spring Boot configuration properties for LangChain4j OpenTelemetry instrumentation.
 */
@ConfigurationProperties(prefix = "otel.langchain4j")
public class LangChain4jTelemetryProperties {

    /** Whether instrumentation is enabled. */
    private boolean enabled = true;

    /** GenAI provider identifier (maps to {@code gen_ai.system}). */
    private String system = "openai";

    /** Default model identifier when not derivable from the LangChain4j model. */
    private String defaultModel;

    /** Default operation name (e.g. chat, text_completion). */
    private String operationName = "chat";

    /** Log and export full prompts. Disabled by default for privacy. */
    private boolean capturePrompts;

    /** Log and export model completions. Disabled by default for privacy. */
    private boolean captureCompletions;

    /** Flag to mark responses as cached. */
    private boolean defaultCached;

    /** Optional cost calculator configuration. */
    @NestedConfigurationProperty
    private Cost cost = new Cost();

    /** Optional tuning defaults applied when provider details cannot be inferred. */
    @NestedConfigurationProperty
    private Tuning tuning = new Tuning();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public boolean isCapturePrompts() {
        return capturePrompts;
    }

    public void setCapturePrompts(boolean capturePrompts) {
        this.capturePrompts = capturePrompts;
    }

    public boolean isCaptureCompletions() {
        return captureCompletions;
    }

    public void setCaptureCompletions(boolean captureCompletions) {
        this.captureCompletions = captureCompletions;
    }

    public boolean isDefaultCached() {
        return defaultCached;
    }

    public void setDefaultCached(boolean defaultCached) {
        this.defaultCached = defaultCached;
    }

    public Cost getCost() {
        return cost;
    }

    public void setCost(Cost cost) {
        this.cost = cost;
    }

    public Tuning getTuning() {
        return tuning;
    }

    public void setTuning(Tuning tuning) {
        this.tuning = tuning;
    }

    public static class Cost {

        /** Whether cost instrumentation is enabled. */
        private boolean enabled;

        /** Cost currency code (default USD). */
        private String currency = "USD";

        /** Cost per 1K input tokens. */
        private Double inputPerThousand;

        /** Cost per 1K output tokens. */
        private Double outputPerThousand;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public Double getInputPerThousand() {
            return inputPerThousand;
        }

        public void setInputPerThousand(Double inputPerThousand) {
            this.inputPerThousand = inputPerThousand;
        }

        public Double getOutputPerThousand() {
            return outputPerThousand;
        }

        public void setOutputPerThousand(Double outputPerThousand) {
            this.outputPerThousand = outputPerThousand;
        }
    }

    public static class Tuning {

        private Double temperature;

        private Double topP;

        private Integer maxTokens;

        private List<String> stopSequences;

        private Duration timeout;

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public List<String> getStopSequences() {
            return stopSequences;
        }

        public void setStopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
