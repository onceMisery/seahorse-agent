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

package com.miracle.ai.seahorse.agent.adapters.ai.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionFragment;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LlmMemoryCompactionSummarizerAdapterTests {

    @Test
    void shouldParseValidCompactionSummary() {
        CapturingChatModelPort chatModelPort = new CapturingChatModelPort("""
                {
                  "content": "User's project uses Java 17 and is tuning Dubbo consumer rejection policy.",
                  "strategy": "llm:fact_compaction",
                  "metadata": {
                    "confidenceLevel": 0.86,
                    "sourceCount": 2
                  }
                }
                """);
        LlmMemoryCompactionSummarizerAdapter adapter = new LlmMemoryCompactionSummarizerAdapter(
                chatModelPort, PromptTemplatePort.empty(), new ObjectMapper());

        MemoryCompactionSummary summary = adapter.summarize(candidate());

        assertThat(summary.content())
                .isEqualTo("User's project uses Java 17 and is tuning Dubbo consumer rejection policy.");
        assertThat(summary.strategy()).isEqualTo("llm:fact_compaction");
        assertThat(summary.metadata()).containsEntry("confidenceLevel", 0.86D)
                .containsEntry("sourceCount", 2);
        String prompt = chatModelPort.lastRequest.get().getMessages().get(0).getContent();
        assertThat(prompt).contains("project.runtime")
                .contains("mem-1")
                .contains("User's project runs on Java 17.");
    }

    @Test
    void shouldExtractFencedJson() {
        LlmMemoryCompactionSummarizerAdapter adapter = new LlmMemoryCompactionSummarizerAdapter(
                new CapturingChatModelPort("""
                        ```json
                        {
                          "content": "Compacted project runtime facts.",
                          "strategy": "llm:fact_compaction",
                          "metadata": {"confidenceLevel": 0.8}
                        }
                        ```
                        """),
                PromptTemplatePort.empty(),
                new ObjectMapper());

        MemoryCompactionSummary summary = adapter.summarize(candidate());

        assertThat(summary.content()).isEqualTo("Compacted project runtime facts.");
        assertThat(summary.strategy()).isEqualTo("llm:fact_compaction");
    }

    @Test
    void shouldReturnEmptySummaryWhenModelOutputIsInvalidJson() {
        LlmMemoryCompactionSummarizerAdapter adapter = new LlmMemoryCompactionSummarizerAdapter(
                new CapturingChatModelPort("not json"),
                PromptTemplatePort.empty(),
                new ObjectMapper());

        MemoryCompactionSummary summary = adapter.summarize(candidate());

        assertThat(summary.content()).isBlank();
        assertThat(summary.strategy()).isBlank();
        assertThat(summary.metadata()).isEmpty();
    }

    @Test
    void shouldReturnEmptySummaryWhenContentIsMissing() {
        LlmMemoryCompactionSummarizerAdapter adapter = new LlmMemoryCompactionSummarizerAdapter(
                new CapturingChatModelPort("""
                        {
                          "strategy": "llm:fact_compaction",
                          "metadata": {"confidenceLevel": 0.8}
                        }
                        """),
                PromptTemplatePort.empty(),
                new ObjectMapper());

        MemoryCompactionSummary summary = adapter.summarize(candidate());

        assertThat(summary.content()).isBlank();
        assertThat(summary.strategy()).isBlank();
        assertThat(summary.metadata()).isEmpty();
    }

    private static MemoryCompactionCandidate candidate() {
        return new MemoryCompactionCandidate(
                "user-1",
                "tenant-1",
                "project.runtime",
                "same-target-key",
                List.of(
                        new MemoryCompactionFragment(
                                "mem-1",
                                "LONG_TERM",
                                "PROJECT_FACT",
                                "User's project runs on Java 17.",
                                Map.of("targetKey", "project.runtime"),
                                Instant.parse("2026-05-20T10:00:00Z")),
                        new MemoryCompactionFragment(
                                "mem-2",
                                "LONG_TERM",
                                "PROJECT_FACT",
                                "User is tuning Dubbo consumer rejection policy.",
                                Map.of("targetKey", "project.runtime"),
                                Instant.parse("2026-05-21T10:00:00Z"))));
    }

    private static final class CapturingChatModelPort implements ChatModelPort {

        private final String response;
        private final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();

        private CapturingChatModelPort(String response) {
            this.response = response;
        }

        @Override
        public String chat(ChatRequest request, String modelId) {
            lastRequest.set(request);
            return response;
        }
    }
}
