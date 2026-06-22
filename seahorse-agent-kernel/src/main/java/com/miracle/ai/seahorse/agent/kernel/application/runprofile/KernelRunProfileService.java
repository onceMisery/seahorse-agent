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

package com.miracle.ai.seahorse.agent.kernel.application.runprofile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileAuditSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileProductionGateCheck;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileResolvedPreview;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileRiskSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileToolBindingCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class KernelRunProfileService implements RunProfileInboundPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @NonNull
    private final RunProfileRepositoryPort repositoryPort;
    @NonNull
    private final Set<String> supportedExecutorEngines;

    public KernelRunProfileService(RunProfileRepositoryPort repositoryPort) {
        this(repositoryPort, Set.of("kernel", "agentscope"));
    }

    @Override
    public List<RunProfileRecord> list(String userId) {
        return repositoryPort.listByUser(requireText(userId, "userId must not be blank"));
    }

    @Override
    public List<String> supportedExecutorEngines() {
        return normalizedEngines().stream()
                .sorted(this::compareEngine)
                .toList();
    }

    @Override
    public Optional<RunProfileDetails> findById(String userId, Long id) {
        String safeUserId = requireText(userId, "userId must not be blank");
        if (id == null) {
            return Optional.empty();
        }
        return repositoryPort.findById(safeUserId, id)
                .map(profile -> RunProfileDetails.builder()
                        .profile(profile)
                        .toolBindings(repositoryPort.listTools(id))
                        .build());
    }

    @Override
    public Optional<RunProfileResolvedPreview> resolvePreview(String userId, Long id) {
        return findById(userId, id).map(details -> {
            RunProfileRecord profile = details.getProfile();
            List<RunProfileToolBindingRecord> enabledTools = Objects.requireNonNullElse(
                            details.getToolBindings(),
                            List.<RunProfileToolBindingRecord>of())
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(tool -> !Integer.valueOf(0).equals(tool.getEnabled()))
                    .toList();
            return RunProfileResolvedPreview.builder()
                    .runProfileId(profile.getId())
                    .roleCardId(profile.getRoleCardId())
                    .executorEngine(normalizeEngine(profile.getExecutorEngine()))
                    .executorConfigJson(profile.getExecutorConfigJson())
                    .modelConfigJson(profile.getModelConfigJson())
                    .memoryScopeJson(profile.getMemoryScopeJson())
                    .guardrailConfigJson(profile.getGuardrailConfigJson())
                    .explicitToolAllowlist(true)
                    .toolIds(toolIdsByProvider(enabledTools, "MCP", "A2A"))
                    .mcpToolIds(toolIdsForProvider(enabledTools, "MCP"))
                    .a2aAgentIds(toolIdsForProvider(enabledTools, "A2A"))
                    .build();
        });
    }

    @Override
    public Optional<RunProfileRiskSummary> riskSummary(String userId, Long id) {
        return findById(userId, id).map(details -> {
            RunProfileRecord profile = details.getProfile();
            List<RunProfileToolBindingRecord> enabledTools = Objects.requireNonNullElse(
                            details.getToolBindings(),
                            List.<RunProfileToolBindingRecord>of())
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(tool -> !Integer.valueOf(0).equals(tool.getEnabled()))
                    .toList();
            List<RunProfileRiskSummary.RiskItem> items = new java.util.ArrayList<>();
            if ("agentscope".equalsIgnoreCase(blankToNull(profile.getExecutorEngine()))) {
                items.add(risk("EXECUTOR_AGENTSCOPE", "MEDIUM", "AgentScope execution engine is enabled"));
            }
            if (profile.getRoleCardId() != null) {
                items.add(risk("ROLE_CARD_BOUND", "MEDIUM", "Role card is bound: " + profile.getRoleCardId()));
            }
            appendToolRisk(items, enabledTools, "MCP", "TOOL_MCP", "HIGH", "MCP tool is enabled: ");
            appendToolRisk(items, enabledTools, "A2A", "TOOL_A2A", "HIGH", "A2A remote agent is enabled: ");
            appendToolRisk(items, enabledTools, "OPENAPI", "TOOL_OPENAPI", "MEDIUM", "OpenAPI tool is enabled: ");
            if (jsonBoolean(profile.getMemoryScopeJson(), "longTerm")) {
                items.add(risk("MEMORY_LONG_TERM", "MEDIUM", "Long-term memory is enabled"));
            }
            if (containsHighRiskTool(items) && !jsonBoolean(profile.getGuardrailConfigJson(), "highRiskToolApproval")) {
                items.add(risk("APPROVAL_NOT_ENFORCED", "HIGH", "High-risk tool approval is not enforced"));
            }
            return RunProfileRiskSummary.builder()
                    .runProfileId(profile.getId())
                    .riskLevel(overallRiskLevel(items))
                    .riskCodes(items.stream().map(RunProfileRiskSummary.RiskItem::getCode).toList())
                    .riskItems(List.copyOf(items))
                    .build();
        });
    }

    @Override
    public Optional<RunProfileProductionGateCheck> productionGateCheck(String userId, Long id) {
        return findById(userId, id).map(details -> {
            RunProfileRecord profile = details.getProfile();
            RunProfileRiskSummary summary = riskSummary(userId, id).orElseGet(() -> RunProfileRiskSummary.builder()
                    .runProfileId(profile.getId())
                    .riskLevel("LOW")
                    .riskCodes(List.of())
                    .riskItems(List.of())
                    .build());
            List<RunProfileProductionGateCheck.CheckItem> items = new java.util.ArrayList<>();
            if (Objects.requireNonNullElse(summary.getRiskCodes(), List.<String>of())
                    .contains("APPROVAL_NOT_ENFORCED")) {
                items.add(block(
                        "APPROVAL_NOT_ENFORCED",
                        "High-risk tool approval must be enabled before production"));
            }
            if ("agentscope".equalsIgnoreCase(blankToNull(profile.getExecutorEngine()))) {
                appendAgentScopeGateItems(items, profile.getExecutorConfigJson());
            }
            List<String> blockingCodes = items.stream()
                    .filter(item -> "BLOCK".equals(item.getStatus()))
                    .map(RunProfileProductionGateCheck.CheckItem::getCode)
                    .toList();
            return RunProfileProductionGateCheck.builder()
                    .runProfileId(profile.getId())
                    .passed(blockingCodes.isEmpty())
                    .riskLevel(summary.getRiskLevel())
                    .blockingCodes(blockingCodes)
                    .checkItems(List.copyOf(items))
                    .build();
        });
    }

    @Override
    public RunProfileResolvedPreview applyToConversation(String userId, String conversationId, Long id) {
        String safeUserId = requireText(userId, "userId must not be blank");
        String safeConversationId = requireText(conversationId, "conversationId must not be blank");
        if (id == null) {
            throw new IllegalArgumentException("runProfileId must not be null");
        }
        RunProfileResolvedPreview preview = resolvePreview(safeUserId, id)
                .orElseThrow(() -> new IllegalArgumentException("run profile not found"));
        repositoryPort.applyToConversation(safeUserId, safeConversationId, id);
        return preview;
    }

    @Override
    public Optional<RunProfileDetails> findAppliedToConversation(String userId, String conversationId) {
        String safeUserId = requireText(userId, "userId must not be blank");
        String safeConversationId = requireText(conversationId, "conversationId must not be blank");
        return repositoryPort.findAppliedProfileId(safeUserId, safeConversationId)
                .flatMap(id -> findById(safeUserId, id));
    }

    @Override
    public Long save(RunProfileCommand command) {
        RunProfileCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String userId = requireText(safeCommand.getUserId(), "userId must not be blank");
        String name = requireText(safeCommand.getName(), "name must not be blank");
        RunProfileRecord record = safeCommand.getId() == null
                ? new RunProfileRecord()
                : repositoryPort.findById(userId, safeCommand.getId())
                        .orElseThrow(() -> new IllegalArgumentException("run profile not found"));
        if (safeCommand.getId() != null) {
            assertMutable(record);
        }
        record.setId(safeCommand.getId());
        record.setUserId(userId);
        record.setName(name);
        record.setDescription(blankToNull(safeCommand.getDescription()));
        record.setRoleCardId(safeCommand.getRoleCardId());
        record.setExecutorEngine(requireSupportedEngine(safeCommand.getExecutorEngine()));
        record.setExecutorConfigJson(blankToNull(safeCommand.getExecutorConfigJson()));
        record.setModelConfigJson(blankToNull(safeCommand.getModelConfigJson()));
        record.setMemoryScopeJson(blankToNull(safeCommand.getMemoryScopeJson()));
        record.setGuardrailConfigJson(blankToNull(safeCommand.getGuardrailConfigJson()));
        if (record.getApprovalStatus() == null) {
            record.setApprovalStatus("DRAFT");
        }
        if (record.getEnabled() == null) {
            record.setEnabled(0);
        }
        if (record.getDeleted() == null) {
            record.setDeleted(0);
        }
        Long id = repositoryPort.save(record);
        repositoryPort.replaceTools(id, toolRecords(id, safeCommand.getToolBindings()));
        return id;
    }

    @Override
    public void activate(String userId, Long id) {
        String safeUserId = requireText(userId, "userId must not be blank");
        if (id == null) {
            throw new IllegalArgumentException("runProfileId must not be null");
        }
        repositoryPort.findById(safeUserId, id)
                .orElseThrow(() -> new IllegalArgumentException("run profile not found"));
        repositoryPort.disableAll(safeUserId);
        repositoryPort.setEnabled(safeUserId, id, true);
    }

    @Override
    public void delete(String userId, Long id) {
        String safeUserId = requireText(userId, "userId must not be blank");
        if (id == null) {
            throw new IllegalArgumentException("runProfileId must not be null");
        }
        RunProfileRecord record = repositoryPort.findById(safeUserId, id)
                .orElseThrow(() -> new IllegalArgumentException("run profile not found"));
        assertMutable(record);
        repositoryPort.delete(safeUserId, id);
    }

    @Override
    public void submitApproval(String userId, Long id, String comment) {
        updateApproval(userId, id, "PENDING_APPROVAL", null, comment);
    }

    @Override
    public void approve(String userId, Long id, String operator, String comment) {
        updateApproval(userId, id, "APPROVED", requireText(operator, "operator must not be blank"), comment);
    }

    @Override
    public void reject(String userId, Long id, String operator, String comment) {
        updateApproval(userId, id, "REJECTED", requireText(operator, "operator must not be blank"), comment);
    }

    @Override
    public Optional<RunProfileAuditSummary> auditSummary(String userId, Long id) {
        return findById(userId, id).map(details -> {
            RunProfileRecord profile = details.getProfile();
            List<RunProfileToolBindingRecord> enabledTools = Objects.requireNonNullElse(
                            details.getToolBindings(),
                            List.<RunProfileToolBindingRecord>of())
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(tool -> !Integer.valueOf(0).equals(tool.getEnabled()))
                    .toList();
            List<String> highRiskToolIds = enabledTools.stream()
                    .filter(tool -> {
                        String provider = blankToNull(tool.getProvider());
                        return "MCP".equalsIgnoreCase(provider) || "A2A".equalsIgnoreCase(provider);
                    })
                    .map(RunProfileToolBindingRecord::getToolId)
                    .filter(Objects::nonNull)
                    .toList();
            RunProfileRiskSummary summary = riskSummary(userId, id).orElseGet(() -> RunProfileRiskSummary.builder()
                    .riskLevel("LOW")
                    .riskCodes(List.of())
                    .riskItems(List.of())
                    .build());
            return RunProfileAuditSummary.builder()
                    .runProfileId(profile.getId())
                    .approvalStatus(Objects.requireNonNullElse(blankToNull(profile.getApprovalStatus()), "DRAFT"))
                    .riskLevel(summary.getRiskLevel())
                    .runCount(0L)
                    .failureCount(0L)
                    .estimatedCost(0D)
                    .enabledToolCount(enabledTools.size())
                    .highRiskToolCount(highRiskToolIds.size())
                    .highRiskToolIds(highRiskToolIds)
                    .build();
        });
    }

    private void updateApproval(String userId, Long id, String status, String operator, String comment) {
        String safeUserId = requireText(userId, "userId must not be blank");
        if (id == null) {
            throw new IllegalArgumentException("runProfileId must not be null");
        }
        repositoryPort.findById(safeUserId, id)
                .orElseThrow(() -> new IllegalArgumentException("run profile not found"));
        repositoryPort.updateApprovalStatus(safeUserId, id, status, operator, blankToNull(comment));
    }

    private List<RunProfileToolBindingRecord> toolRecords(
            Long profileId,
            List<RunProfileToolBindingCommand> commands) {
        return Objects.requireNonNullElse(commands, List.<RunProfileToolBindingCommand>of())
                .stream()
                .filter(Objects::nonNull)
                .map(command -> {
                    RunProfileToolBindingRecord record = new RunProfileToolBindingRecord();
                    record.setProfileId(profileId);
                    record.setToolId(requireText(command.getToolId(), "toolId must not be blank"));
                    record.setProvider(requireText(command.getProvider(), "provider must not be blank"));
                    record.setEnabled(command.isEnabled() ? 1 : 0);
                    record.setDeleted(0);
                    return record;
                })
                .toList();
    }

    private String normalizeEngine(String executorEngine) {
        String normalized = blankToNull(executorEngine);
        return normalized == null ? "kernel" : normalized.toLowerCase(Locale.ROOT);
    }

    private String requireSupportedEngine(String executorEngine) {
        String normalized = normalizeEngine(executorEngine);
        if (!normalizedEngines().contains(normalized)) {
            throw new IllegalArgumentException("executorEngine is not available: " + normalized);
        }
        return normalized;
    }

    private Set<String> normalizedEngines() {
        return Objects.requireNonNullElse(supportedExecutorEngines, Set.<String>of())
                .stream()
                .map(this::blankToNull)
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private int compareEngine(String left, String right) {
        if ("kernel".equals(left) && !"kernel".equals(right)) {
            return -1;
        }
        if (!"kernel".equals(left) && "kernel".equals(right)) {
            return 1;
        }
        return left.compareTo(right);
    }

    private List<String> toolIdsForProvider(List<RunProfileToolBindingRecord> tools, String provider) {
        return tools.stream()
                .filter(tool -> provider.equalsIgnoreCase(blankToNull(tool.getProvider())))
                .map(RunProfileToolBindingRecord::getToolId)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> toolIdsByProvider(List<RunProfileToolBindingRecord> tools, String... excludedProviders) {
        return tools.stream()
                .filter(tool -> {
                    String provider = blankToNull(tool.getProvider());
                    for (String excludedProvider : excludedProviders) {
                        if (excludedProvider.equalsIgnoreCase(provider)) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(RunProfileToolBindingRecord::getToolId)
                .filter(Objects::nonNull)
                .toList();
    }

    private void appendToolRisk(
            List<RunProfileRiskSummary.RiskItem> items,
            List<RunProfileToolBindingRecord> tools,
            String provider,
            String code,
            String level,
            String messagePrefix) {
        tools.stream()
                .filter(tool -> provider.equalsIgnoreCase(blankToNull(tool.getProvider())))
                .map(RunProfileToolBindingRecord::getToolId)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(toolId -> items.add(risk(code, level, messagePrefix + toolId)));
    }

    private RunProfileRiskSummary.RiskItem risk(String code, String level, String message) {
        return RunProfileRiskSummary.RiskItem.builder()
                .code(code)
                .level(level)
                .message(message)
                .build();
    }

    private boolean containsHighRiskTool(List<RunProfileRiskSummary.RiskItem> items) {
        return items.stream()
                .map(RunProfileRiskSummary.RiskItem::getCode)
                .anyMatch(code -> "TOOL_MCP".equals(code) || "TOOL_A2A".equals(code));
    }

    private void appendAgentScopeGateItems(
            List<RunProfileProductionGateCheck.CheckItem> items,
            String executorConfigJson) {
        if (!hasJsonText(executorConfigJson, "nacosNamespace")) {
            items.add(block(
                    "AGENTSCOPE_NACOS_NAMESPACE_MISSING",
                    "AgentScope Nacos namespace is required before production"));
        } else {
            items.add(pass(
                    "AGENTSCOPE_NACOS_NAMESPACE",
                    "AgentScope Nacos namespace is configured"));
        }
        if (!hasJsonText(executorConfigJson, "nacosGroup")) {
            items.add(block(
                    "AGENTSCOPE_NACOS_GROUP_MISSING",
                    "AgentScope Nacos group is required before production"));
        } else {
            items.add(pass(
                    "AGENTSCOPE_NACOS_GROUP",
                    "AgentScope Nacos group is configured"));
        }
        if (!jsonBoolean(executorConfigJson, "studioTraceEnabled")) {
            items.add(block(
                    "AGENTSCOPE_STUDIO_TRACE_DISABLED",
                    "AgentScope Studio trace must be enabled before production"));
        } else {
            items.add(pass(
                    "AGENTSCOPE_STUDIO_TRACE",
                    "AgentScope Studio trace is enabled"));
        }
    }

    private RunProfileProductionGateCheck.CheckItem block(String code, String message) {
        return RunProfileProductionGateCheck.CheckItem.builder()
                .code(code)
                .status("BLOCK")
                .message(message)
                .build();
    }

    private RunProfileProductionGateCheck.CheckItem pass(String code, String message) {
        return RunProfileProductionGateCheck.CheckItem.builder()
                .code(code)
                .status("PASS")
                .message(message)
                .build();
    }

    private String overallRiskLevel(List<RunProfileRiskSummary.RiskItem> items) {
        if (items.stream().anyMatch(item -> "HIGH".equalsIgnoreCase(item.getLevel()))) {
            return "HIGH";
        }
        if (items.stream().anyMatch(item -> "MEDIUM".equalsIgnoreCase(item.getLevel()))) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean jsonBoolean(String json, String fieldName) {
        String safeJson = blankToNull(json);
        if (safeJson == null || fieldName == null || fieldName.isBlank()) {
            return false;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(safeJson);
            JsonNode value = root.get(fieldName);
            return value != null && value.asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasJsonText(String json, String fieldName) {
        String safeJson = blankToNull(json);
        if (safeJson == null || fieldName == null || fieldName.isBlank()) {
            return false;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(safeJson);
            JsonNode value = root.get(fieldName);
            return value != null && value.isTextual() && !value.asText().isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void assertMutable(RunProfileRecord record) {
        if (Integer.valueOf(1).equals(record.getReadonly())) {
            throw new IllegalStateException("Readonly system run profiles cannot be edited or deleted");
        }
    }
}
