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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * Seahorse 内核对话消息契约。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private ChatRole role;

    private String content;

    private String thinkingContent;

    private Integer thinkingDuration;

    private String toolCallId;

    private List<AgentToolCall> toolCalls;

    public ChatMessage(ChatRole role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ChatRole.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatRole.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatRole.ASSISTANT, content);
    }

    public static ChatMessage assistant(String content, String thinkingContent) {
        return assistant(content, thinkingContent, null);
    }

    public static ChatMessage assistant(String content, String thinkingContent, Integer thinkingDuration) {
        ChatMessage message = new ChatMessage(ChatRole.ASSISTANT, content);
        message.setThinkingContent(thinkingContent);
        message.setThinkingDuration(thinkingDuration);
        return message;
    }

    public static ChatMessage assistantToolCalls(String content, List<AgentToolCall> toolCalls) {
        ChatMessage message = new ChatMessage(ChatRole.ASSISTANT, content);
        message.setToolCalls(List.copyOf(Objects.requireNonNull(toolCalls, "toolCalls 不能为空")));
        return message;
    }

    public static ChatMessage tool(String toolCallId, String content) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("tool message 必须携带 toolCallId");
        }
        ChatMessage message = new ChatMessage(ChatRole.TOOL, content);
        message.setToolCallId(toolCallId);
        return message;
    }
}
