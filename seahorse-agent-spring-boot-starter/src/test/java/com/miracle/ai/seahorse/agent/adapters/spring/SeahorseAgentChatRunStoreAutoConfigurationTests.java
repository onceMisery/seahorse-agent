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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationUsagePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
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
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void shouldWireCatalogBackedToolPolicyIntoAgentLoop() {
        contextRunner.withUserConfiguration(TestCatalogBackedPolicyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    RecordingCallback callback = new RecordingCallback();
                    context.getBean(ChatInboundPort.class).streamChat(new StreamChatCommand(
                            "Use memory write", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT),
                            callback);

                    assertThat(callback.awaitTerminal()).isTrue();
                    assertThat(callback.errors).isEmpty();
                    assertThat(callback.contents).containsExactly("Policy blocked");
                    CountingToolPort tool = context.getBean(CountingToolPort.class);
                    assertThat(tool.calls.get()).isZero();

                    InMemoryAgentRunRepository runRepository = context.getBean(InMemoryAgentRunRepository.class);
                    AgentRun run = runRepository.runs.values().iterator().next();
                    assertThat(runRepository.listSteps(run.runId()))
                            .filteredOn(step -> step.stepType() == AgentStepType.TOOL_CALL)
                            .singleElement()
                            .satisfies(step -> assertThat(step.errorMessage()).isEqualTo("TOOL_NOT_BOUND"));
                });
    }

    @Test
    void shouldWireToolInvocationUsageIntoCatalogBackedPolicy() {
        contextRunner.withUserConfiguration(TestCatalogBackedCallLimitPolicyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ToolPolicyRequest request = new ToolPolicyRequest(
                            "run-1",
                            "step-1",
                            "call-1",
                            "agent-1",
                            "version-1",
                            "tenant-1",
                            "user-1",
                            "agent-identity-1",
                            "memory-write",
                            Map.of(),
                            Map.of(),
                            "run-1:call-1",
                            List.of("memory-write"),
                            true);

                    PolicyDecision decision = context.getBean(ToolPolicyPort.class).decide(request);

                    assertThat(decision.effect()).isEqualTo(PolicyDecision.Effect.DENY);
                    assertThat(decision.reasonCode()).isEqualTo(ToolPolicyReasonCodes.TOOL_CALL_LIMIT_EXCEEDED);
                });
    }

    @Test
    void shouldWireApprovalRepositoryIntoToolGateway() {
        contextRunner.withUserConfiguration(TestApprovalGatewayConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ToolInvocationResult result = context.getBean(ToolGatewayPort.class)
                            .invoke(new ToolInvocationRequest(
                                    "run-1",
                                    "step-1",
                                    "call-1",
                                    "agent-1",
                                    "version-1",
                                    "tenant-1",
                                    "user-1",
                                    "agent-identity-1",
                                    "memory-write",
                                    Map.of("input", "forget this"),
                                    Map.of(),
                                    "run-1:call-1",
                                    List.of("memory-write")));

                    assertThat(result.success()).isFalse();
                    assertThat(result.error()).isEqualTo(ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED);
                    RecordingToolApprovalRequestRepository approvals =
                            context.getBean(RecordingToolApprovalRequestRepository.class);
                    assertThat(approvals.saved).singleElement().satisfies(approval -> {
                        assertThat(approval.status()).isEqualTo(ApprovalRequestStatus.PENDING);
                        assertThat(approval.runId()).isEqualTo("run-1");
                        assertThat(approval.toolId()).isEqualTo("memory-write");
                        assertThat(approval.requestedAt()).isEqualTo(FIXED_CLOCK.instant());
                    });
                    assertThat(context.getBean(CountingToolPort.class).calls.get()).isZero();
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

    @Configuration(proxyBeanMethods = false)
    static class TestCatalogBackedPolicyConfiguration {

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
        CountingToolPort countingToolPort() {
            return new CountingToolPort();
        }

        @Bean
        ToolRegistryPort toolRegistryPort(CountingToolPort toolPort) {
            return new SingleToolRegistry(toolPort);
        }

        @Bean
        ToolCatalogRepositoryPort toolCatalogRepositoryPort() {
            return new SingleToolCatalogRepository();
        }

        @Bean
        AgentToolBindingRepositoryPort agentToolBindingRepositoryPort() {
            return AgentToolBindingRepositoryPort.empty();
        }

        @Bean
        StreamingChatModelPort streamingChatModelPort() {
            return new ToolThenFinalStreamingChatModel();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestCatalogBackedCallLimitPolicyConfiguration {

        @Bean
        ToolCatalogRepositoryPort toolCatalogRepositoryPort() {
            return new SingleToolCatalogRepository();
        }

        @Bean
        AgentToolBindingRepositoryPort agentToolBindingRepositoryPort() {
            return new SingleAgentToolBindingRepository(new AgentToolBinding(
                    "binding-1",
                    "agent-1",
                    "version-1",
                    "memory-write",
                    2,
                    "{}",
                    "admin-1",
                    Instant.EPOCH));
        }

        @Bean
        ToolInvocationUsagePort toolInvocationUsagePort() {
            return (runId, agentId, versionId, toolId) -> 3L;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestApprovalGatewayConfiguration {

        @Bean
        Clock clock() {
            return FIXED_CLOCK;
        }

        @Bean
        CountingToolPort countingToolPort() {
            return new CountingToolPort();
        }

        @Bean
        ToolRegistryPort toolRegistryPort(CountingToolPort toolPort) {
            return new SingleToolRegistry(toolPort);
        }

        @Bean
        ToolPolicyPort toolPolicyPort() {
            return request -> PolicyDecision.approvalRequired("approval-1",
                    ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                    "Tool requires approval");
        }

        @Bean
        RecordingToolApprovalRequestRepository toolApprovalRequestRepositoryPort() {
            return new RecordingToolApprovalRequestRepository();
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

    private static final class ToolThenFinalStreamingChatModel implements StreamingChatModelPort {
        private int turns;

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            if (turns++ == 0) {
                callback.onContent("Need tool");
                toolCallCollector.onToolCalls(List.of(AgentToolCall.of(
                        "call-1",
                        "memory-write",
                        Map.of("content", "remember this"))));
            } else {
                callback.onContent("Policy blocked");
                toolCallCollector.onToolCalls(List.of());
            }
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

    private static final class CountingToolPort implements ToolPort {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
            calls.incrementAndGet();
            return ToolInvocationResult.ok("should-not-run");
        }
    }

    private static final class SingleToolRegistry implements ToolRegistryPort {
        private final CountingToolPort toolPort;

        private SingleToolRegistry(CountingToolPort toolPort) {
            this.toolPort = toolPort;
        }

        @Override
        public List<ToolDescriptor> listTools() {
            return List.of(new ToolDescriptor("memory-write", "Memory Write", "Write memory", "{}"));
        }

        @Override
        public Optional<ToolPort> find(String toolId) {
            return "memory-write".equals(toolId) ? Optional.of(toolPort) : Optional.empty();
        }
    }

    private static final class SingleToolCatalogRepository implements ToolCatalogRepositoryPort {
        private final ToolCatalogEntry entry = new ToolCatalogEntry(
                "memory-write",
                ToolProvider.BUILTIN,
                "Memory Write",
                "Write memory",
                "{}",
                null,
                ToolRiskLevel.MEDIUM,
                ToolActionType.WRITE,
                "MEMORY",
                "platform",
                true,
                false,
                Instant.EPOCH,
                Instant.EPOCH);

        @Override
        public void save(ToolCatalogEntry entry) {
        }

        @Override
        public Optional<ToolCatalogEntry> findById(String toolId) {
            return entry.toolId().equals(toolId) ? Optional.of(entry) : Optional.empty();
        }

        @Override
        public void setEnabled(String toolId, boolean enabled) {
        }
    }

    private static final class SingleAgentToolBindingRepository implements AgentToolBindingRepositoryPort {
        private final AgentToolBinding binding;

        private SingleAgentToolBindingRepository(AgentToolBinding binding) {
            this.binding = binding;
        }

        @Override
        public void saveBindings(String agentId, String versionId, List<AgentToolBinding> bindings) {
        }

        @Override
        public List<AgentToolBinding> listBindings(String agentId, String versionId) {
            return agentId.equals(binding.agentId()) && versionId.equals(binding.versionId())
                    ? List.of(binding)
                    : List.of();
        }

        @Override
        public Optional<AgentToolBinding> findBinding(String agentId, String versionId, String toolId) {
            if (agentId.equals(binding.agentId())
                    && versionId.equals(binding.versionId())
                    && toolId.equals(binding.toolId())) {
                return Optional.of(binding);
            }
            return Optional.empty();
        }
    }

    static final class RecordingToolApprovalRequestRepository implements ToolApprovalRequestRepositoryPort {
        private final List<ApprovalRequest> saved = new ArrayList<>();

        @Override
        public void save(ApprovalRequest request) {
            saved.add(request);
        }
    }
}
