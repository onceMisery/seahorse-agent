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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 长时程续跑（P2-4 剩余）：在每一轮迭代成功结束后落一个 {@link AgentCheckpointType#STEP_BOUNDARY}
 * 检查点，下次带着同一个 runId 再次执行时从断点继续。
 *
 * <p>这解决的是"步数上限即天花板"的问题：{@code maxSteps} 约束的是<b>一次调用</b>的推理预算，
 * 而不是任务总时长。每次调用都拿到一整份新预算，任务本身可以跨任意多次调用推进。
 * 单调用的预算仍然必须存在——它是防止单次请求无限燃烧 token 的护栏，不能被移除。
 *
 * <p>三条失败即放弃的原则，保证"续跑"永远不会比"重跑"更糟：
 * <ul>
 *   <li>写入失败只记日志：检查点是加速器，不是正确性依赖；</li>
 *   <li>读取失败（无检查点 / JSON 损坏 / schema 不匹配）一律按"从头开始"处理，
 *       绝不把半截状态塞回循环；</li>
 *   <li>开关默认关闭，未开启时本类不做任何 IO。</li>
 * </ul>
 *
 * <p>落盘的消息内容与审批检查点保持同一套脱敏约定：剥离思考过程、对正文做凭证脱敏。
 * 续跑时模型看到的因此是脱敏后的历史——这与平台既有的"从审批检查点恢复"行为一致，
 * 代价是极少数含凭证字样的历史文本会被替换成占位符。
 */
final class AgentLoopCheckpoints {

    private static final Logger LOG = LoggerFactory.getLogger(AgentLoopCheckpoints.class);

    private static final String CHECKPOINT_ID_PREFIX = "checkpoint_";
    private static final int RESUME_SCHEMA = 1;
    private static final String RESUME_SCHEMA_FIELD = "resumeSchema";
    private static final String COMPLETED_STEPS_FIELD = "completedSteps";
    private static final String EXHAUSTED_TOOL_IDS_FIELD = "exhaustedToolIds";

    private final AgentCheckpointRepositoryPort repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;

    AgentLoopCheckpoints(AgentCheckpointRepositoryPort repository,
                         ObjectMapper objectMapper,
                         Clock clock,
                         boolean enabled) {
        this.repository = Objects.requireNonNullElseGet(repository, AgentCheckpointRepositoryPort::empty);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.enabled = enabled;
    }

    boolean enabled() {
        return enabled;
    }

    /**
     * 读取可续跑的状态。任何异常都退化为 {@link ResumeState#empty()}。
     */
    ResumeState load(String runId) {
        if (!enabled || isBlank(runId)) {
            return ResumeState.empty();
        }
        try {
            AgentCheckpoint latest = repository.findLatestByRunId(runId).orElse(null);
            if (latest == null || latest.checkpointType() != AgentCheckpointType.STEP_BOUNDARY) {
                return ResumeState.empty();
            }
            JsonNode state = objectMapper.readTree(latest.stateJson());
            if (state.path(RESUME_SCHEMA_FIELD).asInt(-1) != RESUME_SCHEMA) {
                LOG.info("Ignoring checkpoint with an incompatible resume schema: runId={}", runId);
                return ResumeState.empty();
            }
            int completedSteps = Math.max(0, state.path(COMPLETED_STEPS_FIELD).asInt(0));
            List<ChatMessage> messages = parseMessages(latest.messageHistoryJson());
            if (messages.isEmpty()) {
                return ResumeState.empty();
            }
            return new ResumeState(messages, completedSteps, parseToolIds(state.path(EXHAUSTED_TOOL_IDS_FIELD)));
        } catch (Exception ex) {
            LOG.warn("Failed to restore agent run checkpoint; starting from scratch: runId={}", runId, ex);
            return ResumeState.empty();
        }
    }

    void saveStepBoundary(String runId,
                          int completedSteps,
                          List<ChatMessage> messages,
                          Set<String> exhaustedToolIds) {
        if (!enabled || isBlank(runId)) {
            return;
        }
        try {
            repository.save(new AgentCheckpoint(
                    CHECKPOINT_ID_PREFIX + SnowflakeIds.nextIdString(),
                    runId,
                    null,
                    nextSequenceNo(runId),
                    AgentCheckpointType.STEP_BOUNDARY,
                    stateJson(completedSteps, exhaustedToolIds),
                    messageHistoryJson(messages),
                    null,
                    null,
                    clock.instant()));
        } catch (RuntimeException ex) {
            LOG.warn("Failed to persist agent run checkpoint; continuing without resume support: runId={}",
                    runId, ex);
        }
    }

    private long nextSequenceNo(String runId) {
        return repository.findLatestByRunId(runId)
                .map(AgentCheckpoint::sequenceNo)
                .orElse(0L) + 1L;
    }

    private String stateJson(int completedSteps, Set<String> exhaustedToolIds) {
        ObjectNode state = objectMapper.createObjectNode();
        state.put(RESUME_SCHEMA_FIELD, RESUME_SCHEMA);
        state.put(COMPLETED_STEPS_FIELD, completedSteps);
        ArrayNode toolIds = state.putArray(EXHAUSTED_TOOL_IDS_FIELD);
        if (exhaustedToolIds != null) {
            exhaustedToolIds.stream().filter(Objects::nonNull).sorted().forEach(toolIds::add);
        }
        return state.toString();
    }

    private String messageHistoryJson(List<ChatMessage> messages) {
        ArrayNode array = objectMapper.createArrayNode();
        if (messages == null) {
            return array.toString();
        }
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            ObjectNode node = array.addObject();
            node.put("role", message.getRole() == null ? null : message.getRole().name());
            node.put("content", redact(message.getContent()));
            node.put("toolCallId", message.getToolCallId());
            ArrayNode toolCalls = node.putArray("toolCalls");
            toolCallsJson(message.getToolCalls()).forEach(toolCalls::addPOJO);
        }
        return array.toString();
    }

    private List<ChatMessage> parseMessages(String messageHistoryJson)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        if (isBlank(messageHistoryJson)) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(messageHistoryJson);
        if (!(root instanceof ArrayNode array)) {
            return List.of();
        }
        List<ChatMessage> messages = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            ChatMessage parsed = parseMessage(node);
            if (parsed != null) {
                messages.add(parsed);
            }
        }
        return List.copyOf(messages);
    }

    private ChatMessage parseMessage(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        ChatRole role = parseRole(node.path("role").asText(null));
        String content = node.path("content").asText(null);
        List<AgentToolCall> toolCalls = parseToolCalls(node.path("toolCalls"));
        String toolCallId = node.path("toolCallId").asText(null);
        if (role == ChatRole.TOOL && !isBlank(toolCallId)) {
            return ChatMessage.tool(toolCallId, content);
        }
        if (!toolCalls.isEmpty()) {
            return ChatMessage.assistantToolCalls(content, toolCalls);
        }
        return new ChatMessage(role, content);
    }

    private static ChatRole parseRole(String raw) {
        if (isBlank(raw)) {
            return ChatRole.USER;
        }
        try {
            return ChatRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ChatRole.USER;
        }
    }

    private List<Map<String, Object>> toolCallsJson(List<AgentToolCall> toolCalls) {
        if (toolCalls == null) {
            return List.of();
        }
        List<Map<String, Object>> payload = new ArrayList<>(toolCalls.size());
        for (AgentToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("toolCallId", toolCall.id());
            entry.put("toolId", toolCall.toolId());
            entry.put("arguments", toolCall.arguments());
            payload.add(entry);
        }
        return payload;
    }

    private List<AgentToolCall> parseToolCalls(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<AgentToolCall> toolCalls = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            String id = item.path("toolCallId").asText(null);
            String toolId = item.path("toolId").asText(null);
            if (isBlank(id) || isBlank(toolId)) {
                continue;
            }
            Map<String, Object> arguments = parseArguments(item.path("arguments"));
            toolCalls.add(AgentToolCall.of(id, toolId, arguments));
        }
        return List.copyOf(toolCalls);
    }

    private Map<String, Object> parseArguments(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry ->
                arguments.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
        return arguments;
    }

    private static Set<String> parseToolIds(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        LinkedHashSet<String> toolIds = new LinkedHashSet<>();
        node.forEach(item -> {
            String value = item.asText(null);
            if (!isBlank(value)) {
                toolIds.add(value);
            }
        });
        return Collections.unmodifiableSet(toolIds);
    }

    private static String redact(String value) {
        return value == null ? null : CredentialTextRedactor.redactStructured(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 一次执行的续跑起点。
     *
     * @param messages       已累积的对话历史，恢复后作为循环的初始消息
     * @param completedSteps 已完成的迭代数，本次执行从该序号继续
     * @param exhaustedToolIds 已耗尽的工具 id，避免续跑后重复调用注定失败的工具
     */
    record ResumeState(List<ChatMessage> messages, int completedSteps, Set<String> exhaustedToolIds) {

        ResumeState {
            messages = messages == null ? List.of() : List.copyOf(messages);
            completedSteps = Math.max(0, completedSteps);
            exhaustedToolIds = exhaustedToolIds == null ? Set.of() : Set.copyOf(exhaustedToolIds);
        }

        static ResumeState empty() {
            return new ResumeState(List.of(), 0, Set.of());
        }

        boolean present() {
            return !messages.isEmpty();
        }
    }
}
