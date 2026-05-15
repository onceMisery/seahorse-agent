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

package com.miracle.ai.seahorse.agent.adapters.local;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.PromptContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 本地 RAG Prompt 组装适配器。
 *
 * <p>该实现把 KB/MCP 检索上下文写入 system message，并保留会话历史，确保原生内核默认路径具备 RAG 回答能力。
 */
public class LocalRagPromptAdapter implements RagPromptPort {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个严谨的知识库问答助手。请优先依据给定上下文回答用户问题。
            如果上下文不足以回答，请明确说明无法从当前知识库中确认，不要编造事实。
            回答应简洁、准确，并保持用户提问的语言。
            """;
    private static final String KB_CONTEXT_TITLE = "知识库上下文：";
    private static final String MCP_CONTEXT_TITLE = "工具上下文：";
    private static final String QUESTION_TITLE = "用户问题：";
    private static final String MEMORY_CONTEXT_TITLE = "用户记忆上下文：";
    private static final String MEMORY_CONFLICT_NOTE =
            "注意：若用户记忆与知识库上下文冲突，以知识库上下文为准，除非问题明确询问用户偏好或历史。";
    private static final int MAX_MEMORY_ITEM_LENGTH = 200;

    @Override
    public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                     List<ChatMessage> history,
                                                     String question,
                                                     List<String> subQuestions) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(context)));
        messages.addAll(safeHistory(history));
        messages.add(ChatMessage.user(buildUserPrompt(question, subQuestions)));
        return messages;
    }

    private String buildSystemPrompt(PromptContext context) {
        StringBuilder builder = new StringBuilder(DEFAULT_SYSTEM_PROMPT.trim());
        String kbContext = context == null ? "" : Objects.requireNonNullElse(context.getKbContext(), "");
        String mcpContext = context == null ? "" : Objects.requireNonNullElse(context.getMcpContext(), "");
        appendContext(builder, KB_CONTEXT_TITLE, kbContext);
        appendContext(builder, MCP_CONTEXT_TITLE, mcpContext);
        appendMemoryContext(builder, context);
        return builder.toString();
    }

    private void appendMemoryContext(StringBuilder builder, PromptContext context) {
        if (context == null || !context.hasMemory()) {
            return;
        }
        MemoryContext memory = context.getMemoryContext();
        StringBuilder memoryBuilder = new StringBuilder();

        appendMemoryLayer(memoryBuilder, "用户画像：", memory.getSemanticMemories());
        appendMemoryLayer(memoryBuilder, "长期记忆：", memory.getLongTermMemories());
        appendMemoryLayer(memoryBuilder, "近期记忆：", memory.getShortTermMemories());

        if (memoryBuilder.isEmpty()) {
            return;
        }
        builder.append("\n\n")
                .append(MEMORY_CONTEXT_TITLE)
                .append("\n")
                .append(memoryBuilder)
                .append("\n")
                .append(MEMORY_CONFLICT_NOTE);
    }

    private void appendMemoryLayer(StringBuilder builder, String title, List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(title);
        for (MemoryItem item : items) {
            String content = truncate(item.getContent(), MAX_MEMORY_ITEM_LENGTH);
            if (content.isBlank()) {
                continue;
            }
            builder.append("\n- ").append(content);
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen) + "...";
    }

    private String buildUserPrompt(String question, List<String> subQuestions) {
        StringBuilder builder = new StringBuilder(QUESTION_TITLE)
                .append(Objects.requireNonNullElse(question, "").trim());
        List<String> safeSubQuestions = Objects.requireNonNullElse(subQuestions, List.of());
        if (!safeSubQuestions.isEmpty()) {
            builder.append("\n拆分问题：").append(String.join("；", safeSubQuestions));
        }
        return builder.toString();
    }

    private void appendContext(StringBuilder builder, String title, String context) {
        if (context == null || context.isBlank()) {
            return;
        }
        builder.append("\n\n")
                .append(title)
                .append("\n")
                .append(context.trim());
    }

    private List<ChatMessage> safeHistory(List<ChatMessage> history) {
        return Objects.requireNonNullElse(history, List.<ChatMessage>of()).stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
