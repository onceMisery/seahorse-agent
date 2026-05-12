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
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRagPromptAdapterTests {

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

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getRole()).isEqualTo(ChatRole.SYSTEM);
        assertThat(messages.get(0).getContent()).contains("知识库上下文", "Seahorse 支持可插拔适配器", "工具上下文");
        assertThat(messages.get(1).getContent()).isEqualTo("历史回答");
        assertThat(messages.get(2).getContent()).contains("Seahorse 支持什么？");
    }
}
