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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;

import java.util.List;
import java.util.Objects;

public class DefaultContextWeaver implements ContextWeaverPort {

    private static final String MEMORY_CONTEXT_TITLE = "用户记忆上下文：";
    private static final String MEMORY_CONFLICT_NOTE =
            "注意：Correction Ledger 和 Profile KV 是用户画像强事实源；业务知识库事实优先于普通历史记忆。";
    private static final int MAX_MEMORY_ITEM_LENGTH = 220;

    public DefaultContextWeaver() {
    }

    @Override
    public String weave(MemoryContext context, ContextBudget budget) {
        if (!hasMemory(context)) {
            return "";
        }
        ContextBudget safeBudget = Objects.requireNonNullElseGet(budget, ContextBudget::defaults);
        BudgetedBuilder builder = new BudgetedBuilder(safeBudget.maxItems(), safeBudget.maxChars());
        builder.appendHeader(MEMORY_CONTEXT_TITLE);
        appendZone(builder, "[Correction Ledger]", context.getCorrectionMemories());
        appendZone(builder, "[Profile KV]", context.getProfileMemories());
        appendZone(builder, "[Short Window]", context.getShortTermMemories());
        appendZone(builder, "[Business Documents]", context.getBusinessDocumentMemories());
        appendZone(builder, "[Semantic Memory]", context.getSemanticMemories());
        appendZone(builder, "[Long-Term Episodic]", context.getLongTermMemories());
        builder.appendFooter(MEMORY_CONFLICT_NOTE);
        return builder.build();
    }

    private static boolean hasMemory(MemoryContext context) {
        return context != null
                && (!safeItems(context.getCorrectionMemories()).isEmpty()
                || !safeItems(context.getProfileMemories()).isEmpty()
                || !safeItems(context.getShortTermMemories()).isEmpty()
                || !safeItems(context.getBusinessDocumentMemories()).isEmpty()
                || !safeItems(context.getSemanticMemories()).isEmpty()
                || !safeItems(context.getLongTermMemories()).isEmpty());
    }

    private static void appendZone(BudgetedBuilder builder, String title, List<MemoryItem> items) {
        for (MemoryItem item : safeItems(items)) {
            String content = decorateWithMetadata(truncate(item.getContent(), MAX_MEMORY_ITEM_LENGTH),
                    item.getMetadataJson());
            if (!content.isBlank()) {
                builder.appendItem(title, content);
            }
        }
    }

    private static List<MemoryItem> safeItems(List<MemoryItem> items) {
        return Objects.requireNonNullElse(items, List.of());
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen) + "...";
    }

    private static String decorateWithMetadata(String content, String metadataJson) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String metadata = metadataSummary(metadataJson);
        if (metadata.isBlank()) {
            return content.trim();
        }
        return content.trim() + " (" + metadata + ")";
    }

    private static String metadataSummary(String metadataJson) {
        StringBuilder summary = new StringBuilder();
        appendMetadata(summary, "profileSlot", "slot", metadataJson);
        appendMetadata(summary, "targetKey", "target", metadataJson);
        appendMetadata(summary, "docId", "docId", metadataJson);
        appendMetadata(summary, "version", "version", metadataJson);
        appendMetadata(summary, "generationId", "generationId", metadataJson);
        appendMetadata(summary, "sourceType", "source", metadataJson);
        return summary.toString();
    }

    private static void appendMetadata(StringBuilder summary, String key, String label, String metadataJson) {
        String value = metadataValue(metadataJson, key);
        if (value.isBlank()) {
            return;
        }
        if (!summary.isEmpty()) {
            summary.append(", ");
        }
        summary.append(label).append("=").append(value);
    }

    private static String metadataValue(String metadataJson, String key) {
        if (metadataJson == null || metadataJson.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        String compactPrefix = "\"" + key + "\":\"";
        int compactStart = metadataJson.indexOf(compactPrefix);
        if (compactStart >= 0) {
            int valueStart = compactStart + compactPrefix.length();
            int valueEnd = metadataJson.indexOf('"', valueStart);
            return valueEnd > valueStart ? metadataJson.substring(valueStart, valueEnd) : "";
        }
        String spacedPrefix = "\"" + key + "\": \"";
        int spacedStart = metadataJson.indexOf(spacedPrefix);
        if (spacedStart >= 0) {
            int valueStart = spacedStart + spacedPrefix.length();
            int valueEnd = metadataJson.indexOf('"', valueStart);
            return valueEnd > valueStart ? metadataJson.substring(valueStart, valueEnd) : "";
        }
        return "";
    }

    private static final class BudgetedBuilder {
        private final StringBuilder builder = new StringBuilder();
        private final int maxItems;
        private final int maxChars;
        private int itemCount;
        private String currentZone = "";

        private BudgetedBuilder(int maxItems, int maxChars) {
            this.maxItems = maxItems;
            this.maxChars = maxChars;
        }

        private void appendHeader(String header) {
            appendRaw(header);
        }

        private void appendFooter(String footer) {
            if (builder.isEmpty() || footer == null || footer.isBlank()) {
                return;
            }
            appendRaw("\n" + footer.trim());
        }

        private void appendItem(String zone, String content) {
            if (itemCount >= maxItems || content == null || content.isBlank()) {
                return;
            }
            StringBuilder candidate = new StringBuilder();
            if (!Objects.equals(currentZone, zone)) {
                candidate.append(builder.isEmpty() ? "" : "\n").append(zone);
            }
            candidate.append("\n- ").append(content.trim());
            if (!fits(candidate.toString())) {
                return;
            }
            builder.append(candidate);
            currentZone = zone;
            itemCount++;
        }

        private void appendRaw(String text) {
            if (text == null || text.isBlank() || !fits(text)) {
                return;
            }
            builder.append(text);
        }

        private boolean fits(String candidate) {
            return builder.length() + candidate.length() <= maxChars;
        }

        private String build() {
            return builder.toString();
        }
    }
}
