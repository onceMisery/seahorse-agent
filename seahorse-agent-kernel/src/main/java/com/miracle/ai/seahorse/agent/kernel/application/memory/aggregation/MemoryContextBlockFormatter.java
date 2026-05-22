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

package com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferSnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;

import java.util.Objects;

final class MemoryContextBlockFormatter {

    private static final String HEADER = "MEMORY_CONTEXT_BLOCK";
    private static final String VERSION = "v1";
    private static final String SECTION_TURNS = "turns";
    private static final String SECTION_SOURCE_SPANS = "source_spans";
    private static final String UNKNOWN_TIME = "";

    private MemoryContextBlockFormatter() {
    }

    static String format(MemoryBufferSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        StringBuilder builder = new StringBuilder();
        appendLine(builder, HEADER, VERSION);
        appendLine(builder, "snapshot_id", snapshot.snapshotId());
        appendLine(builder, "tenant_id", snapshot.tenantId());
        appendLine(builder, "user_id", snapshot.userId());
        appendLine(builder, "conversation_id", snapshot.conversationId());
        appendLine(builder, "session_id", snapshot.sessionId());
        appendLine(builder, "trigger", snapshot.trigger().name());
        appendLine(builder, "turn_count", String.valueOf(snapshot.turns().size()));
        appendLine(builder, "total_tokens", String.valueOf(snapshot.totalTokens()));
        appendLine(builder, "from", snapshot.from() == null ? UNKNOWN_TIME : snapshot.from().toString());
        appendLine(builder, "to", snapshot.to() == null ? UNKNOWN_TIME : snapshot.to().toString());
        builder.append("\n[").append(SECTION_TURNS).append("]\n");
        for (int i = 0; i < snapshot.turns().size(); i++) {
            appendTurn(builder, i + 1, snapshot.turns().get(i));
        }
        builder.append("\n[").append(SECTION_SOURCE_SPANS).append("]\n");
        for (int i = 0; i < snapshot.turns().size(); i++) {
            appendSourceSpan(builder, i + 1, snapshot.turns().get(i));
        }
        return builder.toString().trim();
    }

    private static void appendTurn(StringBuilder builder, int index, MemoryTurnEvent turn) {
        builder.append("turn_").append(index).append(":\n");
        appendLine(builder, "  user_message_id", turn.userMessageId());
        appendLine(builder, "  assistant_message_id", turn.assistantMessageId());
        appendLine(builder, "  completed_at", turn.completedAt().toString());
        appendLine(builder, "  estimated_tokens", String.valueOf(turn.estimatedTokens()));
        appendLine(builder, "  user", sanitizeMultiline(turn.userText()));
        appendLine(builder, "  assistant", sanitizeMultiline(turn.assistantText()));
    }

    private static void appendSourceSpan(StringBuilder builder, int index, MemoryTurnEvent turn) {
        builder.append("span_").append(index).append(": ")
                .append(turn.userMessageId())
                .append(" -> ")
                .append(turn.assistantMessageId())
                .append('\n');
    }

    private static void appendLine(StringBuilder builder, String key, String value) {
        builder.append(key).append(": ").append(Objects.requireNonNullElse(value, "")).append('\n');
    }

    private static String sanitizeMultiline(String value) {
        String text = Objects.requireNonNullElse(value, "").trim();
        if (text.isBlank()) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\n");
    }
}
