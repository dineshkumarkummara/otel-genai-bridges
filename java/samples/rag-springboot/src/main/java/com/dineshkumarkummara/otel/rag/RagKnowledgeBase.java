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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory corpus with naive similarity ranking for RAG demos.
 */
@Component
public class RagKnowledgeBase {

    private final Map<String, RagDocument> documents = new ConcurrentHashMap<>();

    public RagKnowledgeBase() {
        register("otel-intro", "What is OpenTelemetry?",
                "OpenTelemetry (OTel) is a CNCF project that provides APIs and SDKs for collecting traces, metrics, and logs.");
        register("otel-semconv", "GenAI semantic conventions",
                "The OpenTelemetry GenAI semantic conventions standardize span and metric attribute names like gen_ai.request.model.");
        register("rag-pattern", "RAG architecture",
                "Retrieval augmented generation (RAG) retrieves relevant documents, augments the prompt, and lets an LLM answer grounded questions.");
        register("langchain4j", "LangChain4j",
                "LangChain4j is a Java library for orchestrating LLM workflows including chat models, tools, and memories.");
    }

    public void register(String id, String title, String content) {
        documents.put(id, new RagDocument(id, title, content, 0));
    }

    public List<RagDocument> search(String query, int topK) {
        Set<String> queryTokens = tokenize(query);
        return documents.values().stream()
                .map(doc -> doc.withScore(similarity(queryTokens, tokenize(doc.content()))))
                .sorted(Comparator.comparing(RagDocument::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private double similarity(Set<String> queryTokens, Set<String> docTokens) {
        if (queryTokens.isEmpty() || docTokens.isEmpty()) {
            return 0d;
        }
        List<String> intersection = new ArrayList<>(queryTokens);
        intersection.retainAll(docTokens);
        List<String> union = new ArrayList<>(queryTokens);
        union.addAll(docTokens);
        return (double) intersection.size() / (double) union.size();
    }

    public record RagDocument(String id, String title, String content, double score) {
        public RagDocument withScore(double newScore) {
            return new RagDocument(id, title, content, newScore);
        }
    }
}
