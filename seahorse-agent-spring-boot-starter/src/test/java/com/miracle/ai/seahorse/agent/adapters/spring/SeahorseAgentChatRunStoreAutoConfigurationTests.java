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

import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentChatRunStoreAutoConfigurationTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("seahorse-agent.chat.agent-mode-enabled=true")
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelAutoConfiguration.class));

    @Test
    void shouldWireRunStoreIntoAgentChatExecutionPath() {
        contextRunner.withUserConfiguration(TestAgentRunStoreConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AgentRunStepRecorder.class);
                    assertThat(context).getBean(AgentRunStepRecorder.class)
                            .isInstanceOf(RepositoryAgentRunStepRecorder.class);

                    RecordingCallback callback = new RecordingCallback();
                    context.getBean(ChatInboundPort.class).streamChat(new StreamChatCommand(
                            "Hello agent", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT), callback);

                    assertThat(callback.awaitTerminal()).isTrue();
                    assertThat(callback.errors).isEmpty();
                    assertThat(callback.contents).containsExactly("Agent answer");
                    InMemoryAgentRunRepository runRepository = context.getBean(InMemoryAgentRunRepository.class);
                    assertThat(runRepository.runs).hasSize(1);
                    AgentRun run = runRepository.runs.values().iterator().next();
                    assertThat(run.agentId()).isEqualTo("legacy-react-agent");
                    assertThat(run.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
                    assertThat(run.conversationId()).isEqualTo("conversation-1");
                    assertThat(runRepository.listSteps(run.runId()))
                            .extracting(AgentStep::stepType)
                            .containsExactly(AgentStepType.MODEL_TURN);
                });
    }

    @Test
    void shouldKeepAgentChatAvailableWithNoopRunStoreWhenRepositoryIsMissing() {
        contextRunner.withUserConfiguration(TestNoRunStoreConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AgentRunStepRecorder.class);
                    assertThat(context).doesNotHaveBean(AgentRunInboundPort.class);
                    assertThat(context).doesNotHaveBean(AgentRunRepositoryPort.class);
                    assertThat(context).getBean(AgentRunStepRecorder.class)
                            .isNotInstanceOf(RepositoryAgentRunStepRecorder.class);

                    RecordingCallback callback = new RecordingCallback();
                    context.getBean(ChatInboundPort.class).streamChat(new StreamChatCommand(
                            "Hello agent", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT), callback);

                    assertThat(callback.awaitTerminal()).isTrue();
                    assertThat(callback.errors).isEmpty();
                    assertThat(callback.contents).containsExactly("Agent answer");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestAgentRunStoreConfiguration {

        @Bean
        Clock clock() {
            return FIXED_CLOCK;
        }

        @Bean
        CurrentUserPort currentUserPort() {
            return () -> Optional.of(new CurrentUser("user-1", "alice", "user", null));
        }

        @Bean
        AgentDefinitionRepositoryPort agentDefinitionRepositoryPort() {
            return new EmptyAgentDefinitionRepository();
        }

        @Bean
        InMemoryAgentRunRepository agentRunRepositoryPort() {
            return new InMemoryAgentRunRepository();
        }

        @Bean
        ToolRegistryPort toolRegistryPort() {
            return ToolRegistryPort.empty();
        }

        @Bean
        StreamingChatModelPort streamingChatModelPort() {
            return new SingleTurnStreamingChatModel();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestNoRunStoreConfiguration {

        @Bean
        ToolRegistryPort toolRegistryPort() {
            return ToolRegistryPort.empty();
        }

        @Bean
        StreamingChatModelPort streamingChatModelPort() {
            return new SingleTurnStreamingChatModel();
        }
    }

    private static final class SingleTurnStreamingChatModel implements StreamingChatModelPort {

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            callback.onContent("Agent answer");
            toolCallCollector.onToolCalls(List.of());
            callback.onComplete();
            return () -> {
            };
        }
    }

    private static final class RecordingCallback implements StreamCallback {
        private final CountDownLatch terminal = new CountDownLatch(1);
        private final List<String> contents = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onContent(String content) {
            contents.add(content);
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
            terminal.countDown();
        }

        private boolean awaitTerminal() {
            try {
                return terminal.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static final class EmptyAgentDefinitionRepository implements AgentDefinitionRepositoryPort {

        @Override
        public void create(AgentDefinition definition) {
        }

        @Override
        public void update(AgentDefinition definition) {
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return Optional.empty();
        }

        @Override
        public AgentDefinitionPage page(String tenantId, long current, long size, String keyword) {
            return new AgentDefinitionPage(List.of(), 0, size, current, 0);
        }

        @Override
        public long nextVersionNo(String agentId) {
            return 1L;
        }

        @Override
        public void saveVersion(AgentVersion version) {
        }

        @Override
        public Optional<AgentVersion> latestVersion(String agentId) {
            return Optional.empty();
        }
    }

    static final class InMemoryAgentRunRepository implements AgentRunRepositoryPort {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();
        private final List<AgentStep> steps = new ArrayList<>();

        @Override
        public void createRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public void updateRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public void appendStep(AgentStep step) {
            steps.add(step);
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            return steps.stream()
                    .filter(step -> runId.equals(step.runId()))
                    .toList();
        }
    }
}
