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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;

import java.util.List;
import java.util.Objects;

/**
 * Formats activated user memory for model system prompts.
 */
public final class MemoryPromptFormatter {

    private static final String MEMORY_CONTEXT_TITLE = "用户记忆上下文：";
    private static final String MEMORY_CONFLICT_NOTE =
            "注意：若用户记忆与知识库上下文冲突，以知识库上下文为准，除非问题明确询问用户偏好或历史。";
    private static final int MAX_MEMORY_ITEM_LENGTH = 200;

    private MemoryPromptFormatter() {
    }

    public static String appendToSystemPrompt(String systemPrompt, MemoryContext memoryContext) {
        String memoryText = format(memoryContext);
        if (memoryText.isBlank()) {
            return systemPrompt;
        }
        String safeSystemPrompt = Objects.requireNonNullElse(systemPrompt, "").trim();
        if (safeSystemPrompt.isBlank()) {
            return memoryText;
        }
        return safeSystemPrompt + "\n\n" + memoryText;
    }

    public static String format(MemoryContext memoryContext) {
        if (!hasMemory(memoryContext)) {
            return "";
        }
        StringBuilder memoryBuilder = new StringBuilder();
        appendMemoryLayer(memoryBuilder, "用户纠错本：", memoryContext.getCorrectionMemories());
        appendMemoryLayer(memoryBuilder, "用户画像：", memoryContext.getProfileMemories());
        appendMemoryLayer(memoryBuilder, "用户画像：", memoryContext.getSemanticMemories());
        appendMemoryLayer(memoryBuilder, "业务文档：", memoryContext.getBusinessDocumentMemories());
        appendMemoryLayer(memoryBuilder, "长期记忆：", memoryContext.getLongTermMemories());
        appendMemoryLayer(memoryBuilder, "近期记忆：", memoryContext.getShortTermMemories());
        if (memoryBuilder.isEmpty()) {
            return "";
        }
        return MEMORY_CONTEXT_TITLE
                + "\n"
                + memoryBuilder
                + "\n"
                + MEMORY_CONFLICT_NOTE;
    }

    private static boolean hasMemory(MemoryContext memoryContext) {
        return memoryContext != null
                && (!safeMemoryItems(memoryContext.getCorrectionMemories()).isEmpty()
                || !safeMemoryItems(memoryContext.getProfileMemories()).isEmpty()
                || !safeMemoryItems(memoryContext.getShortTermMemories()).isEmpty()
                || !safeMemoryItems(memoryContext.getBusinessDocumentMemories()).isEmpty()
                || !safeMemoryItems(memoryContext.getLongTermMemories()).isEmpty()
                || !safeMemoryItems(memoryContext.getSemanticMemories()).isEmpty());
    }

    private static void appendMemoryLayer(StringBuilder builder, String title, List<MemoryItem> items) {
        List<MemoryItem> safeItems = safeMemoryItems(items);
        if (safeItems.isEmpty()) {
            return;
        }
        StringBuilder layerBuilder = new StringBuilder(title);
        for (MemoryItem item : safeItems) {
            String content = truncate(item.getContent(), MAX_MEMORY_ITEM_LENGTH);
            if (content.isBlank()) {
                continue;
            }
            layerBuilder.append("\n- ").append(content);
        }
        if (layerBuilder.length() == title.length()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(layerBuilder);
    }

    private static List<MemoryItem> safeMemoryItems(List<MemoryItem> items) {
        return Objects.requireNonNullElse(items, List.of());
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen) + "...";
    }
}
