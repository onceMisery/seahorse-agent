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
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPromptFormatterTests {

    @Test
    void shouldLabelEachMemoryTrackWithDistinctTitle() {
        MemoryContext context = MemoryContext.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .correctionMemories(List.of(item("correction-1", MemoryLayer.LONG_TERM, "用户喜欢简体中文，不要用繁体")))
                .profileMemories(List.of(item("profile-1", MemoryLayer.LONG_TERM, "用户姓名是张三")))
                .semanticMemories(List.of(item("semantic-1", MemoryLayer.SEMANTIC, "Pulsar PIP-459 与 Java 客户端兼容")))
                .businessDocumentMemories(List.of(item("doc-1", MemoryLayer.SEMANTIC, "公司差旅政策第 3 条")))
                .longTermMemories(List.of(item("long-1", MemoryLayer.LONG_TERM, "用户长期关注 K8s 拓扑变化")))
                .shortTermMemories(List.of(item("short-1", MemoryLayer.SHORT_TERM, "上一轮讨论了 leader election")))
                .workingMemory(List.of())
                .promptMessages(List.of())
                .build();

        String prompt = MemoryPromptFormatter.format(context);

        assertThat(prompt).contains("用户记忆上下文：");
        assertThat(prompt).contains("用户纠错本：");
        assertThat(prompt).contains("用户画像：");
        assertThat(prompt).contains("语义记忆：");
        assertThat(prompt).contains("业务文档：");
        assertThat(prompt).contains("长期记忆：");
        assertThat(prompt).contains("近期记忆：");
        // 关键回归点：semantic memories 必须用"语义记忆"标题，而不是 v1 误标的"用户画像"。
        assertThat(indexOf(prompt, "用户画像：")).isLessThan(indexOf(prompt, "语义记忆："));
    }

    @Test
    void shouldRenderSemanticMemoryUnderSemanticTitleOnly() {
        MemoryContext context = MemoryContext.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .correctionMemories(List.of())
                .profileMemories(List.of())
                .semanticMemories(List.of(item("semantic-only", MemoryLayer.SEMANTIC, "项目使用六边形架构")))
                .businessDocumentMemories(List.of())
                .longTermMemories(List.of())
                .shortTermMemories(List.of())
                .workingMemory(List.of())
                .promptMessages(List.of())
                .build();

        String prompt = MemoryPromptFormatter.format(context);

        assertThat(prompt).contains("语义记忆：");
        assertThat(prompt).contains("项目使用六边形架构");
        assertThat(prompt).doesNotContain("用户画像：");
    }

    @Test
    void shouldReturnEmptyWhenAllTracksAreEmpty() {
        MemoryContext context = MemoryContext.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .correctionMemories(List.of())
                .profileMemories(List.of())
                .semanticMemories(List.of())
                .businessDocumentMemories(List.of())
                .longTermMemories(List.of())
                .shortTermMemories(List.of())
                .workingMemory(List.of())
                .promptMessages(List.of())
                .build();

        assertThat(MemoryPromptFormatter.format(context)).isEmpty();
    }

    @Test
    void shouldAppendMemoryBlockAfterExistingSystemPrompt() {
        MemoryContext context = MemoryContext.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .correctionMemories(List.of())
                .profileMemories(List.of(item("profile-1", MemoryLayer.LONG_TERM, "用户喜欢简洁回答")))
                .semanticMemories(List.of())
                .businessDocumentMemories(List.of())
                .longTermMemories(List.of())
                .shortTermMemories(List.of())
                .workingMemory(List.of())
                .promptMessages(List.of())
                .build();

        String result = MemoryPromptFormatter.appendToSystemPrompt("You are a helpful agent.", context);

        assertThat(result).startsWith("You are a helpful agent.");
        assertThat(result).contains("用户记忆上下文：");
        assertThat(result).contains("用户画像：");
    }

    private static MemoryItem item(String id, MemoryLayer layer, String content) {
        return MemoryItem.builder()
                .id(id)
                .userId("user-1")
                .layer(layer)
                .content(content)
                .relevanceScore(0.7D)
                .build();
    }

    private static int indexOf(String haystack, String needle) {
        int index = haystack.indexOf(needle);
        assertThat(index).as("expected to find '%s' in formatted prompt", needle).isNotNegative();
        return index;
    }
}
