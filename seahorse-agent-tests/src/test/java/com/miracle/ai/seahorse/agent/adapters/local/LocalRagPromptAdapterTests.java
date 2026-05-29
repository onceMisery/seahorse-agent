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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.PromptContext;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRagPromptAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void shouldFormatRetrievedChunksIntoKbContext() {
        LocalRetrievalContextFormatAdapter adapter = new LocalRetrievalContextFormatAdapter();
        RetrievedChunk first = RetrievedChunk.builder().id("1").text("第一段内容").score(0.91F).build();
        RetrievedChunk second = RetrievedChunk.builder().id("2").text("第二段内容").score(0.82F).build();

        String context = adapter.formatKbContext(List.of(), Map.of("multi_channel", List.of(second, first)), 1);

        assertThat(context).contains("第一段内容");
        assertThat(context).doesNotContain("第二段内容");
    }

    @Test
    void shouldBuildRagMessagesWithContextAndHistory() {
        LocalRagPromptAdapter adapter = new LocalRagPromptAdapter();
        PromptContext context = PromptContext.builder()
                .kbContext("[1] Seahorse 支持可插拔适配器")
                .mcpContext("[1 tool=time] 当前时间")
                .build();

        List<ChatMessage> messages = adapter.buildStructuredMessages(
                context, List.of(ChatMessage.assistant("历史回答")), "Seahorse 支持什么？", List.of("Seahorse 支持什么"));

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).getRole()).isEqualTo(ChatRole.SYSTEM);
        assertThat(messages.get(0).getContent()).doesNotContain("知识库上下文", "Seahorse 支持可插拔适配器", "工具上下文");
        assertThat(messages.get(1).getRole()).isEqualTo(ChatRole.USER);
        assertThat(messages.get(1).getContent()).contains("知识库上下文", "Seahorse 支持可插拔适配器", "工具上下文");
        assertThat(messages.get(2).getContent()).isEqualTo("历史回答");
        assertThat(messages.get(3).getContent()).contains("Seahorse 支持什么？");
    }

    @Test
    void shouldBuildRagMessagesWithContextPackBeforeLegacyMemory() {
        LocalRagPromptAdapter adapter = new LocalRagPromptAdapter();
        PromptContext context = PromptContext.builder()
                .contextPack(contextPack("context pack policy evidence"))
                .memoryContext(MemoryContext.builder()
                        .profileMemories(List.of(MemoryItem.builder().content("legacy memory evidence").build()))
                        .build())
                .build();

        List<ChatMessage> messages = adapter.buildStructuredMessages(
                context, List.of(), "What is the policy?", List.of());

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getRole()).isEqualTo(ChatRole.SYSTEM);
        assertThat(messages.get(0).getContent()).doesNotContain("context pack policy evidence", "legacy memory evidence");
        assertThat(messages.get(1).getRole()).isEqualTo(ChatRole.USER);
        assertThat(messages.get(1).getContent()).contains("context pack policy evidence", "aclDecision=decision-1");
        assertThat(messages.get(1).getContent()).doesNotContain("legacy memory evidence");
    }

    @Test
    void shouldKeepRagSystemPromptStaticAndInjectRuntimeContextAfterIt() {
        LocalRagPromptAdapter adapter = new LocalRagPromptAdapter();
        PromptContext context = PromptContext.builder()
                .kbContext("[1] Seahorse 支持可插拔适配器")
                .mcpContext("[1 tool=time] 当前时间")
                .contextPack(contextPack("context pack policy evidence"))
                .build();

        List<ChatMessage> messages = adapter.buildStructuredMessages(
                context, List.of(ChatMessage.assistant("历史回答")), "Seahorse 支持什么？", List.of());

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).getRole()).isEqualTo(ChatRole.SYSTEM);
        assertThat(messages.get(0).getContent())
                .doesNotContain("知识库上下文", "工具上下文", "context pack policy evidence");
        assertThat(messages.get(1).getRole()).isEqualTo(ChatRole.USER);
        assertThat(messages.get(1).getContent())
                .contains("<runtime-context>", "知识库上下文", "工具上下文", "context pack policy evidence");
        assertThat(messages.get(2).getContent()).isEqualTo("历史回答");
    }

    private static ContextPack contextPack(String content) {
        return new ContextPack(
                "ctx-1",
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "answer user",
                400,
                List.of(new ContextItem(
                        "ctxi-1",
                        "ctx-1",
                        ContextItemSourceType.RAG_CHUNK,
                        "doc-1",
                        content,
                        null,
                        0.9,
                        0.8,
                        ContextSensitivity.INTERNAL,
                        "decision-1",
                        "{\"docId\":\"policy-1\"}",
                        20,
                        null,
                        NOW)),
                NOW);
    }
}
