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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHandoffContextPolicyTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldStripSecretPrivateAndRawToolResultContext() {
        ContextPack pack = new ContextPack(
                "context-pack-1",
                "parent-run-1",
                "source-agent",
                "version-1",
                "tenant-1",
                "user-1",
                "answer finance question",
                4000,
                List.of(
                        item("item-1", ContextItemSourceType.RAG_CHUNK, ContextSensitivity.INTERNAL,
                                "raw document body", "approved citation summary", "{\"resourceId\":\"doc-1\"}"),
                        item("item-2", ContextItemSourceType.MEMORY, ContextSensitivity.CONFIDENTIAL,
                                "private memory secret-token", "private memory summary", "{\"resourceId\":\"mem-1\"}"),
                        item("item-3", ContextItemSourceType.TOOL_RESULT, ContextSensitivity.INTERNAL,
                                "raw tool result payload", "tool result summary", "{\"resourceId\":\"tool-1\"}"),
                        item("item-4", ContextItemSourceType.RAG_CHUNK, ContextSensitivity.SECRET,
                                "secret-token", "secret summary", "{\"resourceId\":\"doc-secret\"}")),
                NOW);

        AgentHandoffContextSnapshot snapshot = AgentHandoffContextPolicy.defaults().reduce(pack);

        assertEquals(1, snapshot.transferredItemCount());
        assertEquals(3, snapshot.strippedItemCount());
        assertTrue(snapshot.summaryJson().contains("approved citation summary"));
        assertTrue(snapshot.citationJson().contains("doc-1"));
        assertFalse(snapshot.summaryJson().contains("raw document body"));
        assertFalse(snapshot.summaryJson().contains("secret-token"));
        assertFalse(snapshot.summaryJson().contains("private memory"));
        assertFalse(snapshot.summaryJson().contains("raw tool result"));
        assertFalse(snapshot.citationJson().contains("doc-secret"));
    }

    private static ContextItem item(String itemId,
                                    ContextItemSourceType sourceType,
                                    ContextSensitivity sensitivity,
                                    String content,
                                    String summary,
                                    String citationJson) {
        return new ContextItem(
                itemId,
                "context-pack-1",
                sourceType,
                itemId,
                content,
                summary,
                0.9d,
                0.8d,
                sensitivity,
                "decision-" + itemId,
                citationJson,
                100,
                null,
                NOW);
    }
}
