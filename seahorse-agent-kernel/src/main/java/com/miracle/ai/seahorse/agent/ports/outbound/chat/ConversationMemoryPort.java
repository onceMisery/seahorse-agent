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

package com.miracle.ai.seahorse.agent.ports.outbound.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;

import java.util.List;

/**
 * 对话记忆端口。
 */
public interface ConversationMemoryPort {

    /**
     * 加载历史并追加当前用户消息。
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param message        用户消息
     * @return 追加前的历史消息
     */
    List<ChatMessage> loadAndAppend(String conversationId, String userId, ChatMessage message);

    /**
     * 追加单条会话消息。
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param message        待追加消息
     */
    default void append(String conversationId, String userId, ChatMessage message) {
    }

    /**
     * Append a conversation message with the agent run that produced it.
     */
    default void append(String conversationId, String userId, ChatMessage message, String agentRunId) {
        append(conversationId, userId, message);
    }

    static ConversationMemoryPort noop() {
        return (conversationId, userId, message) -> List.of();
    }
}
