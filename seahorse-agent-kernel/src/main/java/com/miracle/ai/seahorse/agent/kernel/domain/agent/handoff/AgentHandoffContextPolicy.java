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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;

import java.util.List;
import java.util.Objects;

public final class AgentHandoffContextPolicy {

    private static final String DEFAULT_POLICY_ID = "default-local-handoff-context-policy";

    private AgentHandoffContextPolicy() {
    }

    public static AgentHandoffContextPolicy defaults() {
        return new AgentHandoffContextPolicy();
    }

    public AgentHandoffContextSnapshot reduce(ContextPack contextPack) {
        if (contextPack == null || contextPack.items().isEmpty()) {
            return new AgentHandoffContextSnapshot(
                    summaryJson(List.of(), 0),
                    citationJson(List.of()),
                    0,
                    contextPack == null ? 0 : contextPack.items().size());
        }
        List<ContextItem> transferable = contextPack.items().stream()
                .filter(this::canTransfer)
                .toList();
        int stripped = contextPack.items().size() - transferable.size();
        return new AgentHandoffContextSnapshot(
                summaryJson(transferable, stripped),
                citationJson(transferable),
                transferable.size(),
                stripped);
    }

    private boolean canTransfer(ContextItem item) {
        if (item == null) {
            return false;
        }
        if (item.sourceType() == ContextItemSourceType.TOOL_RESULT) {
            return false;
        }
        return item.sensitivity() == ContextSensitivity.PUBLIC
                || item.sensitivity() == ContextSensitivity.INTERNAL;
    }

    private String summaryJson(List<ContextItem> items, int stripped) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"policyId\":\"").append(DEFAULT_POLICY_ID).append("\",");
        builder.append("\"strippedItemCount\":").append(stripped).append(',');
        builder.append("\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            ContextItem item = items.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append("{\"itemId\":\"").append(escape(item.itemId())).append("\",");
            builder.append("\"sourceType\":\"").append(item.sourceType().name()).append("\",");
            builder.append("\"summary\":\"").append(escape(summaryOf(item))).append("\"}");
        }
        builder.append("]}");
        return truncate(builder.toString(), AgentHandoffLimits.CONTEXT_SUMMARY_MAX_LENGTH);
    }

    private String citationJson(List<ContextItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            ContextItem item = items.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append("{\"itemId\":\"").append(escape(item.itemId())).append("\",");
            builder.append("\"citation\":").append(item.citationJson()).append('}');
        }
        builder.append("]}");
        return truncate(builder.toString(), AgentHandoffLimits.CONTEXT_SUMMARY_MAX_LENGTH);
    }

    private String summaryOf(ContextItem item) {
        String summary = item.summary();
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return "Context item " + item.itemId();
    }

    private String escape(String value) {
        return Objects.requireNonNullElse(value, "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
