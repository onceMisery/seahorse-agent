/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.domain.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import lombok.Data;

import java.util.List;

/**
 * Seahorse 流式问答上下文。
 */
@Data
public class StreamChatContext {

    private String question;

    private String originalQuestion;

    private String conversationId;

    private String taskId;

    private boolean deepThinking;

    private String userId;

    private StreamCallback callback;

    private TraceRunScope traceRunScope;

    private List<ChatMessage> history;

    private RewriteResult rewriteResult;

    private List<SubQuestionIntent> subIntents;

    private MemoryContext memoryContext;

    private ContextPack contextPack;

    private QueryOptimizationResult queryOptimizationResult;

    private List<String> attachmentIds;

    private List<String> knowledgeBaseIds;

    private Long roleCardId;

    private Long branchLeafMessageId;

    private ResolvedRoleCard roleCard;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final StreamChatContext context = new StreamChatContext();

        public Builder question(String question) {
            context.setQuestion(question);
            context.setOriginalQuestion(question);
            return this;
        }

        public Builder conversationId(String conversationId) {
            context.setConversationId(conversationId);
            return this;
        }

        public Builder taskId(String taskId) {
            context.setTaskId(taskId);
            return this;
        }

        public Builder deepThinking(boolean deepThinking) {
            context.setDeepThinking(deepThinking);
            return this;
        }

        public Builder userId(String userId) {
            context.setUserId(userId);
            return this;
        }

        public Builder callback(StreamCallback callback) {
            context.setCallback(callback);
            return this;
        }

        public Builder traceRunScope(TraceRunScope traceRunScope) {
            context.setTraceRunScope(traceRunScope);
            return this;
        }

        public Builder contextPack(ContextPack contextPack) {
            context.setContextPack(contextPack);
            return this;
        }

        public Builder attachmentIds(List<String> attachmentIds) {
            context.setAttachmentIds(attachmentIds == null ? List.of() : List.copyOf(attachmentIds));
            return this;
        }

        public Builder knowledgeBaseIds(List<String> knowledgeBaseIds) {
            context.setKnowledgeBaseIds(knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds));
            return this;
        }

        public Builder roleCardId(Long roleCardId) {
            context.setRoleCardId(roleCardId);
            return this;
        }

        public Builder branchLeafMessageId(Long branchLeafMessageId) {
            context.setBranchLeafMessageId(branchLeafMessageId);
            return this;
        }

        public Builder roleCard(ResolvedRoleCard roleCard) {
            context.setRoleCard(roleCard);
            return this;
        }

        public StreamChatContext build() {
            if (context.getAttachmentIds() == null) {
                context.setAttachmentIds(List.of());
            }
            if (context.getKnowledgeBaseIds() == null) {
                context.setKnowledgeBaseIds(List.of());
            }
            return context;
        }
    }
}
