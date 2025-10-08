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

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatLanguageModel chatModel;
    private final RagKnowledgeBase knowledgeBase;

    public ChatService(ChatLanguageModel chatModel, RagKnowledgeBase knowledgeBase) {
        this.chatModel = chatModel;
        this.knowledgeBase = knowledgeBase;
    }

    public ChatView chat(String question) {
        Response<AiMessage> response = chatModel.generate(List.of(UserMessage.from("CHAT:" + question)));
        log.info("Handled /chat question='{}'", question);
        return ChatView.from(response, List.of());
    }

    public ChatView rag(String question) {
        List<RagKnowledgeBase.RagDocument> docs = knowledgeBase.search(question, 3);
        Response<AiMessage> response = chatModel.generate(List.of(UserMessage.from("RAG:" + question)));
        log.info("Handled /rag question='{}' with {} docs", question, docs.size());
        return ChatView.from(response, docs);
    }
}
