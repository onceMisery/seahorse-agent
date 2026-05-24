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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultContextWeaverContextPackTests {

    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void shouldWeaveContextPackWithProvenanceAndAclDecision() {
        DefaultContextWeaver weaver = new DefaultContextWeaver();

        String prompt = weaver.weave(contextPack(List.of(
                item("item-1", ContextItemSourceType.RAG_CHUNK, "refund policy requires approval",
                        ContextSensitivity.INTERNAL, "decision-doc-1", "{\"docId\":\"policy-1\"}"),
                item("item-2", ContextItemSourceType.MEMORY, "user prefers concise answers",
                        ContextSensitivity.CONFIDENTIAL, "decision-memory-1", "{\"memoryId\":\"mem-1\"}"))),
                ContextBudget.defaults());

        assertTrue(prompt.contains("ContextPack"));
        assertTrue(prompt.contains("refund policy requires approval"));
        assertTrue(prompt.contains("source=RAG_CHUNK:item-1"));
        assertTrue(prompt.contains("aclDecision=decision-doc-1"));
        assertTrue(prompt.contains("citation={\"docId\":\"policy-1\"}"));
        assertTrue(prompt.contains("user prefers concise answers"));
        assertTrue(prompt.contains("sensitivity=CONFIDENTIAL"));
    }

    @Test
    void shouldExcludeSecretItemsAndRespectBudget() {
        DefaultContextWeaver weaver = new DefaultContextWeaver();

        String prompt = weaver.weave(contextPack(List.of(
                item("public-1", ContextItemSourceType.RAG_CHUNK, "visible context",
                        ContextSensitivity.INTERNAL, "decision-1", "{\"docId\":\"doc-1\"}"),
                item("secret-1", ContextItemSourceType.MEMORY, "plain text secret",
                        ContextSensitivity.SECRET, "decision-2", "{\"memoryId\":\"mem-secret\"}"),
                item("public-2", ContextItemSourceType.TOOL_RESULT, "second visible context",
                        ContextSensitivity.PUBLIC, "decision-3", "{\"toolCallId\":\"tool-1\"}"))),
                new ContextBudget(1, 2000));

        assertTrue(prompt.contains("visible context"));
        assertFalse(prompt.contains("plain text secret"));
        assertFalse(prompt.contains("second visible context"));
    }

    @Test
    void shouldPreferContextPackOverLegacyMemoryWhenBothExist() {
        DefaultContextWeaver weaver = new DefaultContextWeaver();
        MemoryContext memoryContext = MemoryContext.builder()
                .profileMemories(List.of(MemoryItem.builder().content("legacy memory").build()))
                .build();

        String prompt = weaver.weave(contextPack(List.of(
                item("item-1", ContextItemSourceType.USER_INPUT, "context pack content",
                        ContextSensitivity.PUBLIC, "decision-1", "{\"inputId\":\"question\"}"))),
                memoryContext,
                ContextBudget.defaults());

        assertTrue(prompt.contains("context pack content"));
        assertFalse(prompt.contains("legacy memory"));
    }

    private static ContextPack contextPack(List<ContextItem> items) {
        return new ContextPack(
                "ctx-1",
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "answer user",
                400,
                items,
                NOW);
    }

    private static ContextItem item(String sourceId,
                                    ContextItemSourceType sourceType,
                                    String content,
                                    ContextSensitivity sensitivity,
                                    String aclDecisionId,
                                    String citationJson) {
        return new ContextItem(
                "item-" + sourceId,
                "ctx-1",
                sourceType,
                sourceId,
                content,
                null,
                0.9,
                0.8,
                sensitivity,
                aclDecisionId,
                citationJson,
                20,
                null,
                NOW);
    }
}
