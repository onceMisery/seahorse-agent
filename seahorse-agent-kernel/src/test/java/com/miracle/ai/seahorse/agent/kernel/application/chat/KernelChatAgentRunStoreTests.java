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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.application.agent.AgentLoopDependencies;
import com.miracle.ai.seahorse.agent.kernel.application.agent.InMemoryToolRegistry;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorRouter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.agent.task.KernelTaskTemplateQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.GetDateTimeToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.GitHubRepositoryReaderToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ImageGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.SearchKnowledgeBaseToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.WebFetchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.WebSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileResolvedPreview;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelChatAgentRunStoreTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldCreateAgentRunAndRecordModelAndToolStepsForAgentMode() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService runService = new KernelAgentRunService(
                new EmptyAgentDefinitionRepository(), runRepository,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);
        AgentToolCall toolCall = AgentToolCall.of("call-1", "weather", Map.of("city", "Shanghai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need weather", List.of(toolCall)),
                Turn.finalAnswer("Shanghai 21C")));
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{\"temp\":21}"));
        KernelAgentLoop agentLoop = agentLoop(
                model,
                toolRegistry,
                successfulWeatherGateway(),
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runService));

        service.streamChat(new StreamChatCommand(
                "What is the weather?", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(List.of("Shanghai 21C"), callback.contents);
        AgentRun run = runRepository.runs.values().iterator().next();
        assertEquals("legacy-react-agent", run.agentId());
        assertEquals(run.runId(), callback.runId);
        assertEquals(AgentRunStatus.SUCCEEDED, run.status());
        assertEquals("conversation-1", run.conversationId());
        List<AgentStep> steps = runRepository.listSteps(run.runId());
        assertEquals(3, steps.size());
        assertEquals(AgentStepType.MODEL_TURN, steps.get(0).stepType());
        assertEquals(AgentStepType.TOOL_CALL, steps.get(1).stepType());
        assertEquals(AgentStepType.MODEL_TURN, steps.get(2).stepType());
        assertEquals(AgentStepStatus.SUCCEEDED, steps.get(1).status());
        assertTrue(steps.get(1).inputJson().contains("weather"));
        assertTrue(steps.get(1).outputJson().contains("temp"));
    }

    @Test
    void shouldRecordAgentModeModelUsageForAgentRun() {
        AgentRun run = new AgentRun(
                "run-usage-1",
                "ops-agent",
                "ops-agent-v1",
                "rollout-1",
                "tenant-a",
                "alice",
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "Count tokens",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
        RecordingAgentRunInboundPort runPort = new RecordingAgentRunInboundPort(run);
        RecordingCostUsageRepository usageRepository = new RecordingCostUsageRepository();
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.create(agentDefinition("ops-agent", "ops-agent-v1"));
        definitionRepository.saveVersion(agentVersion("ops-agent", "ops-agent-v1", "{\"modelId\":\"ops-model\"}"));
        UsageEmittingAgentLoop agentLoop = new UsageEmittingAgentLoop(new ChatTokenUsage(12, 5));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runPort),
                Optional.empty(),
                Optional.of(definitionRepository),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.empty(),
                Optional.of(usageRepository));

        service.streamChat(new StreamChatCommand(
                "Count tokens", "conversation-1", "task-1", "alice", false,
                ChatMode.AGENT, "ops-agent", "ops-agent-v1"), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(List.of("answer"), callback.contents);
        assertEquals(1, usageRepository.records.size());
        CostUsageRecord record = usageRepository.records.get(0);
        assertEquals("tenant-a", record.tenantId());
        assertEquals("ops-agent", record.agentId());
        assertEquals("run-usage-1", record.runId());
        assertEquals("rollout-1", record.rolloutId());
        assertEquals("alice", record.userId());
        assertEquals("ops-model", record.modelId());
        assertEquals(CostUsageSource.MODEL, record.source());
        assertEquals(17L, record.tokens());
        assertEquals(1L, record.calls());
        assertEquals(0.0D, record.cost());
    }

    @Test
    void shouldSaveRunContextSnapshotWhenAgentRunStarts() {
        AgentRun run = new AgentRun(
                "run-context-1",
                "legacy-react-agent",
                null,
                null,
                "tenant-a",
                "user-1",
                "101",
                AgentRunTriggerType.CHAT,
                "Use tools",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
        RecordingAgentRunInboundPort runPort = new RecordingAgentRunInboundPort(run);
        UsageEmittingAgentLoop agentLoop = new UsageEmittingAgentLoop(new ChatTokenUsage(0, 0));
        RecordingRunContextSnapshotRepository snapshotRepository = new RecordingRunContextSnapshotRepository();
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runPort),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.of(snapshotRepository),
                List.of());

        service.streamChat(new StreamChatCommand(
                "Use tools",
                "101",
                "task-1",
                "user-1",
                false,
                ChatMode.AGENT,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of("kb-1"),
                99L), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(1, snapshotRepository.records.size());
        RunContextSnapshotRecord snapshot = snapshotRepository.records.get(0);
        assertEquals("tenant-a", snapshot.getTenantId());
        assertEquals("run-context-1", snapshot.getRunId());
        assertEquals(101L, snapshot.getConversationId());
        assertEquals(99L, snapshot.getRoleCardId());
        assertEquals("kernel", snapshot.getExecutorEngine());
        assertTrue(snapshot.getSnapshotJson().contains("\"knowledgeBaseIds\":[\"kb-1\"]"));
        assertTrue(snapshot.getSnapshotJson().contains("\"executorEngine\":\"kernel\""));
        assertTrue(snapshot.getTraceContextJson().contains("trace-1"));
    }

    @Test
    void shouldSaveRunContextSnapshotForRagChat() {
        RecordingRunContextSnapshotRepository snapshotRepository = new RecordingRunContextSnapshotRepository();
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.empty(),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.of(snapshotRepository),
                List.of());

        service.streamChat(new StreamChatCommand(
                "Explain the current design",
                "101",
                "task-rag-1",
                "user-1",
                false,
                ChatMode.RAG,
                null,
                null,
                null,
                List.of("attachment-1"),
                List.of("java-review"),
                List.of("kb-1"),
                99L,
                123L), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(1, snapshotRepository.records.size());
        RunContextSnapshotRecord snapshot = snapshotRepository.records.get(0);
        assertEquals("default", snapshot.getTenantId());
        assertEquals("task-rag-1", snapshot.getRunId());
        assertEquals(101L, snapshot.getConversationId());
        assertEquals(123L, snapshot.getBranchLeafMessageId());
        assertEquals(99L, snapshot.getRoleCardId());
        assertEquals("kernel", snapshot.getExecutorEngine());
        assertTrue(snapshot.getSnapshotJson().contains("\"chatMode\":\"RAG\""));
        assertTrue(snapshot.getSnapshotJson().contains("\"branchLeafMessageId\":123"));
        assertTrue(snapshot.getSnapshotJson().contains("\"knowledgeBaseIds\":[\"kb-1\"]"));
        assertTrue(snapshot.getSnapshotJson().contains("\"attachmentIds\":[\"attachment-1\"]"));
        assertTrue(snapshot.getSnapshotJson().contains("\"selectedSkillNames\":[\"java-review\"]"));
    }

    @Test
    void shouldApplyRunProfileToolBindingsAndSnapshotForAgentChat() {
        AgentRun run = new AgentRun(
                "run-profile-run-1",
                "legacy-react-agent",
                null,
                null,
                "tenant-a",
                "user-1",
                "101",
                AgentRunTriggerType.CHAT,
                "Use profile",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
        RecordingAgentRunInboundPort runPort = new RecordingAgentRunInboundPort(run);
        CapturingAgentLoop agentLoop = new CapturingAgentLoop("profile answer", "kernel");
        RecordingRunContextSnapshotRepository snapshotRepository = new RecordingRunContextSnapshotRepository();
        InMemoryRunProfilePort runProfilePort = new InMemoryRunProfilePort(profileDetails());
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runPort),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.of(snapshotRepository),
                Optional.of(runProfilePort),
                List.of());

        service.streamChat(new StreamChatCommand(
                "Use profile",
                "101",
                "task-profile-1",
                "user-1",
                false,
                ChatMode.AGENT,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of("kb-1"),
                null,
                123L,
                77L), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(List.of("profile answer"), callback.contents);
        assertNotNull(agentLoop.lastRequest);
        assertEquals(
                List.of("profile-tool-a", "filesystem.read_file", "seahorse-researcher"),
                agentLoop.lastRequest.allowedToolIds());
        assertTrue(agentLoop.lastRequest.explicitToolAllowlist());
        assertEquals(0.2D, agentLoop.lastRequest.samplingOptions().getTemperature());
        assertEquals(77L, runPort.startCommand.runProfileId());
        assertEquals("agentscope", runPort.startCommand.executorEngine());
        assertEquals(Map.of("studioTraceEnabled", true), runPort.startCommand.executorConfig());
        assertTrue(runPort.startCommand.metadataJson().contains("\"engine\":\"agentscope\""),
                runPort.startCommand.metadataJson());
        assertEquals(1, snapshotRepository.records.size());
        RunContextSnapshotRecord snapshot = snapshotRepository.records.get(0);
        assertEquals(77L, snapshot.getRunProfileId());
        assertEquals(200L, snapshot.getRoleCardId());
        assertEquals("agentscope", snapshot.getExecutorEngine());
        assertEquals("{\"studioTraceEnabled\":true}", snapshot.getExecutorConfigJson());
        assertTrue(snapshot.getSnapshotJson().contains("\"runProfileId\":77"));
        assertTrue(snapshot.getSnapshotJson().contains("\"executorEngine\":\"agentscope\""));
        assertTrue(snapshot.getSnapshotJson().contains(
                "\"runProfile\":{\"id\":77,\"name\":\"AgentScope profile\",\"roleCardId\":200,"
                        + "\"executorEngine\":\"agentscope\"}"));
        assertTrue(snapshot.getSnapshotJson().contains("\"executorConfig\":{\"studioTraceEnabled\":true}"));
        assertTrue(snapshot.getSnapshotJson().contains("\"modelConfig\":{\"temperature\":0.2}"));
        assertTrue(snapshot.getSnapshotJson().contains("\"profileModelConfig\":{\"temperature\":0.2}"));
        assertTrue(snapshot.getSnapshotJson().contains("\"memoryScope\":{\"longTerm\":true}"));
        assertTrue(snapshot.getSnapshotJson().contains(
                "\"toolIds\":[\"profile-tool-a\",\"filesystem.read_file\",\"seahorse-researcher\"]"));
        assertTrue(snapshot.getSnapshotJson().contains("\"mcpToolIds\":[\"filesystem.read_file\"]"));
        assertTrue(snapshot.getSnapshotJson().contains("\"a2aAgentIds\":[\"seahorse-researcher\"]"));
        assertTrue(snapshot.getSnapshotJson().contains("\"explicitToolAllowlist\":true"));
        assertTrue(snapshot.getSnapshotJson().contains("\"guardrailConfig\":{\"highRiskToolApproval\":true}"));
    }

    @Test
    void shouldRouteAgentChatToRunProfileExecutorEngine() {
        AgentRun run = new AgentRun(
                "run-profile-router-1",
                "legacy-react-agent",
                null,
                null,
                "tenant-a",
                "user-1",
                "101",
                AgentRunTriggerType.CHAT,
                "Use profile router",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
        RecordingAgentRunInboundPort runPort = new RecordingAgentRunInboundPort(run);
        CapturingAgentLoop kernelLoop = new CapturingAgentLoop("kernel answer", "kernel");
        CapturingAgentLoop agentScopeLoop = new CapturingAgentLoop("agentscope answer", "agentscope");
        ReActExecutorRouter router = new ReActExecutorRouter(List.of(kernelLoop, agentScopeLoop), "kernel");
        assertEquals("kernel", router.engineId());
        RecordingRunContextSnapshotRepository snapshotRepository = new RecordingRunContextSnapshotRepository();
        InMemoryRunProfilePort runProfilePort = new InMemoryRunProfilePort(profileDetails());
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(router),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runPort),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.of(snapshotRepository),
                Optional.of(runProfilePort),
                List.of());

        service.streamChat(new StreamChatCommand(
                "Use profile router",
                "101",
                "task-profile-router-1",
                "user-1",
                false,
                ChatMode.AGENT,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                77L), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(List.of("agentscope answer"), callback.contents);
        assertNull(kernelLoop.lastRequest);
        assertNotNull(agentScopeLoop.lastRequest);
        assertEquals("agentscope", agentScopeLoop.lastRequest.executorEngine());
        assertEquals(1, snapshotRepository.records.size());
        assertEquals("agentscope", snapshotRepository.records.get(0).getExecutorEngine());
    }

    @Test
    void shouldFailAgentRunWhenProfileExecutorEngineIsUnavailable() {
        AgentRun run = new AgentRun(
                "run-profile-router-missing-1",
                "legacy-react-agent",
                null,
                null,
                "tenant-a",
                "user-1",
                "101",
                AgentRunTriggerType.CHAT,
                "Use unavailable profile router",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
        RecordingAgentRunInboundPort runPort = new RecordingAgentRunInboundPort(run);
        CapturingAgentLoop kernelLoop = new CapturingAgentLoop("kernel answer", "kernel");
        ReActExecutorRouter router = new ReActExecutorRouter(List.of(kernelLoop), "kernel");
        InMemoryRunProfilePort runProfilePort = new InMemoryRunProfilePort(profileDetails());
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(router),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runPort),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(runProfilePort),
                List.of());

        service.streamChat(new StreamChatCommand(
                "Use unavailable profile router",
                "101",
                "task-profile-router-missing-1",
                "user-1",
                false,
                ChatMode.AGENT,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                77L), callback);

        assertTrue(callback.awaitTerminal());
        assertNotNull(callback.error);
        assertEquals(run.runId(), callback.runId);
        assertEquals(run.runId(), runPort.failedRunId);
        assertTrue(runPort.failureMessage.contains("agentscope"), runPort.failureMessage);
    }

    @Test
    void shouldUseConversationAppliedRunProfileWhenRequestDoesNotSpecifyProfile() {
        AgentRun run = new AgentRun(
                "run-profile-conversation-1",
                "legacy-react-agent",
                null,
                null,
                "tenant-a",
                "user-1",
                "101",
                AgentRunTriggerType.CHAT,
                "Use conversation profile",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
        RecordingAgentRunInboundPort runPort = new RecordingAgentRunInboundPort(run);
        CapturingAgentLoop agentLoop = new CapturingAgentLoop("conversation profile answer", "agentscope");
        RecordingRunContextSnapshotRepository snapshotRepository = new RecordingRunContextSnapshotRepository();
        InMemoryRunProfilePort runProfilePort = new InMemoryRunProfilePort(profileDetails());
        runProfilePort.applyToConversation("user-1", "101", 77L);
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runPort),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.of(snapshotRepository),
                Optional.of(runProfilePort),
                List.of());

        service.streamChat(new StreamChatCommand(
                "Use conversation profile",
                "101",
                "task-profile-conversation-1",
                "user-1",
                false,
                ChatMode.AGENT,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(List.of("conversation profile answer"), callback.contents);
        assertNotNull(agentLoop.lastRequest);
        assertEquals("agentscope", agentLoop.lastRequest.executorEngine());
        assertEquals(List.of("profile-tool-a", "filesystem.read_file", "seahorse-researcher"),
                agentLoop.lastRequest.allowedToolIds());
        assertEquals(1, snapshotRepository.records.size());
        RunContextSnapshotRecord snapshot = snapshotRepository.records.get(0);
        assertEquals(77L, snapshot.getRunProfileId());
        assertTrue(snapshot.getSnapshotJson().contains("\"runProfileId\":77"));
    }

    @Test
    void shouldPropagateAgentRunRolloutIdToToolGatewayRequest() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "weather", Map.of("city", "Shanghai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need weather", List.of(toolCall)),
                Turn.finalAnswer("Shanghai 21C")));
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{\"temp\":21}"));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok("{\"temp\":21}"));
        KernelAgentLoop agentLoop = agentLoop(
                model,
                toolRegistry,
                gateway,
                KernelAgentLoopOptions.defaults());
        AgentRun run = new AgentRun(
                "run-rollout-1",
                "legacy-react-agent",
                null,
                "rollout-1",
                "default",
                "user-1",
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "What is the weather?",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
        RecordingAgentRunInboundPort runPort = new RecordingAgentRunInboundPort(run);
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runPort));

        service.streamChat(new StreamChatCommand(
                "What is the weather?", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals("run-rollout-1", runPort.startedRun.runId());
        assertEquals(1, gateway.requests.size());
        assertEquals("run-rollout-1", gateway.requests.get(0).runId());
        assertEquals("rollout-1", gateway.requests.get(0).rolloutId());
    }

    @Test
    void agentModePropagatesConfiguredMaxStepsToLoopRequest() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService runService = new KernelAgentRunService(
                new EmptyAgentDefinitionRepository(), runRepository,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);
        List<Turn> turns = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            turns.add(Turn.toolCalls(
                    "need lookup " + i,
                    List.of(AgentToolCall.of("call-" + i, "weather", Map.of("city", "Shanghai")))));
        }
        turns.add(Turn.finalAnswer("complete after configured steps"));
        ScriptedModel model = new ScriptedModel(turns);
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{\"temp\":21}"));
        KernelAgentLoopOptions options = KernelAgentLoopOptions.builder().maxSteps(7).build();
        KernelAgentLoop agentLoop = agentLoop(
                model,
                toolRegistry,
                successfulWeatherGateway(),
                options,
                new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runService),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                options);

        service.streamChat(new StreamChatCommand(
                "Use as many steps as needed", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT),
                callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(List.of("complete after configured steps"), callback.contents);
        assertEquals(7, model.requests.size());
        AgentRun run = runRepository.runs.values().iterator().next();
        assertEquals(AgentRunStatus.SUCCEEDED, run.status());
    }

    @Test
    void shouldKeepRunWaitingApprovalWhenAgentLoopPausesForApproval() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        KernelAgentRunService runService = new KernelAgentRunService(
                new EmptyAgentDefinitionRepository(), runRepository,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);
        AgentToolCall toolCall = AgentToolCall.of("call-1", "memory-forget", Map.of("memoryId", "mem-1"));
        ScriptedModel model = new ScriptedModel(List.of(Turn.toolCalls("need approval", List.of(toolCall))));
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(new ToolDescriptor("memory-forget", "Memory Forget", "Forget memory", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("should-not-run"));
        KernelAgentLoop agentLoop = agentLoop(
                model,
                toolRegistry,
                approvalRequiredGateway(),
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK),
                RepositoryAgentApprovalWaitHandler.fromRepositories(
                        runRepository,
                        checkpointRepository,
                        FIXED_CLOCK));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runService));

        service.streamChat(new StreamChatCommand(
                "Forget memory", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT), callback);

        assertTrue(callback.awaitTerminal());
        AgentRun run = runRepository.runs.values().iterator().next();
        assertEquals(AgentRunStatus.WAITING_APPROVAL, run.status());
        assertTrue(checkpointRepository.findLatestByRunId(run.runId()).orElseThrow()
                .pendingToolCallJson()
                .contains("\"toolId\":\"memory-forget\""));
    }

    @Test
    void registeredAgentModeUsesLatestVersionModelConfigForRuntimeRequest() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.create(agentDefinition("ops-agent", "ops-agent-v1"));
        definitionRepository.saveVersion(agentVersion("ops-agent", "ops-agent-v1", """
                {
                  "modelId": "agent-chat-model",
                  "temperature": 0.12,
                  "topP": 0.8,
                  "topK": 30,
                  "maxTokens": 2048,
                  "thinking": true
                }
                """));
        KernelAgentRunService runService = new KernelAgentRunService(
                definitionRepository, runRepository,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("ops answer")));
        KernelAgentLoop agentLoop = agentLoop(
                model,
                new InMemoryToolRegistry(),
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runService),
                Optional.empty(),
                Optional.of(definitionRepository));

        service.streamChat(new StreamChatCommand(
                "Run ops", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT, "ops-agent", null),
                callback);

        assertTrue(callback.awaitTerminal());
        AgentRun run = runRepository.runs.values().iterator().next();
        assertEquals("ops-agent", run.agentId());
        assertEquals("ops-agent-v1", run.versionId());
        ChatRequest modelRequest = model.requests.get(0);
        assertEquals("agent-chat-model", modelRequest.getModelId());
        ChatSamplingOptions sampling = modelRequest.getSamplingOptions();
        assertEquals(0.12D, sampling.getTemperature());
        assertEquals(0.8D, sampling.getTopP());
        assertEquals(30, sampling.getTopK());
        assertEquals(2048, sampling.getMaxTokens());
        assertEquals(true, sampling.getThinking());
    }

    @Test
    void registeredAgentModeInjectsVersionedSkillSnapshotWithoutGrantingTools() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.create(agentDefinition("ops-agent", "ops-agent-v1"));
        definitionRepository.saveVersion(agentVersion(
                "ops-agent",
                "ops-agent-v1",
                "{\"modelId\":\"agent-chat-model\"}",
                """
                        {
                          "version": 1,
                          "mode": "BOUND_REVISIONS",
                          "skills": [
                            {
                              "name": "deep-research",
                              "revisionId": "skillrev_deep_research_1",
                              "contentHash": "sha256:test",
                              "description": "Research with sources",
                              "category": "PUBLIC",
                              "injectMode": "METADATA_AND_BODY",
                              "allowedTools": ["web_search"],
                              "content": "# Deep Research\\nAlways verify claims with sources."
                            }
                          ]
                        }
                        """));
        KernelAgentRunService runService = new KernelAgentRunService(
                definitionRepository, runRepository,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("ops answer")));
        KernelAgentLoop agentLoop = agentLoop(
                model,
                new InMemoryToolRegistry(),
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runService),
                Optional.empty(),
                Optional.of(definitionRepository));

        service.streamChat(new StreamChatCommand(
                "Run ops", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT, "ops-agent", null),
                callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(null, callback.error);
        ChatRequest modelRequest = model.requests.get(0);
        String systemPrompt = modelRequest.getMessages().get(0).getContent();
        assertTrue(systemPrompt.contains("<skills>"));
        assertTrue(systemPrompt.contains("<skill name=\"deep-research\" revision=\"skillrev_deep_research_1\">"));
        assertTrue(systemPrompt.contains("Always verify claims with sources."));
        assertTrue(modelRequest.getTools().stream().anyMatch(tool -> "load_skill".equals(tool.toolId())));
        assertTrue(modelRequest.getTools().stream().noneMatch(tool -> "web_search".equals(tool.toolId())));
        AgentRun run = runRepository.runs.values().iterator().next();
        assertTrue(run.metadataJson().contains("\"agentId\":\"ops-agent\""));
        assertTrue(run.metadataJson().contains("\"versionId\":\"ops-agent-v1\""));
        assertTrue(run.metadataJson().contains("\"instructions\":\"You are an ops assistant.\""));
        assertTrue(run.metadataJson().contains("\"modelConfigJson\":\"{\\\"modelId\\\":\\\"agent-chat-model\\\"}\""));
        assertTrue(run.metadataJson().contains("\"skillSetJson\""));
        assertTrue(run.metadataJson().contains("deep-research"), run.metadataJson());
        assertTrue(run.metadataJson().contains("\"allowedToolIds\""), run.metadataJson());
        assertTrue(run.metadataJson().contains("\"load_skill\""), run.metadataJson());
    }

    @Test
    void agentRunMetadataIncludesExecutionBackendPromptSourceSnapshot() {
        AgentRun run = new AgentRun(
                "run-metadata-1",
                "ops-agent",
                "ops-agent-v1",
                "rollout-1",
                "tenant-a",
                "alice",
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "Run ops",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
        RecordingAgentRunInboundPort runPort = new RecordingAgentRunInboundPort(run);
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.create(agentDefinition("ops-agent", "ops-agent-v1"));
        definitionRepository.saveVersion(agentVersion("ops-agent", "ops-agent-v1", "{\"modelId\":\"ops-model\"}"));
        UsageEmittingAgentLoop agentLoop = new UsageEmittingAgentLoop(new ChatTokenUsage(0, 0));
        AgentRunMetadataContributor metadataContributor = command -> Map.of(
                "prompt", Map.of(
                        "source", "nacos",
                        "key", "seahorse.agent.prompt",
                        "version", "stable",
                        "label", "default",
                        "namespace", "public",
                        "group", "DEFAULT_GROUP",
                        "revision", "stable"));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runPort),
                Optional.empty(),
                Optional.of(definitionRepository),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.empty(),
                Optional.empty(),
                List.of(metadataContributor));

        service.streamChat(new StreamChatCommand(
                "Run ops", "conversation-1", "task-1", "alice", false,
                ChatMode.AGENT, "ops-agent", "ops-agent-v1"), callback);

        assertTrue(callback.awaitTerminal());
        assertTrue(runPort.startCommand.metadataJson().contains("\"prompt\""), runPort.startCommand.metadataJson());
        assertTrue(runPort.startCommand.metadataJson().contains("\"source\":\"nacos\""),
                runPort.startCommand.metadataJson());
        assertTrue(runPort.startCommand.metadataJson().contains("\"key\":\"seahorse.agent.prompt\""),
                runPort.startCommand.metadataJson());
        assertTrue(runPort.startCommand.metadataJson().contains("\"version\":\"stable\""),
                runPort.startCommand.metadataJson());
        assertTrue(runPort.startCommand.metadataJson().contains("\"label\":\"default\""),
                runPort.startCommand.metadataJson());
        assertTrue(runPort.startCommand.metadataJson().contains("\"revision\":\"stable\""),
                runPort.startCommand.metadataJson());
    }

    @Test
    void registeredAgentModeExposesToolSetJsonToolsToModel() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.create(agentDefinition("project-agent", "project-agent-v1"));
        definitionRepository.saveVersion(agentVersion(
                "project-agent",
                "project-agent-v1",
                "{\"modelId\":\"agent-chat-model\"}",
                """
                        {
                          "tools": [
                            "github_repository_reader",
                            {"toolId": "web_fetch"},
                            {"id": "image_generation"}
                          ]
                        }
                        """,
                AgentVersion.EMPTY_JSON_OBJECT));
        KernelAgentRunService runService = new KernelAgentRunService(
                definitionRepository, runRepository,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("project answer")));
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(new ToolDescriptor(
                GitHubRepositoryReaderToolPortAdapter.TOOL_ID, "GitHub", "Read GitHub", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
        toolRegistry.register(new ToolDescriptor(WebFetchToolPortAdapter.TOOL_ID, "Web Fetch", "Fetch", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
        toolRegistry.register(new ToolDescriptor(
                ImageGenerationToolPortAdapter.TOOL_ID, "Image", "Generate image", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
        KernelAgentLoop agentLoop = agentLoop(
                model,
                toolRegistry,
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runService),
                Optional.empty(),
                Optional.of(definitionRepository));

        service.streamChat(new StreamChatCommand(
                "Summarize https://github.com/redis/redis",
                "conversation-1",
                "task-1",
                "user-1",
                false,
                ChatMode.AGENT,
                "project-agent",
                null), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(null, callback.error);
        assertEquals(List.of(
                GitHubRepositoryReaderToolPortAdapter.TOOL_ID,
                WebFetchToolPortAdapter.TOOL_ID,
                ImageGenerationToolPortAdapter.TOOL_ID), model.requests.get(0).getTools().stream()
                .map(ToolDescriptor::toolId)
                .toList());
        assertTrue(model.requests.get(0).getMessages().get(0).getContent()
                .contains("You are an ops assistant."));
    }

    @Test
    void defaultAgentTemplateUsesBoundAgentVersionToolSet() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.create(agentDefinition(
                "github-visual-project-intro-agent",
                "github-visual-project-intro-agent-v1"));
        definitionRepository.saveVersion(agentVersion(
                "github-visual-project-intro-agent",
                "github-visual-project-intro-agent-v1",
                "{\"modelId\":\"agent-chat-model\"}",
                """
                        {
                          "tools": [
                            "github_repository_reader",
                            "web_fetch",
                            "image_generation"
                          ]
                        }
                        """,
                AgentVersion.EMPTY_JSON_OBJECT));
        KernelAgentRunService runService = new KernelAgentRunService(
                definitionRepository, runRepository,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("visual intro")));
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(new ToolDescriptor(
                GitHubRepositoryReaderToolPortAdapter.TOOL_ID, "GitHub", "Read GitHub", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
        toolRegistry.register(new ToolDescriptor(WebFetchToolPortAdapter.TOOL_ID, "Web Fetch", "Fetch", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
        toolRegistry.register(new ToolDescriptor(
                ImageGenerationToolPortAdapter.TOOL_ID, "Image", "Generate image", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
        KernelAgentLoop agentLoop = agentLoop(
                model,
                toolRegistry,
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runService),
                Optional.empty(),
                Optional.of(definitionRepository),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.of(new KernelTaskTemplateQueryService()));

        service.streamChat(new StreamChatCommand(
                "Introduce https://github.com/redis/redis",
                "conversation-1",
                "task-1",
                "user-1",
                false,
                ChatMode.AGENT,
                null,
                null,
                "github-visual-project-intro",
                List.of()), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(null, callback.error);
        AgentRun run = runRepository.runs.values().iterator().next();
        assertEquals("github-visual-project-intro-agent", run.agentId());
        assertEquals("github-visual-project-intro-agent-v1", run.versionId());
        assertEquals(List.of(
                GitHubRepositoryReaderToolPortAdapter.TOOL_ID,
                WebFetchToolPortAdapter.TOOL_ID,
                ImageGenerationToolPortAdapter.TOOL_ID), model.requests.get(0).getTools().stream()
                .map(ToolDescriptor::toolId)
                .toList());
        assertTrue(callback.events.stream()
                .anyMatch(event -> "artifact_created".equals(event.eventName())));
    }

    @Test
    void explicitMissingRegisteredAgentVersionFailsInsteadOfUsingDefaultModelConfig() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.create(agentDefinition("ops-agent", "ops-agent-v1"));
        definitionRepository.saveVersion(agentVersion("ops-agent", "ops-agent-v1", """
                {"modelId":"agent-chat-model"}
                """));
        KernelAgentRunService runService = new KernelAgentRunService(
                definitionRepository, runRepository,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("should-not-run")));
        KernelAgentLoop agentLoop = agentLoop(
                model,
                new InMemoryToolRegistry(),
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runService),
                Optional.empty(),
                Optional.of(definitionRepository));

        service.streamChat(new StreamChatCommand(
                "Run ops", "conversation-1", "task-1", "user-1", false,
                ChatMode.AGENT, "ops-agent", "ops-agent-missing"), callback);

        assertTrue(callback.awaitTerminal());
        assertNotNull(callback.error);
        assertTrue(callback.error.getMessage().contains("Agent version does not exist"));
        assertTrue(model.requests.isEmpty());
        assertTrue(runRepository.runs.isEmpty());
    }

    @Test
    void controlledWebTemplatesExposeOnlyControlledWebResearchTools() {
        for (String templateId : List.of("deep-research", "web-summary", "compare-analysis")) {
            MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
            KernelAgentRunService runService = new KernelAgentRunService(
                    new EmptyAgentDefinitionRepository(), runRepository,
                    () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);
            ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("research answer")));
            InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
            toolRegistry.register(new ToolDescriptor(WebSearchToolPortAdapter.TOOL_ID, "Web Search", "Search", "{}"),
                    (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
            toolRegistry.register(new ToolDescriptor(WebFetchToolPortAdapter.TOOL_ID, "Web Fetch", "Fetch", "{}"),
                    (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
            toolRegistry.register(
                    new ToolDescriptor(SearchKnowledgeBaseToolPortAdapter.TOOL_ID, "KB Search", "KB", "{}"),
                    (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
            toolRegistry.register(new ToolDescriptor(GetDateTimeToolPortAdapter.TOOL_ID, "Date Time", "Time", "{}"),
                    (callId, toolId, arguments) -> ToolInvocationResult.ok("{}"));
            KernelAgentLoop agentLoop = agentLoop(
                    model,
                    toolRegistry,
                    KernelAgentLoopOptions.defaults(),
                    new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK));
            RecordingCallback callback = new RecordingCallback();
            KernelChatInboundService service = new KernelChatInboundService(
                    newPipeline(),
                    StreamTaskPort.noop(),
                    Optional.of(agentLoop),
                    null,
                    null,
                    MemoryEnginePort.noop(),
                    Optional.of(runService));

            service.streamChat(new StreamChatCommand(
                    "Research public information",
                    "conversation-1",
                    "task-" + templateId,
                    "user-1",
                    false,
                    ChatMode.AGENT,
                    null,
                    null,
                    templateId,
                    List.of()), callback);

            assertTrue(callback.awaitTerminal());
            List<String> toolIds = model.requests.get(0).getTools().stream()
                    .map(ToolDescriptor::toolId)
                    .toList();
            assertEquals(List.of(
                    WebSearchToolPortAdapter.TOOL_ID,
                    WebFetchToolPortAdapter.TOOL_ID,
                    SearchKnowledgeBaseToolPortAdapter.TOOL_ID,
                    GetDateTimeToolPortAdapter.TOOL_ID), toolIds);
        }
    }

    private static ToolGatewayPort approvalRequiredGateway() {
        return request -> ToolInvocationResult.failed(ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED);
    }

    private static ToolGatewayPort successfulWeatherGateway() {
        return request -> ToolInvocationResult.ok("{\"temp\":21}");
    }

    private static KernelAgentLoop agentLoop(
            StreamingChatModelPort model,
            InMemoryToolRegistry toolRegistry,
            KernelAgentLoopOptions options,
            AgentRunStepRecorder runStepRecorder) {
        return agentLoop(model, toolRegistry, null, options, runStepRecorder, AgentApprovalWaitHandler.noop());
    }

    private static KernelAgentLoop agentLoop(
            StreamingChatModelPort model,
            InMemoryToolRegistry toolRegistry,
            ToolGatewayPort toolGateway,
            KernelAgentLoopOptions options) {
        return agentLoop(model, toolRegistry, toolGateway, options, AgentRunStepRecorder.noop(),
                AgentApprovalWaitHandler.noop());
    }

    private static KernelAgentLoop agentLoop(
            StreamingChatModelPort model,
            InMemoryToolRegistry toolRegistry,
            ToolGatewayPort toolGateway,
            KernelAgentLoopOptions options,
            AgentRunStepRecorder runStepRecorder) {
        return agentLoop(model, toolRegistry, toolGateway, options, runStepRecorder, AgentApprovalWaitHandler.noop());
    }

    private static KernelAgentLoop agentLoop(
            StreamingChatModelPort model,
            InMemoryToolRegistry toolRegistry,
            ToolGatewayPort toolGateway,
            KernelAgentLoopOptions options,
            AgentRunStepRecorder runStepRecorder,
            AgentApprovalWaitHandler approvalWaitHandler) {
        return new KernelAgentLoop(new AgentLoopDependencies(
                model,
                toolRegistry,
                toolGateway,
                options,
                KernelRagTraceRecorder.noop(),
                new DefaultContextWeaver(),
                runStepRecorder,
                approvalWaitHandler,
                null,
                null,
                null,
                null));
    }

    private static final class RecordingToolGateway implements ToolGatewayPort {
        private final ToolInvocationResult result;
        private final List<ToolInvocationRequest> requests = new ArrayList<>();

        private RecordingToolGateway(ToolInvocationResult result) {
            this.result = result;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            requests.add(request);
            return result;
        }
    }

    private static final class UsageEmittingAgentLoop implements ReActExecutorPort {
        private final ChatTokenUsage usage;

        private UsageEmittingAgentLoop(ChatTokenUsage usage) {
            this.usage = usage;
        }

        @Override
        public AgentLoopResult execute(AgentLoopRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
            callback.onUsage(usage);
            callback.onContent("answer");
            callback.onComplete();
            return () -> {
            };
        }
    }

    private static final class CapturingAgentLoop implements ReActExecutorPort {
        private final String answer;
        private final String engineId;
        private AgentLoopRequest lastRequest;

        private CapturingAgentLoop(String answer, String engineId) {
            this.answer = answer;
            this.engineId = engineId;
        }

        @Override
        public AgentLoopResult execute(AgentLoopRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
            this.lastRequest = request;
            callback.onContent(answer);
            callback.onComplete();
            return () -> {
            };
        }

        @Override
        public String engineId() {
            return engineId;
        }
    }

    private static RunProfileDetails profileDetails() {
        RunProfileRecord profile = new RunProfileRecord();
        profile.setId(77L);
        profile.setTenantId("tenant-a");
        profile.setUserId("user-1");
        profile.setName("AgentScope profile");
        profile.setRoleCardId(200L);
        profile.setExecutorEngine("agentscope");
        profile.setExecutorConfigJson("{\"studioTraceEnabled\":true}");
        profile.setModelConfigJson("{\"temperature\":0.2}");
        profile.setMemoryScopeJson("{\"longTerm\":true}");
        profile.setGuardrailConfigJson("{\"highRiskToolApproval\":true}");
        return RunProfileDetails.builder()
                .profile(profile)
                .toolBindings(List.of(
                        RunProfileToolBindingRecord.builder()
                                .profileId(77L)
                                .toolId("profile-tool-a")
                                .provider("BUILT_IN")
                                .enabled(1)
                                .build(),
                        RunProfileToolBindingRecord.builder()
                                .profileId(77L)
                                .toolId("profile-tool-disabled")
                                .provider("BUILT_IN")
                                .enabled(0)
                                .build(),
                        RunProfileToolBindingRecord.builder()
                                .profileId(77L)
                                .toolId("filesystem.read_file")
                                .provider("MCP")
                                .enabled(1)
                                .build(),
                        RunProfileToolBindingRecord.builder()
                                .profileId(77L)
                                .toolId("seahorse-researcher")
                                .provider("A2A")
                                .enabled(1)
                                .build()))
                .build();
    }

    private static final class InMemoryRunProfilePort implements RunProfileInboundPort {
        private final RunProfileDetails details;
        private final Map<String, Long> appliedProfiles = new LinkedHashMap<>();

        private InMemoryRunProfilePort(RunProfileDetails details) {
            this.details = details;
        }

        @Override
        public List<RunProfileRecord> list(String userId) {
            return details == null || details.getProfile() == null ? List.of() : List.of(details.getProfile());
        }

        @Override
        public Optional<RunProfileDetails> findById(String userId, Long id) {
            if (details == null || details.getProfile() == null || !Objects.equals(details.getProfile().getId(), id)) {
                return Optional.empty();
            }
            return Optional.of(details);
        }

        @Override
        public RunProfileResolvedPreview applyToConversation(String userId, String conversationId, Long id) {
            appliedProfiles.put(userId + ":" + conversationId, id);
            return RunProfileResolvedPreview.builder()
                    .runProfileId(id)
                    .executorEngine(details.getProfile().getExecutorEngine())
                    .explicitToolAllowlist(true)
                    .toolIds(List.of())
                    .mcpToolIds(List.of())
                    .a2aAgentIds(List.of())
                    .build();
        }

        @Override
        public Optional<RunProfileDetails> findAppliedToConversation(String userId, String conversationId) {
            return findById(userId, appliedProfiles.get(userId + ":" + conversationId));
        }

        @Override
        public Long save(com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void activate(String userId, Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String userId, Long id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingCostUsageRepository implements CostUsageRepositoryPort {
        private final List<CostUsageRecord> records = new ArrayList<>();

        @Override
        public CostUsageRecord append(CostUsageRecord record) {
            records.add(record);
            return record;
        }

        @Override
        public CostUsageAggregate aggregate(CostUsageQuery query) {
            return new CostUsageAggregate("tenant-a", null, null, 0L, 0L, 0.0D, 0L);
        }
    }

    private static final class RecordingRunContextSnapshotRepository implements RunContextSnapshotRepositoryPort {
        private final List<RunContextSnapshotRecord> records = new ArrayList<>();

        @Override
        public Long save(RunContextSnapshotRecord record) {
            records.add(record);
            return 1L;
        }

        @Override
        public Optional<RunContextSnapshotRecord> findByRunId(String runId) {
            return records.stream()
                    .filter(record -> runId.equals(record.getRunId()))
                    .findFirst();
        }
    }

    private static final class RecordingAgentRunInboundPort implements AgentRunInboundPort {
        private final AgentRun startedRun;
        private AgentRunStartCommand startCommand;
        private String failedRunId;
        private String failureMessage;

        private RecordingAgentRunInboundPort(AgentRun startedRun) {
            this.startedRun = startedRun;
        }

        @Override
        public AgentRun startRun(AgentRunStartCommand command) {
            this.startCommand = command;
            return startedRun;
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return startedRun.runId().equals(runId) ? Optional.of(startedRun) : Optional.empty();
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            return List.of();
        }

        @Override
        public AgentRun cancel(String runId) {
            return startedRun;
        }

        @Override
        public AgentRun retry(String runId) {
            return startedRun;
        }

        @Override
        public AgentRun succeed(String runId) {
            return startedRun;
        }

        @Override
        public AgentRun fail(String runId, String errorCode, String errorMessage) {
            this.failedRunId = runId;
            this.failureMessage = errorMessage;
            return startedRun;
        }
    }

    private static KernelChatPipeline newPipeline() {
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(
                com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort.passthrough(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort.passthrough(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort.empty(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build());
        ChatResponsePorts responsePorts = new ChatResponsePorts(
                com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort.simple(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort.empty(),
                StreamingChatModelPort.noop(),
                StreamTaskPort.noop());
        return new KernelChatPipeline(preparationPorts, responsePorts);
    }

    private static final class ScriptedModel implements StreamingChatModelPort {
        private final List<Turn> turns;
        private final List<ChatRequest> requests = new ArrayList<>();
        private int index;

        private ScriptedModel(List<Turn> turns) {
            this.turns = turns;
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            requests.add(request);
            Turn turn = turns.get(index++);
            callback.onContent(turn.content);
            toolCallCollector.onToolCalls(turn.toolCalls);
            callback.onComplete();
            return () -> {
            };
        }
    }

    private record Turn(String content, List<AgentToolCall> toolCalls) {

        private static Turn finalAnswer(String content) {
            return new Turn(content, List.of());
        }

        private static Turn toolCalls(String content, List<AgentToolCall> toolCalls) {
            return new Turn(content, toolCalls);
        }
    }

    private static final class RecordingCallback implements StreamCallback {
        private final CountDownLatch terminal = new CountDownLatch(1);
        private final List<String> contents = new ArrayList<>();
        private final List<RecordedEvent> events = new ArrayList<>();
        private String runId;
        private Throwable error;

        @Override
        public void onContent(String content) {
            contents.add(content);
        }

        @Override
        public void onThinking(String content) {
        }

        @Override
        public void onRunStarted(String runId) {
            this.runId = runId;
        }

        @Override
        public void onEvent(String eventName, Object payload) {
            events.add(new RecordedEvent(eventName, payload));
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
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

    private record RecordedEvent(String eventName, Object payload) {
    }

    private static class EmptyAgentDefinitionRepository implements AgentDefinitionRepositoryPort {

        @Override
        public void create(AgentDefinition definition) {
        }

        @Override
        public void update(AgentDefinition definition) {
        }

        @Override
        public void delete(String agentId) {
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
            return 1;
        }

        @Override
        public void saveVersion(AgentVersion version) {
        }

        @Override
        public Optional<AgentVersion> latestVersion(String agentId) {
            return Optional.empty();
        }

        @Override
        public Optional<AgentVersion> findVersion(String agentId, String versionId) {
            return Optional.empty();
        }
    }

    private static AgentDefinition agentDefinition(String agentId, String latestVersionId) {
        return new AgentDefinition(
                agentId,
                AgentDefinition.DEFAULT_TENANT_ID,
                "Ops Agent",
                "Operations assistant",
                "owner-1",
                "platform",
                AgentType.ASSISTANT,
                null,
                AgentStatus.PUBLISHED,
                AgentRiskLevel.LOW,
                latestVersionId,
                FIXED_CLOCK.instant(),
                FIXED_CLOCK.instant());
    }

    private static AgentVersion agentVersion(String agentId, String versionId, String modelConfigJson) {
        return agentVersion(agentId, versionId, modelConfigJson, AgentVersion.EMPTY_JSON_OBJECT);
    }

    private static AgentVersion agentVersion(String agentId,
                                             String versionId,
                                             String modelConfigJson,
                                             String skillSetJson) {
        return agentVersion(agentId, versionId, modelConfigJson,
                AgentVersion.EMPTY_JSON_OBJECT, skillSetJson);
    }

    private static AgentVersion agentVersion(String agentId,
                                             String versionId,
                                             String modelConfigJson,
                                             String toolSetJson,
                                             String skillSetJson) {
        return new AgentVersion(
                versionId,
                agentId,
                1L,
                "You are an ops assistant.",
                toolSetJson,
                modelConfigJson,
                AgentVersion.EMPTY_JSON_OBJECT,
                AgentVersion.EMPTY_JSON_OBJECT,
                skillSetJson,
                "owner-1",
                FIXED_CLOCK.instant(),
                "publish ops agent");
    }

    private static final class MemoryAgentDefinitionRepository extends EmptyAgentDefinitionRepository {
        private final Map<String, AgentDefinition> definitions = new LinkedHashMap<>();
        private final Map<String, AgentVersion> versions = new LinkedHashMap<>();

        @Override
        public void create(AgentDefinition definition) {
            definitions.put(definition.agentId(), definition);
        }

        @Override
        public void delete(String agentId) {
            definitions.remove(agentId);
            versions.entrySet().removeIf(entry -> entry.getKey().startsWith(agentId + ":"));
        }

        @Override
        public void saveVersion(AgentVersion version) {
            versions.put(version.agentId() + ":" + version.versionId(), version);
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return Optional.ofNullable(definitions.get(agentId));
        }

        @Override
        public Optional<AgentVersion> latestVersion(String agentId) {
            return versions.values().stream()
                    .filter(version -> agentId.equals(version.agentId()))
                    .max(Comparator.comparingLong(AgentVersion::versionNo));
        }

        @Override
        public Optional<AgentVersion> findVersion(String agentId, String versionId) {
            return Optional.ofNullable(versions.get(agentId + ":" + versionId));
        }
    }

    private static final class MemoryAgentRunRepository implements AgentRunRepositoryPort {
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

    private static final class MemoryAgentCheckpointRepository implements AgentCheckpointRepositoryPort {
        private final List<AgentCheckpoint> checkpoints = new ArrayList<>();

        @Override
        public void save(AgentCheckpoint checkpoint) {
            checkpoints.add(checkpoint);
        }

        @Override
        public Optional<AgentCheckpoint> findLatestByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .max(Comparator.comparingLong(AgentCheckpoint::sequenceNo));
        }

        @Override
        public List<AgentCheckpoint> listByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .sorted(Comparator.comparingLong(AgentCheckpoint::sequenceNo))
                    .toList();
        }
    }
}
