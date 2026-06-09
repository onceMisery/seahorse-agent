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

import com.miracle.ai.seahorse.agent.kernel.application.agent.InMemoryToolRegistry;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        KernelAgentLoop agentLoop = new KernelAgentLoop(
                model,
                toolRegistry,
                successfulWeatherGateway(),
                KernelAgentLoopOptions.defaults(),
                com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder.noop(),
                new com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver(),
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
        KernelAgentLoop agentLoop = new KernelAgentLoop(
                model,
                toolRegistry,
                successfulWeatherGateway(),
                options,
                com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder.noop(),
                new com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver(),
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
        KernelAgentLoop agentLoop = new KernelAgentLoop(
                model,
                toolRegistry,
                approvalRequiredGateway(),
                KernelAgentLoopOptions.defaults(),
                com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder.noop(),
                new com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver(),
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
        KernelAgentLoop agentLoop = new KernelAgentLoop(
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
        KernelAgentLoop agentLoop = new KernelAgentLoop(
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
        KernelAgentLoop agentLoop = new KernelAgentLoop(
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
        KernelAgentLoop agentLoop = new KernelAgentLoop(
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
        KernelAgentLoop agentLoop = new KernelAgentLoop(
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
            KernelAgentLoop agentLoop = new KernelAgentLoop(
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
