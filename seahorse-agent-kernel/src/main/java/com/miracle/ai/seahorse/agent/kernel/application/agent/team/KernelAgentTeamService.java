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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.AgentHandoffCreateCommand;
import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.KernelAgentHandoffService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamEdge;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMember;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamNodeRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamNodeStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRun;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantConstants;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentTeamCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentTeamInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentTeamRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentTeamRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 团队编排服务（Multi-Agent A2A 设计 §6 P1）。
 *
 * <p>SUPERVISOR 模式：supervisor 成员通过受管 chat 规划子任务 JSON，每个子任务经
 * {@link KernelAgentHandoffService#createLocalHandoff} 分派（handoff + child run + 审计），
 * 成员由同步 chat 引擎执行，结果回 supervisor 汇总。
 * WORKFLOW_DAG 模式：按拓扑顺序执行成员节点，边条件满足才触发。
 * P1 失败策略为 fail-fast：任一节点失败即收敛其 child run 与 handoff，
 * 剩余未执行节点置 SKIPPED，团队运行置 FAILED。
 */
public class KernelAgentTeamService implements AgentTeamInboundPort {

    static final int MAX_SUBTASKS = 8;
    static final int MAX_OUTPUT_SUMMARY_LENGTH = 4000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentTeamRepositoryPort teamRepository;
    private final KernelAgentHandoffService handoffService;
    private final AgentRunInboundPort runPort;
    private final ChatInboundPort chatPort;
    private final ConversationManagementInboundPort conversationPort;
    private final Clock clock;

    public KernelAgentTeamService(AgentTeamRepositoryPort teamRepository,
                                  KernelAgentHandoffService handoffService,
                                  AgentRunInboundPort runPort,
                                  ChatInboundPort chatPort,
                                  ConversationManagementInboundPort conversationPort,
                                  Clock clock) {
        this.teamRepository = Objects.requireNonNull(teamRepository, "teamRepository must not be null");
        this.handoffService = Objects.requireNonNull(handoffService, "handoffService must not be null");
        this.runPort = Objects.requireNonNull(runPort, "runPort must not be null");
        this.chatPort = Objects.requireNonNull(chatPort, "chatPort must not be null");
        this.conversationPort = Objects.requireNonNull(conversationPort, "conversationPort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public AgentTeamDefinition createTeam(AgentTeamCreateCommand command) {
        AgentTeamCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        AgentTeamDefinition definition = new AgentTeamDefinition(
                "team_" + SnowflakeIds.nextIdString(),
                TenantConstants.resolve(safeCommand.tenantId()),
                requireText(safeCommand.name(), "team name 不能为空"),
                safeCommand.mode(),
                safeCommand.ownerTeam(),
                safeCommand.supervisorMemberId(),
                safeCommand.members(),
                safeCommand.edges(),
                true,
                now,
                now);
        return teamRepository.saveDefinition(definition);
    }

    @Override
    public List<AgentTeamDefinition> listTeams(String tenantId) {
        return teamRepository.listDefinitions(TenantConstants.resolve(tenantId));
    }

    @Override
    public AgentTeamDefinition getTeam(String teamId) {
        return teamRepository.findDefinitionById(requireText(teamId, "teamId 不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("Agent team 不存在"));
    }

    @Override
    public AgentTeamRun startTeamRun(String teamId, AgentTeamRunStartCommand command) {
        AgentTeamDefinition definition = getTeam(teamId);
        if (!definition.isActive()) {
            throw new IllegalStateException("Agent team 已停用: " + teamId);
        }
        AgentTeamRunStartCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String objective = requireText(safeCommand.objective(), "objective 不能为空");
        String userId = requireText(safeCommand.userId(), "userId 不能为空");
        Instant now = clock.instant();

        String teamRunId = "teamrun_" + SnowflakeIds.nextIdString();
        String parentRunId = anchorParentRun(teamRunId, definition, userId, objective, safeCommand.traceId());
        List<AgentTeamNodeRun> initialNodes = definition.mode() == AgentTeamMode.WORKFLOW_DAG
                ? definition.members().stream()
                        .map(member -> AgentTeamNodeRun.pending(
                                nextNodeRunId(), member.memberId(), member.agentId(), member.instruction()))
                        .toList()
                : List.<AgentTeamNodeRun>of();
        AgentTeamRun teamRun = teamRepository.saveTeamRun(
                AgentTeamRun.start(teamRunId, definition, userId, objective,
                        parentRunId, initialNodes, now));

        try {
            teamRun = definition.mode() == AgentTeamMode.SUPERVISOR
                    ? executeSupervisor(teamRun, definition, safeCommand)
                    : executeWorkflowDag(teamRun, definition, safeCommand);
        } catch (RuntimeException ex) {
            return persistFailure(teamRun, "TEAM_EXECUTION_ERROR", Objects.requireNonNullElse(
                    ex.getMessage(), ex.getClass().getSimpleName()));
        }
        return teamRun;
    }

    @Override
    public AgentTeamRun getTeamRun(String teamRunId) {
        return teamRepository.findTeamRunById(requireText(teamRunId, "teamRunId 不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("Agent team run 不存在"));
    }

    private String anchorParentRun(String teamRunId,
                                   AgentTeamDefinition definition,
                                   String userId,
                                   String objective,
                                   String traceId) {
        String conversationId = conversationPort.create(definition.tenantId() + ":" + teamRunId);
        AgentRun parentRun = runPort.startRun(new AgentRunStartCommand(
                definition.defaultSourceAgentId(),
                null,
                null,
                definition.tenantId(),
                conversationId,
                AgentRunTriggerType.TEAM,
                truncate(objective, 512),
                traceId,
                json(Map.of("teamRunId", teamRunId, "teamMode", definition.mode().name())),
                null,
                null,
                Map.of(),
                new CurrentUser(null, userId, null, null, definition.tenantId())));
        return parentRun.runId();
    }

    private AgentTeamRun executeSupervisor(AgentTeamRun teamRun,
                                           AgentTeamDefinition definition,
                                           AgentTeamRunStartCommand command) {
        AgentTeamMember supervisor = definition.member(definition.supervisorMemberId());
        String planOutput = runAgentChat(teamRun, definition, command, supervisor.agentId(),
                planningPrompt(teamRun, definition));
        List<PlannedSubtask> subtasks = parsePlan(planOutput, definition);

        List<AgentTeamNodeRun> nodes = new ArrayList<>();
        for (PlannedSubtask subtask : subtasks) {
            AgentTeamMember member = definition.member(subtask.memberId());
            AgentTeamNodeRun nodeRun = AgentTeamNodeRun.pending(
                    nextNodeRunId(), member.memberId(), member.agentId(), subtask.instruction());
            nodes.add(nodeRun);
            teamRun = teamRepository.updateTeamRun(teamRun.withNodeRuns(List.copyOf(nodes))
                    .withNodeRun(nodeRun.running(clock.instant())));
            nodeRun = teamRun.nodeRunIndex().get(member.memberId());
            DispatchOutcome outcome = dispatchAndExecute(teamRun, definition, command,
                    supervisor.agentId(), member, subtask.instruction(), nodeRun);
            nodes.set(nodes.size() - 1, outcome.nodeRun());
            teamRun = teamRun.withNodeRuns(List.copyOf(nodes));
            if (outcome.errorCode() != null) {
                return persistFailure(teamRun, outcome.errorCode(), outcome.errorMessage());
            }
            teamRun = teamRepository.updateTeamRun(teamRun);
        }

        String summaryOutput = runAgentChat(teamRun, definition, command, supervisor.agentId(),
                synthesisPrompt(teamRun));
        if (summaryOutput.isBlank()) {
            return persistFailure(teamRun, "TEAM_SUMMARY_FAILED", "supervisor 汇总输出为空");
        }
        return teamRepository.updateTeamRun(
                teamRun.succeed(truncate(summaryOutput, MAX_OUTPUT_SUMMARY_LENGTH), clock.instant()));
    }

    private AgentTeamRun executeWorkflowDag(AgentTeamRun teamRun,
                                            AgentTeamDefinition definition,
                                            AgentTeamRunStartCommand command) {
        // P1 执行策略为 fail-fast：拓扑序保证节点执行时其全部前驱已成功，
        // 因此 ALWAYS/ON_SUCCESS 条件恒满足；条件评估与 skip/retry 策略随后续阶段引入。
        List<String> order = topologicalOrder(definition);
        Map<String, AgentTeamNodeRun> nodeByMember = new LinkedHashMap<>();
        for (AgentTeamMember member : definition.members()) {
            nodeByMember.put(member.memberId(), AgentTeamNodeRun.pending(
                    nextNodeRunId(), member.memberId(), member.agentId(), member.instruction()));
        }
        teamRun = teamRepository.updateTeamRun(teamRun.withNodeRuns(List.copyOf(nodeByMember.values())));

        for (String memberId : order) {
            AgentTeamNodeRun nodeRun = nodeByMember.get(memberId);
            teamRun = teamRepository.updateTeamRun(
                    teamRun.withNodeRuns(List.copyOf(nodeByMember.values()))
                            .withNodeRun(nodeRun.running(clock.instant())));
            nodeRun = teamRun.nodeRunIndex().get(memberId);
            AgentTeamMember member = definition.member(memberId);
            DispatchOutcome outcome = dispatchAndExecute(teamRun, definition, command,
                    predecessorAgentId(definition, memberId), member,
                    instructionFor(nodeRun), nodeRun);
            nodeByMember.put(memberId, outcome.nodeRun());
            teamRun = teamRun.withNodeRuns(List.copyOf(nodeByMember.values()));
            if (outcome.errorCode() != null) {
                List<AgentTeamNodeRun> skipped = skipUnfinished(nodeByMember, definition, clock.instant());
                AgentTeamRun failed = teamRun.withNodeRuns(skipped)
                        .fail(outcome.errorCode(), outcome.errorMessage(), clock.instant());
                return teamRepository.updateTeamRun(failed);
            }
            teamRun = teamRepository.updateTeamRun(teamRun);
        }

        String summary = nodeByMember.values().stream()
                .filter(node -> node.status() == AgentTeamNodeStatus.SUCCEEDED)
                .map(node -> node.memberId() + ": " + node.outputSummary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (summary.isBlank()) {
            return persistFailure(teamRun, "TEAM_EXECUTION_ERROR", "DAG 没有成功节点");
        }
        return teamRepository.updateTeamRun(
                teamRun.succeed(truncate(summary, MAX_OUTPUT_SUMMARY_LENGTH), clock.instant()));
    }

    private DispatchOutcome dispatchAndExecute(AgentTeamRun teamRun,
                                               AgentTeamDefinition definition,
                                               AgentTeamRunStartCommand command,
                                               String sourceAgentId,
                                               AgentTeamMember member,
                                               String instruction,
                                               AgentTeamNodeRun nodeRun) {
        AgentHandoff handoff = handoffService.createLocalHandoff(new AgentHandoffCreateCommand(
                definition.tenantId(),
                teamRun.parentRunId(),
                sourceAgentId,
                member.agentId(),
                null,
                "TEAM_DISPATCH",
                null,
                truncate(instruction, 512),
                null,
                1,
                List.of(sourceAgentId),
                command.traceId()));
        if (handoff.status() != AgentHandoffStatus.RUNNING) {
            String errorCode = handoff.failureCode() == null ? "TEAM_DISPATCH_DENIED"
                    : handoff.failureCode().name();
            AgentTeamNodeRun denied = nodeRun.withDispatch(handoff.handoffId(), handoff.childRunId())
                    .fail(errorCode, "handoff 分派被拒绝", clock.instant());
            return new DispatchOutcome(denied, errorCode, "handoff 分派被拒绝: " + handoff.handoffId());
        }
        nodeRun = nodeRun.withDispatch(handoff.handoffId(), handoff.childRunId());

        String output;
        try {
            output = runAgentChat(teamRun, definition, command, member.agentId(), instruction);
        } catch (RuntimeException ex) {
            String message = Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName());
            convergeChildRun(handoff.childRunId(), false, "MEMBER_RUN_FAILED", message);
            handoffService.completeOnTerminal(handoff.handoffId(), false,
                    AgentHandoffFailureCode.CHILD_RUN_FAILED);
            return new DispatchOutcome(
                    nodeRun.fail("MEMBER_RUN_FAILED", message, clock.instant()),
                    "MEMBER_RUN_FAILED",
                    "成员执行失败: " + member.memberId());
        }
        convergeChildRun(handoff.childRunId(), true, null, null);
        handoffService.completeOnTerminal(handoff.handoffId(), true, null);
        return new DispatchOutcome(
                nodeRun.succeed(truncate(output, MAX_OUTPUT_SUMMARY_LENGTH), clock.instant()),
                null,
                null);
    }

    private void convergeChildRun(String childRunId, boolean success, String errorCode, String errorMessage) {
        if (childRunId == null || childRunId.isBlank()) {
            return;
        }
        if (success) {
            runPort.succeed(childRunId);
        } else {
            runPort.fail(childRunId, errorCode, errorMessage);
        }
    }

    private String runAgentChat(AgentTeamRun teamRun,
                                AgentTeamDefinition definition,
                                AgentTeamRunStartCommand command,
                                String agentId,
                                String prompt) {
        String conversationId = conversationPort.create(definition.tenantId() + ":" + teamRun.teamRunId());
        CollectingCallback callback = new CollectingCallback();
        chatPort.streamChat(new StreamChatCommand(
                prompt,
                conversationId,
                teamRun.teamRunId(),
                command.userId(),
                false,
                ChatMode.AGENT,
                agentId,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                definition.tenantId()), callback);
        if (callback.error() != null) {
            throw new IllegalStateException("成员 chat 执行失败: "
                    + Objects.requireNonNullElse(callback.error().getMessage(),
                    callback.error().getClass().getSimpleName()));
        }
        return callback.output();
    }

    private List<PlannedSubtask> parsePlan(String planOutput, AgentTeamDefinition definition) {
        String json = extractJsonObject(planOutput);
        if (json == null) {
            throw new IllegalArgumentException("TEAM_PLAN_INVALID: 输出中不存在 JSON 对象");
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("TEAM_PLAN_INVALID: JSON 解析失败");
        }
        JsonNode subtasks = root.path("subtasks");
        if (!subtasks.isArray() || subtasks.isEmpty()) {
            throw new IllegalArgumentException("TEAM_PLAN_INVALID: subtasks 必须是非空数组");
        }
        if (subtasks.size() > MAX_SUBTASKS) {
            throw new IllegalArgumentException("TEAM_PLAN_INVALID: 子任务数超过上限 " + MAX_SUBTASKS);
        }
        List<PlannedSubtask> plan = new ArrayList<>();
        for (JsonNode subtask : subtasks) {
            String memberId = AgentTeamMember.normalize(subtask.path("memberId").asText(""));
            String instruction = AgentTeamMember.normalize(subtask.path("instruction").asText(""));
            if (memberId.isEmpty() || instruction.isEmpty()) {
                throw new IllegalArgumentException("TEAM_PLAN_INVALID: 子任务缺少 memberId 或 instruction");
            }
            definition.member(memberId);
            plan.add(new PlannedSubtask(memberId, instruction));
        }
        return List.copyOf(plan);
    }

    private String planningPrompt(AgentTeamRun teamRun, AgentTeamDefinition definition) {
        StringBuilder roster = new StringBuilder();
        for (AgentTeamMember member : definition.members()) {
            roster.append("- memberId=").append(member.memberId())
                    .append(" agentId=").append(member.agentId())
                    .append(" role=").append(member.role())
                    .append(" instruction=").append(member.instruction())
                    .append('\n');
        }
        return "你是团队 supervisor。任务目标：" + teamRun.objective() + "\n"
                + "团队成员：\n" + roster
                + "请把任务拆解为子任务并分派给成员。只输出 JSON，格式："
                + "{\"subtasks\":[{\"memberId\":\"成员ID\",\"instruction\":\"子任务说明\"}]}，不要输出其他内容。";
    }

    private String synthesisPrompt(AgentTeamRun teamRun) {
        StringBuilder outputs = new StringBuilder();
        for (AgentTeamNodeRun node : teamRun.nodeRuns()) {
            outputs.append("- ").append(node.memberId()).append("（").append(node.status()).append("）：")
                    .append(node.outputSummary()).append('\n');
        }
        return "你是团队 supervisor。任务目标：" + teamRun.objective() + "\n"
                + "成员输出：\n" + outputs
                + "请基于成员输出汇总最终结果，不要分派新任务。";
    }

    private List<String> topologicalOrder(AgentTeamDefinition definition) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (AgentTeamMember member : definition.members()) {
            inDegree.putIfAbsent(member.memberId(), 0);
            adjacency.putIfAbsent(member.memberId(), new ArrayList<>());
        }
        for (AgentTeamEdge edge : definition.edges()) {
            adjacency.get(edge.sourceMemberId()).add(edge.targetMemberId());
            inDegree.merge(edge.targetMemberId(), 1, Integer::sum);
        }
        Deque<String> ready = new ArrayDeque<>();
        for (AgentTeamMember member : definition.members()) {
            if (inDegree.get(member.memberId()) == 0) {
                ready.add(member.memberId());
            }
        }
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String current = ready.poll();
            order.add(current);
            for (String next : adjacency.get(current)) {
                int remaining = inDegree.merge(next, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(next);
                }
            }
        }
        return order;
    }

    private String predecessorAgentId(AgentTeamDefinition definition, String memberId) {
        return definition.edges().stream()
                .filter(edge -> edge.targetMemberId().equals(memberId))
                .map(AgentTeamEdge::sourceMemberId)
                .map(definition::member)
                .map(AgentTeamMember::agentId)
                .findFirst()
                .orElseGet(definition::defaultSourceAgentId);
    }

    private String instructionFor(AgentTeamNodeRun nodeRun) {
        String instruction = nodeRun.instruction();
        return instruction.isBlank() ? "执行你的团队职责" : instruction;
    }

    private List<AgentTeamNodeRun> skipUnfinished(Map<String, AgentTeamNodeRun> nodeByMember,
                                                  AgentTeamDefinition definition,
                                                  Instant now) {
        List<AgentTeamNodeRun> nodes = new ArrayList<>();
        for (AgentTeamMember member : definition.members()) {
            AgentTeamNodeRun node = nodeByMember.get(member.memberId());
            nodes.add(node.status().isTerminal() ? node : node.skip(now));
        }
        return nodes;
    }

    private AgentTeamRun persistFailure(AgentTeamRun teamRun, String errorCode, String errorMessage) {
        Instant now = clock.instant();
        List<AgentTeamNodeRun> nodes = teamRun.nodeRuns().stream()
                .map(node -> node.status().isTerminal() ? node : node.skip(now))
                .toList();
        AgentTeamRun failed = teamRun.withNodeRuns(nodes).fail(errorCode, errorMessage, now);
        return teamRepository.updateTeamRun(failed);
    }

    private String extractJsonObject(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return cleaned.substring(start, end + 1);
    }

    private String nextNodeRunId() {
        return "teamnode_" + SnowflakeIds.nextIdString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private String json(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize team JSON payload", ex);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private record PlannedSubtask(String memberId, String instruction) {
    }

    /**
     * 单个成员分派与执行的结果：errorCode 为 null 表示成功；否则为 fail-fast 失败码。
     */
    private record DispatchOutcome(AgentTeamNodeRun nodeRun, String errorCode, String errorMessage) {
    }

    /**
     * 收集 chat 引擎输出内容的回调：onContent 累积文本，onError 记录失败。
     * 引擎同步执行回调，因此 {@code runAgentChat} 返回时结果已就绪。
     */
    private static final class CollectingCallback implements StreamCallback {

        private final StringBuilder content = new StringBuilder();
        private volatile Throwable error;

        @Override
        public void onContent(String chunk) {
            if (chunk != null) {
                content.append(chunk);
            }
        }

        @Override
        public void onError(Throwable error) {
            this.error = Objects.requireNonNullElse(error,
                    new IllegalStateException("chat callback failed without error"));
        }

        @Override
        public void onComplete() {
            // 同步引擎：返回即完成，无需额外终态标记
        }

        String output() {
            return content.toString().trim();
        }

        Throwable error() {
            return error;
        }
    }
}
