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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.adapters.ai.openai.MockEmbeddingAdapter;
import com.miracle.ai.seahorse.agent.adapters.ai.openai.OpenAiCompatibleModelAdapter;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentAiAdapterAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentAiAdapterAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void shouldConfigureMockEmbeddingDimensionFromVectorDimension() {
        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.ai.type=mock",
                        "seahorse.agent.adapters.vector.dimension=1024")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmbeddingModelPort.class);
                    assertThat(context.getBean(EmbeddingModelPort.class)).isInstanceOf(MockEmbeddingAdapter.class);
                    assertThat(context.getBean(EmbeddingModelPort.class).embed("mock", "knowledge chunk"))
                            .hasSize(1024);
                });
    }

    @Test
    void shouldConfigureMockEmbeddingFromCanonicalAdapterProperties() {
        contextRunner.withPropertyValues(
                        "seahorse-agent.adapters.ai.type=mock",
                        "seahorse-agent.adapters.vector.dimension=384")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmbeddingModelPort.class);
                    assertThat(context.getBean(EmbeddingModelPort.class)).isInstanceOf(MockEmbeddingAdapter.class);
                    assertThat(context.getBean(EmbeddingModelPort.class).embed("mock", "knowledge chunk"))
                            .hasSize(384);
                });
    }

    @Test
    void shouldConfigureMockEmbeddingDimensionFromEmbeddingModelWhenVectorDimensionIsUnset() {
        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.ai.type=mock",
                        "seahorse.agent.adapters.ai.embedding-model=bge-m3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmbeddingModelPort.class);
                    assertThat(context.getBean(EmbeddingModelPort.class).embed("mock", "knowledge chunk"))
                            .hasSize(1024);
                });
    }

    @Test
    void shouldAllowMockEmbeddingWithOpenAiCompatibleChat() {
        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.ai.type=openai-compatible",
                        "seahorse.agent.adapters.ai.embedding-type=mock",
                        "seahorse.agent.adapters.ai.base-url=https://apihub.agnes-ai.com/v1",
                        "seahorse.agent.adapters.ai.api-key=test-key",
                        "seahorse.agent.adapters.ai.chat-model=agnes-2.0-flash",
                        "seahorse.agent.adapters.ai.embedding.base-url=http://ollama:11434/v1",
                        "seahorse.agent.adapters.ai.mock.embedding-dimension=1024")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OpenAiCompatibleModelAdapter.class)).isNotNull();
                    assertThat(context.getBean(EmbeddingModelPort.class)).isInstanceOf(MockEmbeddingAdapter.class);
                    assertThat(context.getBean(EmbeddingModelPort.class).embed("local-mock", "knowledge chunk"))
                            .hasSize(1024);
                });
    }

    @Test
    void shouldConfigureMockStreamingChatModelForAgentRuntimeE2e() {
        contextRunner.withPropertyValues("seahorse.agent.adapters.ai.type=mock")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StreamingChatModelPort.class);
                    AtomicBoolean completed = new AtomicBoolean(false);
                    context.getBean(StreamingChatModelPort.class).streamChat(ChatRequest.builder()
                            .messages(List.of(ChatMessage.user("hello")))
                            .build(), new StreamCallback() {
                        @Override
                        public void onContent(String content) {
                        }

                        @Override
                        public void onComplete() {
                            completed.set(true);
                        }

                        @Override
                        public void onError(Throwable error) {
                        }
                    });
                    assertThat(completed).isTrue();
                });
    }

    @Test
    void shouldExposeMockStreamingDiagnosticsForSkillSelectionE2e() {
        contextRunner.withPropertyValues("seahorse.agent.adapters.ai.type=mock")
                .run(context -> {
                    StreamingChatModelPort model = context.getBean(StreamingChatModelPort.class);
                    AtomicReference<String> content = new AtomicReference<>("");
                    AtomicBoolean completed = new AtomicBoolean(false);

                    model.streamChatWithTools(ChatRequest.builder()
                            .messages(List.of(
                                    ChatMessage.system("<skills><skill name=\"codex-e2e-skill\" revision=\"rev-1\">"),
                                    ChatMessage.user("hello")))
                            .tools(List.of(new ToolDescriptor(
                                    "load_skill", "Load Skill", "Load a skill", "{}")))
                            .build(), new StreamCallback() {
                        @Override
                        public void onContent(String value) {
                            content.updateAndGet(current -> current + value);
                        }

                        @Override
                        public void onComplete() {
                            completed.set(true);
                        }

                        @Override
                        public void onError(Throwable error) {
                        }
                    }, calls -> assertThat(calls).isEmpty());

                    assertThat(completed).isTrue();
                    assertThat(content.get())
                            .contains("mock-streaming-chat")
                            .contains("skill=codex-e2e-skill")
                            .contains("tools=1");
                });
    }
}
