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

package com.miracle.ai.seahorse.agent.kernel.application.agent.team;

import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.DefaultMeshPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.KernelAgentHandoffService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamEdge;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamEdgeCondition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMember;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamNodeStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRunStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentTeamCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentTeamRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentTeamRepositoryPort;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentTeamServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldRejectDuplicateMemberAgentIds() {
        KernelAgentTeamService service = newService(new InMemoryTeamRepository(), new InMemoryHandoffRepository(), new FakeAgentRunPort(), new RecordingChat(responses -> "ok"));
        AgentTeamCreateCommand command = new AgentTeamCreateCommand(
                "tenant-1", "team", AgentTeamMode.SUPERVISOR, "org", "sup",
                List.of(member("sup", "agent-1"), member("dup", "agent-1")),
                List.of());
        assertThrows(IllegalArgumentException.class, () -> service.createTeam(command));
    }

    @Test
    void shouldRejectSupervisorModeWithoutSupervisorMember() {
        KernelAgentTeamService service = newService(new InMemoryTeamRepository(), new InMemoryHandoffRepository(), new FakeAgentRunPort(), new RecordingChat(responses -> "ok"));
        AgentTeamCreateCommand command = new AgentTeamCreateCommand(
                "tenant-1", "team", AgentTeamMode.SUPERVISOR, "org", "ghost",
                List.of(member("sup", "agent-1"), member("w", "agent-2")),
                List.of());
        assertThrows(IllegalArgumentException.class, () -> service.createTeam(command));
    }

    @Test
    void shouldRejectCyclicWorkflowDag() {
        KernelAgentTeamService service = newService(new InMemoryTeamRepository(), new InMemoryHandoffRepository(), new FakeAgentRunPort(), new RecordingChat(responses -> "ok"));
        AgentTeamCreateCommand command = new AgentTeamCreateCommand(
                "tenant-1", "team", AgentTeamMode.WORKFLOW_DAG, "org", null,
                List.of(member("a", "agent-1"), member("b", "agent-2")),
                List.of(new AgentTeamEdge("a", "b", AgentTeamEdgeCondition.ALWAYS),
                        new AgentTeamEdge("b", "a", AgentTeamEdgeCondition.ALWAYS)));
        assertThrows(IllegalArgumentException.class, () -> service.createTeam(command));
    }

    @Test
    void shouldExecuteSupervisorPlanDispatchAndSynthesis() {
        RecordingChat chat = new RecordingChat(responses -> switch (responses.size()) {
            case 1 -> "{\"subtasks\":[{\"memberId\":\"researcher\",\"instruction\":\"调查主题\"},"
                    + "{\"memberId\":\"writer\",\"instruction\":\"撰写报告\"}]}";
            case 4 -> "最终汇总";
            default -> "成员输出-" + responses.size();
        });
        InMemoryTeamRepository repository = new InMemoryTeamRepository();
        InMemoryHandoffRepository handoffRepository = new InMemoryHandoffRepository();
        FakeAgentRunPort runPort = new FakeAgentRunPort();
        KernelAgentTeamService service = newService(repository, handoffRepository, runPort, chat);

        AgentTeamDefinition definition = service.createTeam(new AgentTeamCreateCommand(
                "tenant-1", "research-team", AgentTeamMode.SUPERVISOR, "org", "sup",
                List.of(member("sup", "agent-sup"), member("researcher", "agent-r"),
                        member("writer", "agent-w")),
                List.of()));
        AgentTeamRun run = service.startTeamRun(definition.teamId(),
                new AgentTeamRunStartCommand("调研并输出报告", "user-1", null));

        assertEquals(AgentTeamRunStatus.SUCCEEDED, run.status());
        assertEquals("最终汇总", run.summary());
        assertEquals(2, run.nodeRuns().size());
        assertTrue(run.nodeRuns().stream().allMatch(node -> node.status() == AgentTeamNodeStatus.SUCCEEDED));
        // 成员子任务各自经由 handoff 分派：handoff 完成态回写 + child run 收敛
        assertEquals(2, handoffRepository.handoffs.size());
        assertTrue(handoffRepository.handoffs.values().stream()
                .allMatch(handoff -> handoff.status() == AgentHandoffStatus.SUCCEEDED));
        assertEquals(2, runPort.runs.size() - countByTrigger(runPort, AgentRunTriggerType.TEAM));
        assertTrue(runPort.runs.values().stream()
                .filter(r -> r.triggerType() == AgentRunTriggerType.A2A)
                .allMatch(r -> r.status() == AgentRunStatus.SUCCEEDED));
        assertEquals(1, countByTrigger(runPort, AgentRunTriggerType.TEAM));
        // 4 次 chat：规划 + 2 个成员 + 汇总；执行 agent 顺序正确
        assertEquals("agent-sup", chat.agentIds.get(0));
        assertEquals("agent-r", chat.agentIds.get(1));
        assertEquals("agent-w", chat.agentIds.get(2));
        assertEquals("agent-sup", chat.agentIds.get(3));
    }

    @Test
    void shouldFailTeamWhenPlanIsInvalid() {
        RecordingChat chat = new RecordingChat(responses -> "我不会输出 JSON");
        KernelAgentTeamService service = newService(new InMemoryTeamRepository(),
                new InMemoryHandoffRepository(), new FakeAgentRunPort(), chat);

        AgentTeamDefinition definition = service.createTeam(new AgentTeamCreateCommand(
                "tenant-1", "team", AgentTeamMode.SUPERVISOR, "org", "sup",
                List.of(member("sup", "agent-sup"), member("w", "agent-w")), List.of()));
        AgentTeamRun run = service.startTeamRun(definition.teamId(),
                new AgentTeamRunStartCommand("目标", "user-1", null));

        assertEquals(AgentTeamRunStatus.FAILED, run.status());
        assertEquals("TEAM_EXECUTION_ERROR", run.errorCode());
        assertTrue(run.errorMessage().contains("TEAM_PLAN_INVALID"));
    }

    @Test
    void shouldFailFastAndConvergeWhenMemberExecutionFails() {
        RecordingChat chat = new RecordingChat(responses -> switch (responses.size()) {
            case 1 -> "{\"subtasks\":[{\"memberId\":\"a\",\"instruction\":\"第一步\"},"
                    + "{\"memberId\":\"b\",\"instruction\":\"第二步\"}]}";
            case 3 -> throw new IllegalStateException("模型超时");
            default -> "ok";
        });
        InMemoryTeamRepository repository = new InMemoryTeamRepository();
        InMemoryHandoffRepository handoffRepository = new InMemoryHandoffRepository();
        FakeAgentRunPort runPort = new FakeAgentRunPort();
        KernelAgentTeamService service = newService(repository, handoffRepository, runPort, chat);

        AgentTeamDefinition definition = service.createTeam(new AgentTeamCreateCommand(
                "tenant-1", "team", AgentTeamMode.SUPERVISOR, "org", "sup",
                List.of(member("sup", "agent-sup"), member("a", "agent-a"), member("b", "agent-b")),
                List.of()));
        AgentTeamRun run = service.startTeamRun(definition.teamId(),
                new AgentTeamRunStartCommand("目标", "user-1", null));

        assertEquals(AgentTeamRunStatus.FAILED, run.status());
        assertEquals("MEMBER_RUN_FAILED", run.errorCode());
        assertEquals(AgentTeamNodeStatus.SUCCEEDED, run.nodeRuns().get(0).status());
        assertEquals(AgentTeamNodeStatus.FAILED, run.nodeRuns().get(1).status());
        assertTrue(handoffRepository.handoffs.values().stream()
                .anyMatch(handoff -> handoff.status() == AgentHandoffStatus.FAILED));
        assertTrue(runPort.runs.values().stream()
                .anyMatch(r -> r.triggerType() == AgentRunTriggerType.A2A
                        && r.status() == AgentRunStatus.FAILED));
    }

    @Test
    void shouldExecuteWorkflowDagInTopologicalOrder() {
        RecordingChat chat = new RecordingChat(responses -> "输出-" + responses.size());
        InMemoryTeamRepository repository = new InMemoryTeamRepository();
        InMemoryHandoffRepository handoffRepository = new InMemoryHandoffRepository();
        FakeAgentRunPort runPort = new FakeAgentRunPort();
        KernelAgentTeamService service = newService(repository, handoffRepository, runPort, chat);

        AgentTeamDefinition definition = service.createTeam(new AgentTeamCreateCommand(
                "tenant-1", "pipeline", AgentTeamMode.WORKFLOW_DAG, "org", null,
                List.of(member("b", "agent-b"), member("a", "agent-a"), member("c", "agent-c")),
                List.of(new AgentTeamEdge("a", "b", AgentTeamEdgeCondition.ALWAYS),
                        new AgentTeamEdge("b", "c", AgentTeamEdgeCondition.ON_SUCCESS))));
        AgentTeamRun run = service.startTeamRun(definition.teamId(),
                new AgentTeamRunStartCommand("流水线目标", "user-1", null));

        assertEquals(AgentTeamRunStatus.SUCCEEDED, run.status());
        assertEquals(3, run.nodeRuns().size());
        assertTrue(run.nodeRuns().stream().allMatch(node -> node.status() == AgentTeamNodeStatus.SUCCEEDED));
        // 拓扑序：a 先于 b，b 先于 c
        List<String> dispatchedAgents = chat.agentIds.subList(0, 3);
        assertTrue(dispatchedAgents.indexOf("agent-a") < dispatchedAgents.indexOf("agent-b"));
        assertTrue(dispatchedAgents.indexOf("agent-b") < dispatchedAgents.indexOf("agent-c"));
        // 边前驱成员作为 handoff source
        AgentHandoff edgeHandoff = handoffRepository.handoffs.values().stream()
                .filter(handoff -> "agent-a".equals(handoff.sourceAgentId()))
                .findFirst()
                .orElseThrow();
        assertEquals("agent-b", edgeHandoff.targetAgentId());
        assertTrue(run.summary().contains("输出-"));
    }

    private static long countByTrigger(FakeAgentRunPort runPort, AgentRunTriggerType triggerType) {
        return runPort.runs.values().stream().filter(run -> run.triggerType() == triggerType).count();
    }

    private static KernelAgentTeamService newService(AgentTeamRepositoryPort repository, ChatInboundPort chat) {
        return newService(repository, new InMemoryHandoffRepository(), new FakeAgentRunPort(), chat);
    }

    private static KernelAgentTeamService newService(AgentTeamRepositoryPort repository,
                                                     InMemoryHandoffRepository handoffRepository,
                                                     FakeAgentRunPort runPort,
                                                     ChatInboundPort chat) {
        KernelAgentHandoffService handoffService = new KernelAgentHandoffService(
                handoffRepository, runPort, new DefaultMeshPolicyPort(), null, FIXED_CLOCK);
        return new KernelAgentTeamService(repository, handoffService, runPort, chat,
                new FakeConversationPort(), FIXED_CLOCK);
    }

    private static AgentTeamMember member(String memberId, String agentId) {
        return new AgentTeamMember(memberId, agentId, "role-" + memberId, "指令-" + memberId);
    }

    @FunctionalInterface
    private interface ResponseFactory {
        String response(List<String> previousPrompts);
    }

    private static final class RecordingChat implements ChatInboundPort {

        private final ResponseFactory factory;
        private final List<String> prompts = new ArrayList<>();
        final List<String> agentIds = new ArrayList<>();

        private RecordingChat(ResponseFactory factory) {
            this.factory = factory;
        }

        @Override
        public void streamChat(StreamChatCommand command,
                               com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback callback) {
            prompts.add(command.question());
            agentIds.add(command.agentId() == null ? "" : command.agentId());
            try {
                callback.onContent(factory.response(prompts));
                callback.onComplete();
            } catch (RuntimeException ex) {
                callback.onError(ex);
            }
        }

        @Override
        public void stopTask(String taskId) {
            // 测试无需停止任务
        }
    }

    private static final class FakeConversationPort
            implements com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort {

        @Override
        public String create(String userId) {
            return "conv-" + userId;
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRecord> listConversations(String userId) {
            return List.of();
        }

        @Override
        public void rename(String conversationId, String userId, String title) {
            // 测试无需改名
        }

        @Override
        public void delete(String conversationId, String userId) {
            // 测试无需删除
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord> listMessages(
                String conversationId, String userId) {
            return List.of();
        }
    }

    private static final class InMemoryTeamRepository implements AgentTeamRepositoryPort {

        private final Map<String, AgentTeamDefinition> definitions = new LinkedHashMap<>();
        private final Map<String, AgentTeamRun> teamRuns = new LinkedHashMap<>();

        @Override
        public AgentTeamDefinition saveDefinition(AgentTeamDefinition definition) {
            definitions.put(definition.teamId(), definition);
            return definition;
        }

        @Override
        public Optional<AgentTeamDefinition> findDefinitionById(String teamId) {
            return Optional.ofNullable(definitions.get(teamId));
        }

        @Override
        public List<AgentTeamDefinition> listDefinitions(String tenantId) {
            return definitions.values().stream()
                    .filter(definition -> definition.tenantId().equals(tenantId))
                    .toList();
        }

        @Override
        public AgentTeamRun saveTeamRun(AgentTeamRun teamRun) {
            teamRuns.put(teamRun.teamRunId(), teamRun);
            return teamRun;
        }

        @Override
        public Optional<AgentTeamRun> findTeamRunById(String teamRunId) {
            return Optional.ofNullable(teamRuns.get(teamRunId));
        }

        @Override
        public AgentTeamRun updateTeamRun(AgentTeamRun teamRun) {
            teamRuns.put(teamRun.teamRunId(), teamRun);
            return teamRun;
        }
    }

    private static final class InMemoryHandoffRepository implements AgentHandoffRepositoryPort {

        final Map<String, AgentHandoff> handoffs = new LinkedHashMap<>();

        @Override
        public AgentHandoff save(AgentHandoff handoff) {
            handoffs.put(handoff.handoffId(), handoff);
            return handoff;
        }

        @Override
        public AgentHandoff update(AgentHandoff handoff) {
            handoffs.put(handoff.handoffId(), handoff);
            return handoff;
        }

@Override
        public Optional<AgentHandoff> findByChildRunId(String childRunId) {
            return handoffs.values().stream()
                    .filter(handoff -> childRunId.equals(handoff.childRunId()))
                    .findFirst();
        }

        @Override
        public Optional<AgentHandoff> findById(String handoffId) {
            return Optional.ofNullable(handoffs.get(handoffId));
        }

        @Override
        public List<AgentHandoff> listByParentRunId(String tenantId, String parentRunId) {
            return List.copyOf(handoffs.values());
        }
    }

    private static final class FakeAgentRunPort implements AgentRunInboundPort {

        final Map<String, AgentRun> runs = new LinkedHashMap<>();
        private int sequence;

        @Override
        public AgentRun startRun(AgentRunStartCommand command) {
            sequence++;
            String runId = "run_" + sequence;
            String userId = command.currentUser() == null ? "user" : command.currentUser().username();
            AgentRun run = new AgentRun(runId, command.agentId(), null, command.tenantId(),
                    userId, command.conversationId(), command.triggerType(),
                    command.inputSummary(), AgentRunStatus.CREATED, command.traceId(),
                    0L, 0L, BigDecimal.ZERO, null, null, Instant.EPOCH, null);
            runs.put(runId, run);
            return run;
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep> listSteps(String runId) {
            return List.of();
        }

        @Override
        public AgentRun cancel(String runId) {
            return transition(runId, AgentRunStatus.CANCELLED);
        }

        @Override
        public AgentRun cancelExecution(String runId) {
            return transition(runId, AgentRunStatus.CANCELLED);
        }

        @Override
        public AgentRun retry(String runId) {
            return transition(runId, AgentRunStatus.RETRYING);
        }

        @Override
        public AgentRun succeed(String runId) {
            return transition(runId, AgentRunStatus.SUCCEEDED);
        }

        @Override
        public AgentRun fail(String runId, String errorCode, String errorMessage) {
            return transition(runId, AgentRunStatus.FAILED);
        }

        private AgentRun transition(String runId, AgentRunStatus status) {
            AgentRun current = runs.get(runId);
            if (current == null) {
                throw new IllegalArgumentException("run 不存在: " + runId);
            }
            AgentRun next = new AgentRun(current.runId(), current.agentId(), current.versionId(),
                    current.tenantId(), current.userId(), current.conversationId(), current.triggerType(),
                    current.inputSummary(), status, current.traceId(), current.tokenInput(),
                    current.tokenOutput(), current.costTotal(), current.errorCode(), current.errorMessage(),
                    current.startedAt(), Instant.EPOCH);
            runs.put(runId, next);
            return next;
        }
    }
}
