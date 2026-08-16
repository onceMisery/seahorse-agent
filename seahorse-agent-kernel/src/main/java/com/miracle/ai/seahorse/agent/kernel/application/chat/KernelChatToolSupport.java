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

import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.hasText;
import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.text;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.skill.SkillSetJsonSupport;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ChartVisualizationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.FrontendDesignToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.GetDateTimeToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ImageGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryReadToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryWriteToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.SearchKnowledgeBaseToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ToolSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.WebFetchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.WebSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateOutputType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.TaskTemplateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 聊天工具准入协作者（从 {@link KernelChatInboundService} 提取）。
 * 按 §7 收敛原则外提：只负责 Agent 模式下的工具白名单推导、受控 Web 模板识别与技能合并/智能匹配。
 */
final class KernelChatToolSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KernelChatToolSupport.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final EnumSet<TaskTemplateId> CONTROLLED_WEB_AGENT_TEMPLATES = EnumSet.of(
            TaskTemplateId.DEEP_RESEARCH,
            TaskTemplateId.WEB_SUMMARY,
            TaskTemplateId.COMPARE_ANALYSIS);
    private static final List<String> CONTROLLED_WEB_RESEARCH_TOOL_IDS = List.of(
            WebSearchToolPortAdapter.TOOL_ID,
            WebFetchToolPortAdapter.TOOL_ID,
            SearchKnowledgeBaseToolPortAdapter.TOOL_ID,
            GetDateTimeToolPortAdapter.TOOL_ID);
    private static final List<String> LEGACY_DEFAULT_TOOL_IDS = List.of(
            SearchKnowledgeBaseToolPortAdapter.TOOL_ID,
            WebSearchToolPortAdapter.TOOL_ID,
            WebFetchToolPortAdapter.TOOL_ID,
            GetDateTimeToolPortAdapter.TOOL_ID,
            ImageGenerationToolPortAdapter.TOOL_ID,
            FrontendDesignToolPortAdapter.TOOL_ID,
            ChartVisualizationToolPortAdapter.TOOL_ID,
            MemoryReadToolPortAdapter.TOOL_ID,
            MemoryWriteToolPortAdapter.TOOL_ID,
            ToolSearchToolPortAdapter.TOOL_ID);

    private final KernelChatModelConfigSupport modelConfigSupport;
    private final Optional<TaskTemplateQueryInboundPort> taskTemplateQueryPort;
    private final Optional<AgentDefinitionRepositoryPort> agentDefinitionRepository;
    private final SkillSetJsonSupport skillSetJsonSupport;
    private final ChatSelectedSkillResolver chatSkillResolver;
    private final SkillSmartMatcher skillSmartMatcher;
    private final SkillSemanticMatcher skillSemanticMatcher;
    private final boolean enableSmartSkillMatching;

    KernelChatToolSupport(KernelChatModelConfigSupport modelConfigSupport,
                          Optional<TaskTemplateQueryInboundPort> taskTemplateQueryPort,
                          Optional<AgentDefinitionRepositoryPort> agentDefinitionRepository,
                          SkillSetJsonSupport skillSetJsonSupport,
                          ChatSelectedSkillResolver chatSkillResolver,
                          SkillSmartMatcher skillSmartMatcher,
                          SkillSemanticMatcher skillSemanticMatcher,
                          boolean enableSmartSkillMatching) {
        this.modelConfigSupport = Objects.requireNonNull(modelConfigSupport, "modelConfigSupport must not be null");
        this.taskTemplateQueryPort = taskTemplateQueryPort == null ? Optional.empty() : taskTemplateQueryPort;
        this.agentDefinitionRepository = agentDefinitionRepository == null
                ? Optional.empty()
                : agentDefinitionRepository;
        this.skillSetJsonSupport = Objects.requireNonNullElseGet(skillSetJsonSupport, SkillSetJsonSupport::new);
        this.chatSkillResolver = chatSkillResolver;
        this.skillSmartMatcher = skillSmartMatcher;
        this.skillSemanticMatcher = skillSemanticMatcher;
        this.enableSmartSkillMatching = enableSmartSkillMatching;
    }

    String selectedAgentId(StreamChatCommand command) {
        if (hasText(command.agentId())) {
            return command.agentId();
        }
        return defaultAgentId(command).orElse(AgentRuntimeConstants.LEGACY_REACT_AGENT_ID);
    }

    Optional<String> defaultAgentId(StreamChatCommand command) {
        return taskTemplate(command)
                .map(TaskTemplate::defaultAgentId)
                .filter(KernelChatJsonSupport::hasText);
    }

    Optional<TaskTemplate> taskTemplate(StreamChatCommand command) {
        if (command == null || !hasText(command.taskTemplateId()) || taskTemplateQueryPort.isEmpty()) {
            return Optional.empty();
        }
        try {
            return taskTemplateQueryPort.get()
                    .findById(TaskTemplateId.fromValue(command.taskTemplateId()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    void validateAgentVersionSelection(StreamChatCommand command) {
        String agentId = selectedAgentId(command);
        if (!hasText(command.versionId()) || AgentRuntimeConstants.LEGACY_REACT_AGENT_ID.equals(agentId)
                || agentDefinitionRepository.isEmpty()) {
            return;
        }
        modelConfigSupport.selectedVersion(agentId, command.versionId());
    }

    List<String> allowedToolIds(StreamChatCommand command) {
        Optional<RunProfileDetails> profile = modelConfigSupport.runProfile(command);
        if (profile.isPresent()) {
            return profile.get().getToolBindings().stream()
                    .filter(Objects::nonNull)
                    .filter(binding -> binding.getEnabled() == null || binding.getEnabled() != 0)
                    .map(RunProfileToolBindingRecord::getToolId)
                    .filter(KernelChatJsonSupport::hasText)
                    .distinct()
                    .toList();
        }
        if (isControlledWebAgentTemplate(command)) {
            return CONTROLLED_WEB_RESEARCH_TOOL_IDS;
        }
        String agentId = selectedAgentId(command);
        String versionId = command.versionId();
        return modelConfigSupport.selectedVersion(agentId, versionId)
                .map(version -> {
                    List<String> toolIds = toolIdsFromToolSetJson(version.toolSetJson());
                    return toolIds.isEmpty() ? LEGACY_DEFAULT_TOOL_IDS : toolIds;
                })
                .orElse(LEGACY_DEFAULT_TOOL_IDS);
    }

    boolean explicitToolAllowlist(StreamChatCommand command) {
        return modelConfigSupport.runProfile(command).isPresent();
    }

    List<String> allowedToolIdsByProvider(StreamChatCommand command, String provider) {
        return modelConfigSupport.runProfile(command)
                .stream()
                .flatMap(profile -> profile.getToolBindings().stream())
                .filter(Objects::nonNull)
                .filter(binding -> binding.getEnabled() == null || binding.getEnabled() != 0)
                .filter(binding -> matchesToolProvider(binding.getProvider(), provider))
                .map(RunProfileToolBindingRecord::getToolId)
                .filter(KernelChatJsonSupport::hasText)
                .distinct()
                .toList();
    }

    private boolean matchesToolProvider(String actualProvider, String expectedProvider) {
        if (!hasText(actualProvider) || !hasText(expectedProvider)) {
            return false;
        }
        String actual = actualProvider.trim();
        String expected = expectedProvider.trim();
        if ("MCP".equalsIgnoreCase(expected)) {
            return actual.equalsIgnoreCase("MCP")
                    || actual.toUpperCase(Locale.ROOT).startsWith("MCP_");
        }
        return actual.equalsIgnoreCase(expected);
    }

    private List<String> toolIdsFromToolSetJson(String toolSetJson) {
        if (toolSetJson == null || toolSetJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(toolSetJson);
            LinkedHashSet<String> toolIds = new LinkedHashSet<>();
            collectToolIds(root, toolIds);
            return List.copyOf(toolIds);
        } catch (JsonProcessingException ex) {
            LOG.warn("Agent version tool set is not valid JSON, no tools exposed: {}", toolSetJson, ex);
            return List.of();
        }
    }

    private void collectToolIds(JsonNode node, LinkedHashSet<String> toolIds) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addToolId(node.asText(), toolIds);
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectToolIds(item, toolIds);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        addToolId(text(node, "toolId"), toolIds);
        addToolId(text(node, "tool_id"), toolIds);
        addToolId(text(node, "id"), toolIds);
        addToolId(text(node, "name"), toolIds);
        collectToolIds(node.get("tools"), toolIds);
        collectToolIds(node.get("toolIds"), toolIds);
        collectToolIds(node.get("tool_ids"), toolIds);
        collectToolIds(node.get("selectedTools"), toolIds);
    }

    private void addToolId(String toolId, LinkedHashSet<String> toolIds) {
        if (toolId != null && !toolId.isBlank()) {
            toolIds.add(toolId.trim());
        }
    }

    OutputArtifactType expectedOutputArtifactType(StreamChatCommand command) {
        Optional<OutputArtifactType> templateType = taskTemplate(command)
                .map(TaskTemplate::defaultOutputType)
                .map(this::outputArtifactType);
        if (templateType.isPresent()) {
            return templateType.get();
        }
        return isControlledWebAgentTemplate(command) ? OutputArtifactType.MARKDOWN : null;
    }

    private OutputArtifactType outputArtifactType(TaskTemplateOutputType outputType) {
        if (outputType == null || outputType == TaskTemplateOutputType.PLAIN_TEXT) {
            return null;
        }
        return OutputArtifactType.MARKDOWN;
    }

    boolean isControlledWebAgentTemplate(StreamChatCommand command) {
        if (!hasText(command.taskTemplateId())) {
            return false;
        }
        try {
            return CONTROLLED_WEB_AGENT_TEMPLATES.contains(TaskTemplateId.fromValue(command.taskTemplateId()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    boolean hasVersionBoundSkills(AgentVersion version) {
        return version != null && version.skillSetJson() != null
                && !skillSetJsonSupport.fromJson(version.skillSetJson()).skills().isEmpty();
    }

    List<String> agentRunSnapshotToolIds(StreamChatCommand command, Optional<AgentVersion> version) {
        List<String> toolIds = new java.util.ArrayList<>(allowedToolIds(command));
        if (version.isPresent() && !toolIds.contains("load_skill") && hasVersionBoundSkills(version.get())) {
            toolIds.add("load_skill");
        }
        return List.copyOf(toolIds);
    }

    /**
     * Merge version-bound skills with per-turn selected skills.
     * Version-bound skills take priority on name collision (published contract).
     *
     * <p>智能匹配逻辑（优先级）：
     * <ol>
     *   <li>语义匹配（SkillSemanticMatcher）：基于 Embedding 向量的深度语义理解</li>
     *   <li>规则匹配（SkillSmartMatcher）：基于关键词的规则匹配（降级方案）</li>
     * </ol>
     *
     * @throws IllegalStateException if selectedSkillNames is non-empty but resolver is unavailable
     */
    List<SkillRuntimeBlock> mergeSkills(Optional<AgentVersion> selectedVersion,
                                        StreamChatCommand command,
                                        String tenantId) {
        // Version-bound skills (from published Agent version snapshot)
        List<SkillRuntimeBlock> versionBound = List.of();
        if (selectedVersion != null && selectedVersion.isPresent()) {
            versionBound = skillSetJsonSupport.fromJson(selectedVersion.get().skillSetJson()).skills();
        }
        // Per-turn selected skills (from chat input)
        boolean hasPerTurnSelection = command.selectedSkillNames() != null
                && !command.selectedSkillNames().isEmpty();
        if (hasPerTurnSelection && chatSkillResolver == null) {
            throw new IllegalStateException(
                    "selectedSkillNames provided but ChatSelectedSkillResolver is not configured "
                            + "(AgentSkillRepositoryPort bean is missing)");
        }
        List<SkillRuntimeBlock> perTurn = List.of();
        if (hasPerTurnSelection) {
            perTurn = chatSkillResolver.resolve(tenantId, command.selectedSkillNames());
        }

        // 智能匹配：当没有任何 Skill 时，尝试根据用户问题自动匹配
        if (versionBound.isEmpty() && perTurn.isEmpty() && enableSmartSkillMatching && chatSkillResolver != null) {
            List<String> recommendations = matchSkillsIntelligently(tenantId, command.question());
            if (!recommendations.isEmpty()) {
                LOG.info("Smart skill matching triggered: questionLength={}, recommendations={}",
                        command.question().length(), recommendations);
                perTurn = chatSkillResolver.resolve(tenantId, recommendations);
            }
        }

        if (versionBound.isEmpty() && perTurn.isEmpty()) {
            return List.of();
        }
        if (perTurn.isEmpty()) {
            return versionBound;
        }
        if (versionBound.isEmpty()) {
            return perTurn;
        }
        // Merge: version-bound takes priority on name collision
        Map<String, SkillRuntimeBlock> merged = new LinkedHashMap<>();
        for (SkillRuntimeBlock block : perTurn) {
            merged.put(block.name(), block);
        }
        for (SkillRuntimeBlock block : versionBound) {
            merged.put(block.name(), block);
        }
        return List.copyOf(merged.values());
    }

    /**
     * 智能匹配 Skill，优先使用语义匹配，降级到规则匹配。
     *
     * @param tenantId 租户 ID
     * @param question 用户问题
     * @return 推荐的 Skill 名称列表
     */
    List<String> matchSkillsIntelligently(String tenantId, String question) {
        // 优先使用语义匹配（基于 Embedding 向量）
        if (skillSemanticMatcher != null) {
            try {
                List<String> semanticResults = skillSemanticMatcher.match(tenantId, question);
                if (!semanticResults.isEmpty()) {
                    LOG.debug("Using semantic matching results: {}", semanticResults);
                    return semanticResults;
                }
            } catch (Exception ex) {
                LOG.warn("Semantic matching failed, falling back to rule-based matching: {}", ex.getMessage());
            }
        }

        // 降级到规则匹配（基于关键词）
        if (skillSmartMatcher != null) {
            try {
                List<String> ruleResults = skillSmartMatcher.match(tenantId, question);
                LOG.debug("Using rule-based matching results: {}", ruleResults);
                return ruleResults;
            } catch (Exception ex) {
                LOG.error("Rule-based matching failed: {}", ex.getMessage(), ex);
            }
        }

        return List.of();
    }
}
