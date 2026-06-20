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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.LoadSkillResourceToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ToolSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopExitReason;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillToolPolicyMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamApprovalRequiredEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamSkillEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamToolCallEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentLoopToolGatewayTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldInvokeToolsThroughGatewayWithRunContext() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "weather", Map.of("city", "Shanghai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need weather", List.of(toolCall)),
                Turn.finalAnswer("done")));
        RecordingToolGateway gateway = new RecordingToolGateway();
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("weather?")
                .allowedToolIds(List.of("weather"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .memoryContext(MemoryContext.builder()
                        .conversationId("conversation-1")
                        .userId("user-1")
                .currentQuestion("weather?")
                        .build())
                .runId("run-1")
                .rolloutId("rollout-1")
                .build());

        assertEquals("done", result.finalAnswer());
        assertEquals(1, gateway.requests.size());
        ToolInvocationRequest request = gateway.requests.get(0);
        assertEquals("run-1", request.runId());
        assertEquals("rollout-1", request.rolloutId());
        assertEquals("call-1", request.toolCallId());
        assertEquals("call-1", request.stepId());
        assertEquals("weather", request.toolId());
        assertEquals("Shanghai", request.arguments().get("city"));
        assertEquals("user-1", request.userId());
        assertEquals("conversation-1", request.arguments().get("_seahorseConversationId"));
        assertEquals(List.of("weather"), request.allowedToolIds());
    }

    @Test
    void shouldSendHallucinatedDisallowedToolCallsThroughGatewayPolicyBoundary() {
        AgentToolCall toolCall = AgentToolCall.of("call-2", "delete-memory", Map.of("memoryId", "mem-1"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("try delete", List.of(toolCall)),
                Turn.finalAnswer("blocked")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.failed("TOOL_NOT_BOUND"));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("delete memory")
                .allowedToolIds(List.of("weather"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-2")
                .build());

        assertEquals("blocked", result.finalAnswer());
        assertEquals(1, gateway.requests.size());
        ToolInvocationRequest request = gateway.requests.get(0);
        assertEquals("run-2", request.runId());
        assertEquals("delete-memory", request.toolId());
        assertEquals(List.of("weather"), request.allowedToolIds());
        assertEquals("TOOL_NOT_BOUND", result.steps().get(0).observations().get(0).error());
    }

    @Test
    void shouldDispatchSkillResourceLoadingThroughGatewayWithInjectedRuntimeSnapshot() {
        AgentToolCall loadSkill = AgentToolCall.of("call-skill", "load_skill_resource",
                Map.of("skillName", "research", "resourcePath", "SKILL.md"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need full skill", List.of(loadSkill)),
                Turn.finalAnswer("used the research skill")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok(
                "{\"name\":\"research\",\"resourcePath\":\"SKILL.md\",\"content\":\"Use sources carefully.\"}"));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("write research plan")
                .allowedToolIds(List.of())
                .skillRuntimeBlocks(List.of(new SkillRuntimeBlock(
                        "research",
                        "rev-1",
                        "hash-1",
                        "Research workflow",
                        AgentSkillCategory.PUBLIC,
                        SkillInjectMode.METADATA_ONLY,
                        List.of("web_search"),
                        "Use sources carefully.")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .build());

        assertEquals("used the research skill", result.finalAnswer());
        assertEquals(1, gateway.requests.size());
        ToolInvocationRequest request = gateway.requests.get(0);
        assertEquals(LoadSkillResourceToolPortAdapter.TOOL_ID, request.toolId());
        assertEquals("research", request.arguments().get("skillName"));
        assertEquals("SKILL.md", request.arguments().get("resourcePath"));
        assertTrue(request.arguments().containsKey(LoadSkillResourceToolPortAdapter.RUNTIME_SKILLS_ARGUMENT));
        assertTrue(request.arguments().get(LoadSkillResourceToolPortAdapter.RUNTIME_SKILLS_ARGUMENT) instanceof List<?>);
        assertTrue(model.requests.get(0).getTools().stream()
                .anyMatch(tool -> LoadSkillResourceToolPortAdapter.TOOL_ID.equals(tool.toolId())));
        assertTrue(result.steps().get(0).observations().get(0).success(),
                result.steps().get(0).observations().get(0).error());
        assertTrue(result.steps().get(0).observations().get(0).content().contains("Use sources carefully."));
    }

    @Test
    void shouldRejectLoadingSkillOutsideCurrentVersionSnapshot() {
        AgentToolCall loadSkill = AgentToolCall.of("call-skill", "load_skill_resource",
                Map.of("skillName", "missing", "resourcePath", "SKILL.md"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("try missing skill", List.of(loadSkill)),
                Turn.finalAnswer("could not load")));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                new RecordingToolGateway(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("load missing")
                .allowedToolIds(List.of())
                .skillRuntimeBlocks(List.of(new SkillRuntimeBlock(
                        "research",
                        "rev-1",
                        "hash-1",
                        "Research workflow",
                        AgentSkillCategory.PUBLIC,
                        SkillInjectMode.METADATA_ONLY,
                        List.of(),
                        "Use sources carefully.")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .build());

        assertEquals("could not load", result.finalAnswer());
        assertTrue(result.steps().get(0).observations().get(0).success());
        assertEquals(1, model.requests.get(0).getTools().stream()
                .filter(tool -> LoadSkillResourceToolPortAdapter.TOOL_ID.equals(tool.toolId()))
                .count());
    }

    @Test
    void shouldKeepLegacyLoadSkillAliasForExistingModelCalls() {
        AgentToolCall loadSkill = AgentToolCall.of("call-skill", "load_skill", Map.of("name", "research"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need full skill", List.of(loadSkill)),
                Turn.finalAnswer("used legacy alias")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.failed("should-not-call"));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("write research plan")
                .allowedToolIds(List.of())
                .skillRuntimeBlocks(List.of(new SkillRuntimeBlock(
                        "research",
                        "rev-1",
                        "hash-1",
                        "Research workflow",
                        AgentSkillCategory.PUBLIC,
                        SkillInjectMode.METADATA_ONLY,
                        List.of("web_search"),
                        "Use sources carefully.")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .build());

        assertEquals("used legacy alias", result.finalAnswer());
        assertTrue(gateway.requests.isEmpty());
        assertTrue(result.steps().get(0).observations().get(0).content().contains("Use sources carefully."));
    }

    @Test
    void shouldAllowRegisteredSkillResourceToolThroughDefaultGatewayPolicy() {
        AgentToolCall loadSkill = AgentToolCall.of("call-skill", LoadSkillResourceToolPortAdapter.TOOL_ID,
                Map.of("skillName", "research", "resourcePath", "SKILL.md"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need full skill", List.of(loadSkill)),
                Turn.finalAnswer("used gateway skill loader")));
        ListingOnlyToolRegistry registry = new ListingOnlyToolRegistry();
        KernelAgentLoop loop = kernelLoop(
                model,
                registry,
                new LocalToolGatewayPort(registry),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("write research plan")
                .allowedToolIds(List.of())
                .skillRuntimeBlocks(List.of(new SkillRuntimeBlock(
                        "research",
                        "rev-1",
                        "hash-1",
                        "Research workflow",
                        AgentSkillCategory.PUBLIC,
                        SkillInjectMode.METADATA_ONLY,
                        List.of("web_search"),
                        "Use sources carefully.")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .build());

        assertEquals("used gateway skill loader", result.finalAnswer());
        assertTrue(result.steps().get(0).observations().get(0).success(),
                result.steps().get(0).observations().get(0).error());
        assertTrue(result.steps().get(0).observations().get(0).content().contains("Use sources carefully."));
    }

    @Test
    void shouldNotLetSkillAllowedToolsGrantAgentDeniedToolsInAdvisoryMode() {
        ToolListRecordingModel model = new ToolListRecordingModel();
        RecordingToolGateway gateway = new RecordingToolGateway();
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("answer with skill metadata")
                .allowedToolIds(List.of("weather"))
                .skillRuntimeBlocks(List.of(skill("research", List.of("web_search"))))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-advisory")
                .build());

        assertEquals("direct answer", result.finalAnswer());
        assertEquals(List.of("weather", LoadSkillResourceToolPortAdapter.TOOL_ID, ToolSearchToolPortAdapter.TOOL_ID),
                toolIds(model.tools));
        assertEquals(0, gateway.requests.size());
    }

    @Test
    void shouldRestrictExposedAndGatewayAllowedToolsToSelectedSkillToolsInRestrictiveMode() {
        AgentToolCall toolCall = AgentToolCall.of("call-web", "web_search", Map.of("query", "seahorse"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need search", List.of(toolCall)),
                Turn.finalAnswer("searched")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok("{\"sources\":[]}"));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("research")
                .allowedToolIds(List.of("weather", "web_search"))
                .skillRuntimeBlocks(List.of(skill("research", List.of("web_search"))))
                .skillToolPolicyMode(SkillToolPolicyMode.RESTRICTIVE)
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-restrictive")
                .build());

        assertEquals("searched", result.finalAnswer());
        assertEquals(List.of("web_search", LoadSkillResourceToolPortAdapter.TOOL_ID, ToolSearchToolPortAdapter.TOOL_ID),
                toolIds(model.requests.get(0).getTools()));
        assertEquals(1, gateway.requests.size());
        assertEquals(List.of("web_search"), gateway.requests.get(0).allowedToolIds());
    }

    @Test
    void shouldExposeNoAgentToolsWhenRestrictiveSkillHasNoAllowedTools() {
        ToolListRecordingModel model = new ToolListRecordingModel();
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                new RecordingToolGateway(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("answer without tools")
                .allowedToolIds(List.of("weather", "web_search"))
                .skillRuntimeBlocks(List.of(skill("drafting", List.of())))
                .skillToolPolicyMode(SkillToolPolicyMode.RESTRICTIVE)
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-restrictive-empty")
                .build());

        assertEquals("direct answer", result.finalAnswer());
        assertEquals(List.of(LoadSkillResourceToolPortAdapter.TOOL_ID), toolIds(model.tools));
    }

    @Test
    void shouldInjectEffectiveAllowedToolsIntoToolSearchCalls() {
        AgentToolCall toolCall = AgentToolCall.of("call-search", ToolSearchToolPortAdapter.TOOL_ID,
                Map.of("query", "weather"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("find tools", List.of(toolCall)),
                Turn.finalAnswer("searched tools")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok("{\"tools\":[]}"));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("which tools can I use?")
                .allowedToolIds(List.of("weather", "web_search"))
                .skillRuntimeBlocks(List.of(skill("research", List.of("web_search"))))
                .skillToolPolicyMode(SkillToolPolicyMode.RESTRICTIVE)
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-tool-search")
                .build());

        assertEquals("searched tools", result.finalAnswer());
        assertTrue(toolIds(model.requests.get(0).getTools()).containsAll(List.of(
                "web_search",
                ToolSearchToolPortAdapter.TOOL_ID,
                LoadSkillResourceToolPortAdapter.TOOL_ID)));
        assertEquals(1, gateway.requests.size());
        ToolInvocationRequest request = gateway.requests.get(0);
        assertEquals(ToolSearchToolPortAdapter.TOOL_ID, request.toolId());
        assertEquals(List.of("web_search"), request.arguments().get(ToolSearchToolPortAdapter.ALLOWED_TOOL_IDS_ARGUMENT));
        assertEquals(List.of("web_search", ToolSearchToolPortAdapter.TOOL_ID), request.allowedToolIds());
    }

    @Test
    void shouldNotExposeAnyToolsWhenAllowlistIsEmpty() {
        ToolListRecordingModel model = new ToolListRecordingModel();
        RecordingToolGateway gateway = new RecordingToolGateway();
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("answer directly")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-empty-tools")
                .build());

        assertEquals("direct answer", result.finalAnswer());
        assertEquals(List.of(), model.tools);
        assertEquals(0, gateway.requests.size());
    }

    @Test
    void shouldPauseRunAndCheckpointPendingToolWhenGatewayRequiresApproval() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "memory-forget", Map.of("memoryId", "mem-1"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need approval", List.of(toolCall)),
                Turn.finalAnswer("should not be reached")));
        RecordingToolGateway gateway = new RecordingToolGateway(
                ToolInvocationResult.failed(ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(new AgentRun(
                "run-approval",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "forget memory",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null));
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults(),
                RepositoryAgentApprovalWaitHandler.fromRepositories(
                        runRepository,
                        checkpointRepository,
                        FIXED_CLOCK));

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("forget memory")
                .allowedToolIds(List.of("memory-forget"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-approval")
                .agentId("agent-1")
                .versionId("version-1")
                .tenantId("tenant-1")
                .userId("user-1")
                .build());

        assertEquals(AgentLoopExitReason.WAITING_APPROVAL, result.exitReason());
        assertFalse(result.truncated());
        assertEquals(1, model.index);
        assertEquals(AgentRunStatus.WAITING_APPROVAL, runRepository.runs.get("run-approval").status());
        AgentCheckpoint checkpoint = checkpointRepository.findLatestByRunId("run-approval").orElseThrow();
        assertEquals(AgentCheckpointType.WAITING_APPROVAL, checkpoint.checkpointType());
        assertEquals("call-1", checkpoint.stepId());
        assertTrue(checkpoint.pendingToolCallJson().contains("\"toolId\":\"memory-forget\""));
        assertTrue(checkpoint.pendingToolCallJson().contains("\"toolCallId\":\"call-1\""));
        assertTrue(checkpoint.pendingToolCallJson().contains("\"idempotencyKey\":\"run-approval:call-1\""));
        assertTrue(checkpoint.pendingToolCallJson().contains("\"agentId\":\"agent-1\""));
        assertTrue(checkpoint.pendingToolCallJson().contains("\"tenantId\":\"tenant-1\""));
    }

    @Test
    void shouldEmitStepToolAndApprovalEventsDuringStreamingExecution() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "memory-forget", Map.of("memoryId", "mem-1"));
        ScriptedModel model = new ScriptedModel(List.of(Turn.toolCalls("need approval", List.of(toolCall))));
        RecordingToolGateway gateway = new RecordingToolGateway(
                ToolInvocationResult.failed(ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED, "approval-1"));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("forget memory")
                .allowedToolIds(List.of("memory-forget"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-approval")
                .agentId("agent-1")
                .versionId("version-1")
                .tenantId("tenant-1")
                .userId("user-1")
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(List.of(), callback.errors);
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.STEP_STARTED.value().equals(event.eventName())));
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.TOOL_CALL_STARTED.value().equals(event.eventName())));
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.TOOL_CALL_WAITING_USER.value().equals(event.eventName())));
        StreamApprovalRequiredEvent approvalEvent = callback.events.stream()
                .filter(event -> StreamEventType.TOOL_CALL_WAITING_USER.value().equals(event.eventName()))
                .map(StreamEvent::payload)
                .filter(StreamApprovalRequiredEvent.class::isInstance)
                .map(StreamApprovalRequiredEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("approval-1", approvalEvent.approvalId());
        assertEquals("memory-forget", approvalEvent.toolId());
        assertFalse(callback.events.stream()
                .anyMatch(event -> StreamEventType.TOOL_CALL_FINISHED.value().equals(event.eventName())));
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.STEP_FINISHED.value().equals(event.eventName())));
    }

    @Test
    void shouldEmitToolFinishedEventWithObservationDuringStreamingExecution() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "newsletter_generation", Map.of("topic", "Redis"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("generate article", List.of(toolCall)),
                Turn.finalAnswer("done")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok("""
                {"artifactType":"newsletter","format":"markdown","content":"# Redis article\\n\\nGenerated article body."}
                """));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("write article")
                .allowedToolIds(List.of("newsletter_generation"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-newsletter")
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        StreamToolCallEvent finishedEvent = callback.events.stream()
                .filter(event -> StreamEventType.TOOL_CALL_FINISHED.value().equals(event.eventName()))
                .map(StreamEvent::payload)
                .filter(StreamToolCallEvent.class::isInstance)
                .map(StreamToolCallEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("newsletter_generation", finishedEvent.toolId());
        assertEquals("SUCCEEDED", finishedEvent.message());
        assertTrue(finishedEvent.summary().contains("Generated article body."), finishedEvent.summary());
    }

    @Test
    void shouldEmitFinishedToolCallEventsWithResultSummaryDuringStreamingExecution() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "web_search", Map.of("query", "seahorse ai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need search", List.of(toolCall)),
                Turn.finalAnswer("done")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok("{\"sources\":[{\"title\":\"Seahorse\"}]}"));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("research")
                .allowedToolIds(List.of("web_search"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-tool-finished")
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        StreamToolCallEvent finishedEvent = callback.events.stream()
                .filter(event -> StreamEventType.TOOL_CALL_FINISHED.value().equals(event.eventName()))
                .map(StreamEvent::payload)
                .filter(StreamToolCallEvent.class::isInstance)
                .map(StreamToolCallEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("call-1", finishedEvent.toolCallId());
        assertEquals("web_search", finishedEvent.toolId());
        assertEquals("SUCCEEDED", finishedEvent.message());
        assertTrue(finishedEvent.summary().contains("Seahorse"), finishedEvent.summary());
        assertTrue(finishedEvent.finishedAt() != null);
    }

    @Test
    void shouldExecuteTextEncodedToolCallWhenModelStreamsItAsContent() {
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.finalAnswer("""
                        准备生成稿件。
                        <tool_call><function=newsletter_generation><parameter=topic>Redis</parameter><parameter=audience>architects</parameter></function></tool_call>
                        """),
                Turn.finalAnswer("稿件已基于工具 observation 生成。")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok("""
                {"artifactType":"newsletter","format":"markdown","content":"# Redis article\\n\\nGenerated article body."}
                """));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("write article")
                .allowedToolIds(List.of("newsletter_generation"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-text-tool-call")
                .build());

        assertEquals("稿件已基于工具 observation 生成。", result.finalAnswer());
        assertEquals(1, gateway.requests.size());
        ToolInvocationRequest request = gateway.requests.get(0);
        assertEquals("newsletter_generation", request.toolId());
        assertEquals("Redis", request.arguments().get("topic"));
        assertEquals("architects", request.arguments().get("audience"));
        assertFalse(model.requests.get(1).getMessages().get(1).getContent().contains("<tool_call>"));
    }

    @Test
    void shouldEmitSkillRuntimeDiagnosticsWithoutSkillContentDuringStreamingExecution() {
        AgentToolCall loadSkill = AgentToolCall.of("call-skill", LoadSkillResourceToolPortAdapter.TOOL_ID,
                Map.of("skillName", "research", "resourcePath", "SKILL.md"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need full skill", List.of(loadSkill)),
                Turn.finalAnswer("used research")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok(
                "{\"name\":\"research\",\"resourcePath\":\"SKILL.md\",\"content\":\"Use sources carefully.\"}"));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("write research plan")
                .allowedToolIds(List.of())
                .skillRuntimeBlocks(List.of(skill("research", List.of("web_search"))))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-skill-events")
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        StreamSkillEvent selected = callback.events.stream()
                .filter(event -> StreamEventType.SKILL_SELECTED.value().equals(event.eventName()))
                .map(StreamEvent::payload)
                .filter(StreamSkillEvent.class::isInstance)
                .map(StreamSkillEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("research", selected.name());
        assertEquals("rev-research", selected.revisionId());
        assertEquals(SkillInjectMode.METADATA_ONLY.name(), selected.injectMode());
        assertEquals(List.of("web_search"), selected.allowedTools());

        StreamSkillEvent loaded = callback.events.stream()
                .filter(event -> StreamEventType.SKILL_RESOURCE_LOADED.value().equals(event.eventName()))
                .map(StreamEvent::payload)
                .filter(StreamSkillEvent.class::isInstance)
                .map(StreamSkillEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("research", loaded.name());
        assertEquals("SKILL.md", loaded.resourcePath());
        assertEquals("LOADED", loaded.status());
    }

    @Test
    void shouldEmitFailedToolCallEventsWithErrorDuringStreamingExecution() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "web_search", Map.of("query", "seahorse ai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need search", List.of(toolCall)),
                Turn.finalAnswer("done")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.failed("network timeout"));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("research")
                .allowedToolIds(List.of("web_search"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-tool-failed")
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        StreamToolCallEvent finishedEvent = callback.events.stream()
                .filter(event -> StreamEventType.TOOL_CALL_FINISHED.value().equals(event.eventName()))
                .map(StreamEvent::payload)
                .filter(StreamToolCallEvent.class::isInstance)
                .map(StreamToolCallEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("FAILED", finishedEvent.message());
        assertEquals("network timeout", finishedEvent.errorCode());
        assertEquals("network timeout", finishedEvent.summary());
    }

    @Test
    void shouldEmitSourcesFromWebSearchObservationDuringStreamingExecution() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "web_search", Map.of("query", "seahorse ai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need search", List.of(toolCall)),
                Turn.finalAnswer("done")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok("""
                {"sources":[{"title":"Seahorse","url":"https://example.com","snippet":"AI source","score":0.9}]}
                """));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("research")
                .allowedToolIds(List.of("web_search"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-web-search")
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.SOURCE_FOUND.value().equals(event.eventName())
                        && String.valueOf(event.payload()).contains("https://example.com")));
    }

    @Test
    void shouldEmitMarkdownArtifactWhenExpectedOutputIsMarkdown() {
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("# Report\n\nFinal research result.")));
        KernelAgentLoop loop = kernelLoop(
                model,
                new ListingOnlyToolRegistry(),
                new RecordingToolGateway(),
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("write report")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-report")
                .expectedOutputArtifactType(OutputArtifactType.MARKDOWN)
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.ARTIFACT_CREATED.value().equals(event.eventName())
                        && String.valueOf(event.payload()).contains("Final research result.")));
    }

    private static SkillRuntimeBlock skill(String name, List<String> allowedTools) {
        return new SkillRuntimeBlock(
                name,
                "rev-" + name,
                "hash-" + name,
                "Skill " + name,
                AgentSkillCategory.PUBLIC,
                SkillInjectMode.METADATA_ONLY,
                allowedTools,
                "Use " + name + " carefully.");
    }

    private static List<String> toolIds(List<ToolDescriptor> tools) {
        return tools.stream().map(ToolDescriptor::toolId).toList();
    }

    private static KernelAgentLoop kernelLoop(
            StreamingChatModelPort modelPort,
            ToolRegistryPort toolRegistry,
            ToolGatewayPort toolGateway,
            KernelAgentLoopOptions options) {
        return kernelLoop(modelPort, toolRegistry, toolGateway, options, null);
    }

    private static KernelAgentLoop kernelLoop(
            StreamingChatModelPort modelPort,
            ToolRegistryPort toolRegistry,
            ToolGatewayPort toolGateway,
            KernelAgentLoopOptions options,
            AgentApprovalWaitHandler approvalWaitHandler) {
        return new KernelAgentLoop(new AgentLoopDependencies(
                modelPort,
                toolRegistry,
                toolGateway,
                options,
                null,
                null,
                null,
                approvalWaitHandler,
                null,
                null,
                null,
                null));
    }

    private static final class RecordingToolGateway implements ToolGatewayPort {
        private final List<ToolInvocationRequest> requests = new ArrayList<>();
        private final ToolInvocationResult result;

        private RecordingToolGateway() {
            this(ToolInvocationResult.ok("{\"temp\":21}"));
        }

        private RecordingToolGateway(ToolInvocationResult result) {
            this.result = result;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            requests.add(request);
            return result;
        }
    }

    private static final class ListingOnlyToolRegistry implements ToolRegistryPort {
        @Override
        public List<ToolDescriptor> listTools() {
            return List.of(
                    new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"),
                    new ToolDescriptor("memory-forget", "Memory Forget", "Forget memory", "{}"),
                    new ToolDescriptor("web_search", "Web Search", "Search public Web sources", "{}"),
                    new ToolDescriptor("newsletter_generation", "Newsletter", "Generate article", "{}"),
                    new ToolSearchToolPortAdapter(ToolRegistryPort.empty(), null).descriptor(),
                    new LoadSkillResourceToolPortAdapter(null).descriptor());
        }

        @Override
        public Optional<ToolPort> find(String toolId) {
            if (LoadSkillResourceToolPortAdapter.TOOL_ID.equals(toolId)) {
                return Optional.of(new LoadSkillResourceToolPortAdapter(null));
            }
            if (ToolSearchToolPortAdapter.TOOL_ID.equals(toolId)) {
                return Optional.of(new ToolSearchToolPortAdapter(ToolRegistryPort.empty(), null));
            }
            throw new AssertionError("KernelAgentLoop must invoke tools through ToolGatewayPort");
        }
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
            if (!turn.toolCalls().isEmpty()) {
                assertFalse(request.getTools().isEmpty());
            }
            callback.onContent(turn.content);
            toolCallCollector.onToolCalls(turn.toolCalls);
            callback.onComplete();
            return () -> {
            };
        }
    }

    private static final class ToolListRecordingModel implements StreamingChatModelPort {
        private List<ToolDescriptor> tools = List.of();

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            tools = List.copyOf(request.getTools());
            callback.onContent("direct answer");
            toolCallCollector.onToolCalls(List.of());
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

    private static final class RecordingStreamCallback implements StreamCallback {
        private final java.util.concurrent.CountDownLatch terminal = new java.util.concurrent.CountDownLatch(1);
        private final List<StreamEvent> events = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onEvent(String eventName, Object payload) {
            events.add(new StreamEvent(eventName, payload));
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
                return terminal.await(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private record StreamEvent(String eventName, Object payload) {
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
                    .max(java.util.Comparator.comparingLong(AgentCheckpoint::sequenceNo));
        }

        @Override
        public List<AgentCheckpoint> listByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .sorted(java.util.Comparator.comparingLong(AgentCheckpoint::sequenceNo))
                    .toList();
        }
    }
}
