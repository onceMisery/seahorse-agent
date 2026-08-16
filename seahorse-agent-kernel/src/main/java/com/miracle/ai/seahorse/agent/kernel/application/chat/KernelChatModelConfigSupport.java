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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.booleanValue;
import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.doubleValue;
import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.firstText;
import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.hasText;
import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.intValue;
import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.putTextIfPresent;

/**
 * Agent 运行 Profile 与模型配置解析协作者（从 {@link KernelChatInboundService} 提取）。
 * 按 §7 收敛原则外提：只负责 run profile 查询与 model/executor 配置解析，通过端口交互。
 */
final class KernelChatModelConfigSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KernelChatModelConfigSupport.class);
    private static final double DEFAULT_AGENT_TEMPERATURE = 0.3D;
    private static final String MODEL_CONFIG_MODEL_ID = "modelId";
    private static final String MODEL_CONFIG_MODEL = "model";
    private static final String MODEL_CONFIG_TEMPERATURE = "temperature";
    private static final String MODEL_CONFIG_TOP_P = "topP";
    private static final String MODEL_CONFIG_TOP_K = "topK";
    private static final String MODEL_CONFIG_MAX_TOKENS = "maxTokens";
    private static final String MODEL_CONFIG_THINKING = "thinking";

    private final ObjectMapper objectMapper;
    private final Optional<RunProfileInboundPort> runProfilePort;
    private final Optional<AgentDefinitionRepositoryPort> agentDefinitionRepository;
    private final Optional<ReActExecutorPort> agentLoop;

    KernelChatModelConfigSupport(ObjectMapper objectMapper,
                                 Optional<RunProfileInboundPort> runProfilePort,
                                 Optional<AgentDefinitionRepositoryPort> agentDefinitionRepository,
                                 Optional<ReActExecutorPort> agentLoop) {
        this.objectMapper = objectMapper;
        this.runProfilePort = runProfilePort;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentLoop = agentLoop;
    }

    Optional<RunProfileDetails> runProfile(StreamChatCommand command) {
        if (command == null || runProfilePort.isEmpty()) {
            return Optional.empty();
        }
        if (command.runProfileId() != null) {
            return runProfilePort.get().findById(command.userId(), command.runProfileId());
        }
        return runProfilePort.get().findAppliedToConversation(command.userId(), command.conversationId());
    }

    Optional<AgentVersion> selectedVersion(String agentId, String versionId) {
        if (agentDefinitionRepository.isEmpty() || !hasText(agentId)
                || AgentRuntimeConstants.LEGACY_REACT_AGENT_ID.equals(agentId)) {
            return Optional.empty();
        }
        AgentDefinitionRepositoryPort repository = agentDefinitionRepository.get();
        if (hasText(versionId)) {
            return Optional.of(repository.findVersion(agentId, versionId)
                    .orElseThrow(() -> new IllegalArgumentException("Agent version does not exist")));
        }
        return repository.latestVersion(agentId);
    }

    String effectiveExecutorEngine(StreamChatCommand command) {
        return runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getExecutorEngine)
                .filter(KernelChatJsonSupport::hasText)
                .orElseGet(() -> hasText(command.preferredExecutorEngine())
                        ? command.preferredExecutorEngine()
                        : command.chatMode() == ChatMode.AGENT
                                ? agentLoop.map(ReActExecutorPort::engineId).orElse("kernel")
                                : "kernel");
    }

    Long effectiveRunProfileId(StreamChatCommand command) {
        if (command.runProfileId() != null) {
            return command.runProfileId();
        }
        return runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getId)
                .orElse(null);
    }

    String effectiveExecutorConfigJson(StreamChatCommand command) {
        return runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getExecutorConfigJson)
                .filter(KernelChatJsonSupport::hasText)
                .orElse(null);
    }

    Map<String, Object> effectiveExecutorConfig(StreamChatCommand command) {
        String json = effectiveExecutorConfigJson(command);
        if (!hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        } catch (JsonProcessingException ex) {
            LOG.warn("Run profile executorConfig is not valid JSON, ignoring it: conversationId={}, runProfileId={}",
                    command.conversationId(), effectiveRunProfileId(command), ex);
            return Map.of();
        }
    }

    void appendRunProfileSnapshot(Map<String, Object> snapshot, StreamChatCommand command) {
        runProfile(command).ifPresent(details -> {
            RunProfileRecord profile = details.getProfile();
            if (profile == null) {
                return;
            }
            Map<String, Object> profileSnapshot = new LinkedHashMap<>();
            profileSnapshot.put("id", profile.getId());
            profileSnapshot.put("name", profile.getName());
            profileSnapshot.put("roleCardId", profile.getRoleCardId());
            profileSnapshot.put("executorEngine", profile.getExecutorEngine());
            snapshot.put("runProfile", profileSnapshot);
            putJsonSnapshot(snapshot, "executorConfig", profile.getExecutorConfigJson());
            putJsonSnapshot(snapshot, "profileModelConfig", profile.getModelConfigJson());
            putJsonSnapshot(snapshot, "memoryScope", profile.getMemoryScopeJson());
            putJsonSnapshot(snapshot, "guardrailConfig", profile.getGuardrailConfigJson());
        });
    }

    void putJsonSnapshot(Map<String, Object> snapshot, String key, String json) {
        if (!hasText(json)) {
            return;
        }
        try {
            snapshot.put(key, objectMapper.readTree(json));
        } catch (JsonProcessingException ex) {
            snapshot.put(key, json);
        }
    }

    Map<String, Object> modelConfigSnapshot(String agentId, String versionId) {
        return modelConfigSnapshot(modelExecutionConfig(agentId, versionId));
    }

    Map<String, Object> modelConfigSnapshot(AgentModelExecutionConfig modelConfig) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (modelConfig.modelId() != null) {
            snapshot.put("modelId", modelConfig.modelId());
        }
        snapshot.put("temperature", modelConfig.samplingOptions().getTemperature());
        if (modelConfig.samplingOptions().getTopP() != null) {
            snapshot.put("topP", modelConfig.samplingOptions().getTopP());
        }
        if (modelConfig.samplingOptions().getTopK() != null) {
            snapshot.put("topK", modelConfig.samplingOptions().getTopK());
        }
        if (modelConfig.samplingOptions().getMaxTokens() != null) {
            snapshot.put("maxTokens", modelConfig.samplingOptions().getMaxTokens());
        }
        if (modelConfig.samplingOptions().getThinking() != null) {
            snapshot.put("thinking", modelConfig.samplingOptions().getThinking());
        }
        return snapshot;
    }

    AgentModelExecutionConfig modelExecutionConfig(String agentId, String versionId) {
        return selectedVersion(agentId, versionId)
                .map(this::modelExecutionConfig)
                .orElseGet(AgentModelExecutionConfig::defaults);
    }

    AgentModelExecutionConfig effectiveModelExecutionConfig(
            StreamChatCommand command,
            String agentId,
            String versionId) {
        AgentModelExecutionConfig base = modelExecutionConfig(agentId, versionId);
        return runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getModelConfigJson)
                .filter(KernelChatJsonSupport::hasText)
                .map(json -> modelExecutionConfig(json, base, "run profile", command.conversationId()))
                .orElse(base);
    }

    AgentModelExecutionConfig modelExecutionConfig(AgentVersion version) {
        if (version == null || version.modelConfigJson() == null || version.modelConfigJson().isBlank()) {
            return AgentModelExecutionConfig.defaults();
        }
        return modelExecutionConfig(
                version.modelConfigJson(),
                AgentModelExecutionConfig.defaults(),
                "agent version",
                version.agentId() + ":" + version.versionId());
    }

    AgentModelExecutionConfig modelExecutionConfig(
            String modelConfigJson,
            AgentModelExecutionConfig fallback,
            String source,
            String sourceId) {
        AgentModelExecutionConfig base = fallback == null ? AgentModelExecutionConfig.defaults() : fallback;
        try {
            JsonNode root = objectMapper.readTree(modelConfigJson);
            if (root == null || !root.isObject()) {
                return base;
            }
            ChatSamplingOptions baseSampling = base.samplingOptions();
            return new AgentModelExecutionConfig(
                    firstText(root, MODEL_CONFIG_MODEL_ID, MODEL_CONFIG_MODEL, base.modelId()),
                    ChatSamplingOptions.builder()
                            .temperature(doubleValue(root, MODEL_CONFIG_TEMPERATURE, baseSampling.getTemperature()))
                            .topP(doubleValue(root, MODEL_CONFIG_TOP_P, baseSampling.getTopP()))
                            .topK(intValue(root, MODEL_CONFIG_TOP_K, baseSampling.getTopK()))
                            .maxTokens(intValue(root, MODEL_CONFIG_MAX_TOKENS, baseSampling.getMaxTokens()))
                            .thinking(booleanValue(root, MODEL_CONFIG_THINKING, baseSampling.getThinking()))
                            .build());
        } catch (JsonProcessingException ex) {
            LOG.warn("{} model config is not valid JSON, fallback to base config: sourceId={}",
                    source, sourceId, ex);
            return base;
        }
    }

    record AgentModelExecutionConfig(String modelId, ChatSamplingOptions samplingOptions) {

        AgentModelExecutionConfig {
            modelId = modelId == null || modelId.isBlank() ? null : modelId.trim();
            samplingOptions = Objects.requireNonNullElseGet(samplingOptions, () -> ChatSamplingOptions.builder()
                    .temperature(DEFAULT_AGENT_TEMPERATURE)
                    .build());
        }

        static AgentModelExecutionConfig defaults() {
            return new AgentModelExecutionConfig(null, ChatSamplingOptions.builder()
                    .temperature(DEFAULT_AGENT_TEMPERATURE)
                    .build());
        }
    }
}
