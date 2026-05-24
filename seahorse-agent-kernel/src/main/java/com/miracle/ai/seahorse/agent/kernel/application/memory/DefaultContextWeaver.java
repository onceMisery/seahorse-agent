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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultContextWeaver implements ContextWeaverPort {

    private static final String MEMORY_CONTEXT_TITLE = "用户记忆上下文：";
    private static final String MEMORY_CONFLICT_NOTE =
            "注意：Correction Ledger 和 Profile KV 是用户画像强事实源；业务知识库事实优先于普通历史记忆。";
    private static final int MAX_MEMORY_ITEM_LENGTH = 220;
    private static final String CONTEXT_PACK_TITLE = "ContextPack context:";
    private static final String CONTEXT_PACK_NOTE =
            "Use only these ContextPack items as authorized runtime context; keep citations and ACL decisions traceable.";
    private static final int MAX_CONTEXT_ITEM_LENGTH = 320;
    private static final String COMPONENT = "memory-context-weaver";
    static final String OBSERVATION_WEAVE_EVENT = "memory-context-weave";
    static final String OBSERVATION_ATTR_OUTCOME = "outcome";
    static final String OBSERVATION_ATTR_TRUNCATED = "truncated";
    static final String OBSERVATION_OUTCOME_SUCCESS = "success";

    private final MemoryTraceRecorder traceRecorder;
    private final ObservationPort observationPort;

    public DefaultContextWeaver() {
        this(MemoryTraceRecorder.noop());
    }

    public DefaultContextWeaver(MemoryTraceRecorder traceRecorder) {
        this(traceRecorder, ObservationPort.noop());
    }

    public DefaultContextWeaver(MemoryTraceRecorder traceRecorder, ObservationPort observationPort) {
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
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
        String prompt = builder.build();
        recordTrace(context, safeBudget, builder, prompt);
        return prompt;
    }

    @Override
    public String weave(ContextPack contextPack, ContextBudget budget) {
        if (!hasContextPackItems(contextPack)) {
            return "";
        }
        ContextBudget safeBudget = Objects.requireNonNullElseGet(budget, ContextBudget::defaults);
        BudgetedBuilder builder = new BudgetedBuilder(safeBudget.maxItems(), safeBudget.maxChars());
        builder.appendHeader(CONTEXT_PACK_TITLE);
        for (ContextItem item : contextPack.items()) {
            appendContextItem(builder, item);
        }
        if (builder.itemCount == 0) {
            return "";
        }
        builder.appendFooter(CONTEXT_PACK_NOTE);
        return builder.build();
    }

    @Override
    public String weave(ContextPack contextPack, MemoryContext memoryContext, ContextBudget budget) {
        String contextPackText = weave(contextPack, budget);
        if (!contextPackText.isBlank()) {
            return contextPackText;
        }
        return weave(memoryContext, budget);
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

    private static boolean hasContextPackItems(ContextPack contextPack) {
        return contextPack != null && contextPack.items() != null && !contextPack.items().isEmpty();
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

    private static void appendContextItem(BudgetedBuilder builder, ContextItem item) {
        if (item == null || item.sensitivity() == ContextSensitivity.SECRET) {
            return;
        }
        String content = truncate(item.content(), MAX_CONTEXT_ITEM_LENGTH);
        if (content.isBlank()) {
            return;
        }
        builder.appendItem("[" + item.sourceType().name() + "]",
                content + " (" + contextItemProvenance(item) + ")");
    }

    private static String contextItemProvenance(ContextItem item) {
        StringBuilder summary = new StringBuilder();
        appendSummary(summary, "source", item.sourceType().name() + ":" + item.sourceId());
        appendSummary(summary, "sensitivity", item.sensitivity().name());
        appendSummary(summary, "aclDecision", item.aclDecisionId());
        appendSummary(summary, "citation", item.citationJson());
        return summary.toString();
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

    private static void appendSummary(StringBuilder summary, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!summary.isEmpty()) {
            summary.append(", ");
        }
        summary.append(label).append("=").append(value.trim());
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

    private void recordTrace(MemoryContext context,
                             ContextBudget budget,
                             BudgetedBuilder builder,
                             String prompt) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("promptChars", prompt.length());
        details.put("selectedItemCount", builder.itemCount);
        details.put("maxItems", budget.maxItems());
        details.put("maxChars", budget.maxChars());
        int correctionCount = safeItems(context.getCorrectionMemories()).size();
        int profileCount = safeItems(context.getProfileMemories()).size();
        int shortTermCount = safeItems(context.getShortTermMemories()).size();
        int businessDocumentCount = safeItems(context.getBusinessDocumentMemories()).size();
        int semanticCount = safeItems(context.getSemanticMemories()).size();
        int longTermCount = safeItems(context.getLongTermMemories()).size();
        details.put("correctionCount", correctionCount);
        details.put("profileCount", profileCount);
        details.put("shortTermCount", shortTermCount);
        details.put("businessDocumentCount", businessDocumentCount);
        details.put("semanticCount", semanticCount);
        details.put("longTermCount", longTermCount);
        traceRecorder.record(new MemoryTraceEvent(
                "",
                "default",
                Objects.requireNonNullElse(context.getUserId(), ""),
                Objects.requireNonNullElse(context.getConversationId(), ""),
                Objects.requireNonNullElse(context.getConversationId(), ""),
                COMPONENT,
                "weave",
                MemoryTraceEvent.STATUS_SUCCESS,
                Objects.requireNonNullElse(context.getConversationId(), ""),
                "memory-context",
                details,
                null));
        int totalInputItems = correctionCount + profileCount + shortTermCount
                + businessDocumentCount + semanticCount + longTermCount;
        emitWeaveMetric(builder.itemCount < totalInputItems);
    }

    private void emitWeaveMetric(boolean truncated) {
        try {
            observationPort.recordEvent(new ObservationEvent(
                    OBSERVATION_WEAVE_EVENT,
                    Instant.now(),
                    ObservationEvent.DEFAULT_AMOUNT,
                    Map.of(
                            OBSERVATION_ATTR_OUTCOME, OBSERVATION_OUTCOME_SUCCESS,
                            OBSERVATION_ATTR_TRUNCATED, Boolean.toString(truncated))));
        } catch (RuntimeException ignored) {
            // Observation emission is best-effort and must not change weave semantics.
        }
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
