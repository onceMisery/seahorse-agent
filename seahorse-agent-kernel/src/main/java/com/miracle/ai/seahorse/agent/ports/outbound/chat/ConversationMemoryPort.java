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
     * Load the selected branch path and append the current user message under that leaf.
     *
     * @param conversationId       conversation ID
     * @param userId               user ID
     * @param message              user message to append
     * @param branchLeafMessageId  selected branch leaf message ID
     * @return history messages before append
     */
    default List<ChatMessage> loadAndAppend(
            String conversationId,
            String userId,
            ChatMessage message,
            Long branchLeafMessageId) {
        return loadAndAppend(conversationId, userId, message);
    }

    /**
     * Load the selected branch path without appending a new message.
     */
    default List<ChatMessage> loadBranchPath(String conversationId, String userId, Long branchLeafMessageId) {
        return List.of();
    }

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

    /**
     * Append a conversation message under a requested parent message.
     */
    default void append(
            String conversationId,
            String userId,
            ChatMessage message,
            String agentRunId,
            Long parentMessageId) {
        append(conversationId, userId, message, agentRunId);
    }

    static ConversationMemoryPort noop() {
        return (conversationId, userId, message) -> List.of();
    }
}
