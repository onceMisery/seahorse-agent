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

package com.miracle.ai.seahorse.agent.ports.inbound.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;

import java.util.Objects;

/**
 * 流式问答命令。
 */
public record StreamChatCommand(
        String question,
        String conversationId,
        String taskId,
        String userId,
        boolean deepThinking,
        ChatMode chatMode,
        String agentId,
        String versionId) {

    public StreamChatCommand {
        question = requireText(question, "question");
        conversationId = requireText(conversationId, "conversationId");
        taskId = requireText(taskId, "taskId");
        userId = Objects.requireNonNullElse(userId, "");
        chatMode = Objects.requireNonNullElse(chatMode, ChatMode.RAG);
        agentId = trimToNull(agentId);
        versionId = trimToNull(versionId);
    }

    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking,
                             ChatMode chatMode) {
        this(question, conversationId, taskId, userId, deepThinking, chatMode, null, null);
    }

    /**
     * 兼容旧 5 参签名：缺省 {@link ChatMode#RAG}。
     */
    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking) {
        this(question, conversationId, taskId, userId, deepThinking, ChatMode.RAG, null, null);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
