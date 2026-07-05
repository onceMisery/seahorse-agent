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

package com.miracle.ai.seahorse.agent.ports.inbound.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.model.AiModelConfig;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileProductionGateCheck;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineNodePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRecord;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class GateResults {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GateResults() {
    }

    public static GateResult fromAgentReport(ProductionGateReport report) {
        Objects.requireNonNull(report, "report must not be null");
        List<String> blockingCodes = report.items().stream()
                .filter(item -> item.status() == ProductionGateStatus.FAIL)
                .map(item -> item.code().name())
                .toList();
        return new GateResult(
                "AGENT",
                report.agentId(),
                report.status().name(),
                report.status() != ProductionGateStatus.FAIL,
                blockingCodes,
                report.items().stream()
                        .map(GateResults::fromAgentItem)
                        .toList(),
                report.checkedAt(),
                "ProductionGateReport",
                report.reportId());
    }

    public static GateResult fromRunProfileCheck(RunProfileProductionGateCheck check) {
        Objects.requireNonNull(check, "check must not be null");
        List<RunProfileProductionGateCheck.CheckItem> items = Objects.requireNonNullElse(
                check.getCheckItems(),
                List.<RunProfileProductionGateCheck.CheckItem>of());
        List<String> blockingCodes = Objects.requireNonNullElse(
                check.getBlockingCodes(),
                List.<String>of());
        return new GateResult(
                "RUN_PROFILE",
                String.valueOf(check.getRunProfileId()),
                check.isPassed() ? "PASS" : "FAIL",
                check.isPassed(),
                blockingCodes,
                items.stream()
                        .filter(Objects::nonNull)
                        .map(GateResults::fromRunProfileItem)
                        .toList(),
                Instant.now(),
                "RunProfileProductionGateCheck",
                String.valueOf(check.getRunProfileId()));
    }

    public static GateResult fromRetrievalStrategyComparison(RetrievalEvaluationComparisonRecord comparison) {
        Objects.requireNonNull(comparison, "comparison must not be null");
        RetrievalEvaluationComparisonReport report = comparison.report();
        List<GateResultItem> items = retrievalStrategyItems(report);
        List<String> blockingCodes = items.stream()
                .filter(item -> "FAIL".equals(item.status()))
                .map(GateResultItem::code)
                .toList();
        String winner = trimToNull(report.winnerStrategyName());
        return new GateResult(
                "RAG_STRATEGY",
                comparison.knowledgeBaseId() + ":" + Objects.requireNonNullElse(winner, "unknown"),
                blockingCodes.isEmpty() ? "PASS" : "FAIL",
                blockingCodes.isEmpty(),
                blockingCodes,
                items,
                comparison.createTime(),
                "RetrievalEvaluationComparisonRecord",
                comparison.comparisonId());
    }

    public static GateResult fromSkillRevision(AgentSkillRevision revision) {
        Objects.requireNonNull(revision, "revision must not be null");
        SkillScanDecision decision = Objects.requireNonNullElse(revision.scanDecision(), SkillScanDecision.ALLOW);
        GateResultItem scanItem = new GateResultItem(
                "SKILL_SECURITY_SCAN",
                statusForSkillScan(decision),
                "Latest skill revision security scan decision is " + decision.name());
        List<String> blockingCodes = decision == SkillScanDecision.BLOCK
                ? List.of(scanItem.code())
                : List.of();
        return new GateResult(
                "SKILL",
                revision.tenantId() + ":" + revision.skillName(),
                statusForSkillGate(decision),
                decision != SkillScanDecision.BLOCK,
                blockingCodes,
                List.of(scanItem),
                revision.createdAt(),
                "AgentSkillRevision",
                revision.revisionId());
    }

    public static GateResult fromIngestionPipeline(IngestionPipelineRecord pipeline) {
        Objects.requireNonNull(pipeline, "pipeline must not be null");
        List<GateResultItem> items = ingestionPipelineItems(pipeline);
        List<String> blockingCodes = items.stream()
                .filter(item -> "FAIL".equals(item.status()))
                .map(GateResultItem::code)
                .distinct()
                .toList();
        return new GateResult(
                "INGESTION_PIPELINE",
                pipeline.getId(),
                blockingCodes.isEmpty() ? "PASS" : "FAIL",
                blockingCodes.isEmpty(),
                blockingCodes,
                items,
                Objects.requireNonNullElse(pipeline.getUpdateTime(), pipeline.getCreateTime()),
                "IngestionPipelineRecord",
                pipeline.getId());
    }

    public static GateResult fromAiModelConfig(AiModelConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        List<GateResultItem> items = aiModelConfigItems(config);
        List<String> blockingCodes = items.stream()
                .filter(item -> "FAIL".equals(item.status()))
                .map(GateResultItem::code)
                .toList();
        return new GateResult(
                "MODEL_CONFIG",
                tenantId(config) + ":" + Objects.requireNonNullElse(config.getConfigKey(), "unknown"),
                blockingCodes.isEmpty() ? "PASS" : "FAIL",
                blockingCodes.isEmpty(),
                blockingCodes,
                items,
                instant(config.getUpdatedAt(), config.getCreatedAt()),
                "AiModelConfig",
                config.getId());
    }

    public static GateResult fromToolCatalogEntry(ToolCatalogEntry tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        List<GateResultItem> items = toolCatalogItems(tool);
        List<String> blockingCodes = items.stream()
                .filter(item -> "FAIL".equals(item.status()))
                .map(GateResultItem::code)
                .toList();
        return new GateResult(
                "TOOL",
                tool.toolId(),
                gateStatus(items),
                blockingCodes.isEmpty(),
                blockingCodes,
                items,
                tool.updatedAt(),
                "ToolCatalogEntry",
                tool.toolId());
    }

    private static GateResultItem fromAgentItem(ProductionGateCheckItem item) {
        return new GateResultItem(item.code().name(), item.status().name(), item.message());
    }

    private static GateResultItem fromRunProfileItem(RunProfileProductionGateCheck.CheckItem item) {
        return new GateResultItem(item.getCode(), item.getStatus(), item.getMessage());
    }

    private static List<GateResultItem> retrievalStrategyItems(RetrievalEvaluationComparisonReport report) {
        RetrievalEvaluationComparisonReport safeReport = Objects.requireNonNull(report, "report must not be null");
        String baselineName = trimToNull(safeReport.baselineStrategyName());
        String winnerName = trimToNull(safeReport.winnerStrategyName());
        java.util.ArrayList<GateResultItem> items = new java.util.ArrayList<>();
        if (baselineName == null) {
            items.add(new GateResultItem("RAG_BASELINE_PRESENT", "FAIL",
                    "Comparison baseline strategy is missing"));
        } else {
            items.add(new GateResultItem("RAG_BASELINE_PRESENT", "PASS",
                    "Comparison baseline strategy is " + baselineName));
        }
        if (winnerName == null) {
            items.add(new GateResultItem("RAG_WINNER_PRESENT", "FAIL",
                    "Comparison winner strategy is missing"));
        } else {
            items.add(new GateResultItem("RAG_WINNER_PRESENT", "PASS",
                    "Comparison winner strategy is " + winnerName));
        }
        RetrievalEvaluationReport baseline = reportFor(safeReport, baselineName);
        RetrievalEvaluationReport winner = reportFor(safeReport, winnerName);
        if (baseline == null || winner == null) {
            items.add(new GateResultItem("RAG_STRATEGY_METRICS_PRESENT", "FAIL",
                    "Comparison report is missing baseline or winner metrics"));
            return List.copyOf(items);
        }
        items.add(metricItem("RAG_EVALUABLE_CASES_PRESENT",
                winner.evaluableCaseCount() > 0,
                "Winner evaluable cases: " + winner.evaluableCaseCount()));
        items.add(metricItem("RAG_RECALL_NOT_REGRESSED",
                winner.recallAtK() >= baseline.recallAtK(),
                "Winner recall@k " + winner.recallAtK() + " vs baseline " + baseline.recallAtK()));
        items.add(metricItem("RAG_PRECISION_NOT_REGRESSED",
                winner.precisionAtK() >= baseline.precisionAtK(),
                "Winner precision@k " + winner.precisionAtK() + " vs baseline " + baseline.precisionAtK()));
        items.add(metricItem("RAG_MRR_NOT_REGRESSED",
                winner.mrr() >= baseline.mrr(),
                "Winner MRR " + winner.mrr() + " vs baseline " + baseline.mrr()));
        items.add(metricItem("RAG_NDCG_NOT_REGRESSED",
                winner.ndcgAtK() >= baseline.ndcgAtK(),
                "Winner NDCG@k " + winner.ndcgAtK() + " vs baseline " + baseline.ndcgAtK()));
        items.add(metricItem("RAG_EMPTY_RECALL_NOT_REGRESSED",
                winner.emptyRecallRate() <= baseline.emptyRecallRate(),
                "Winner empty recall rate " + winner.emptyRecallRate()
                        + " vs baseline " + baseline.emptyRecallRate()));
        return List.copyOf(items);
    }

    private static GateResultItem metricItem(String code, boolean passed, String message) {
        return new GateResultItem(code, passed ? "PASS" : "FAIL", message);
    }

    private static String statusForSkillGate(SkillScanDecision decision) {
        return switch (decision) {
            case ALLOW -> "PASS";
            case WARN -> "WARN";
            case BLOCK -> "FAIL";
        };
    }

    private static String statusForSkillScan(SkillScanDecision decision) {
        return decision == SkillScanDecision.BLOCK ? "FAIL" : statusForSkillGate(decision);
    }

    private static List<GateResultItem> ingestionPipelineItems(IngestionPipelineRecord pipeline) {
        List<IngestionPipelineNodePayload> nodes = Objects.requireNonNullElse(
                pipeline.getNodes(),
                List.<IngestionPipelineNodePayload>of()).stream()
                .filter(Objects::nonNull)
                .toList();
        java.util.ArrayList<GateResultItem> items = new java.util.ArrayList<>();
        items.add(metricItem("INGESTION_PIPELINE_NODES_PRESENT",
                !nodes.isEmpty(),
                "Pipeline node count: " + nodes.size()));
        List<String> blankNodeIds = nodes.stream()
                .filter(node -> !hasText(node.nodeId()))
                .map(node -> Objects.requireNonNullElse(node.nodeType(), "unknown"))
                .toList();
        items.add(metricItem("INGESTION_PIPELINE_NODE_IDS_PRESENT",
                blankNodeIds.isEmpty(),
                blankNodeIds.isEmpty()
                        ? "All pipeline nodes define nodeId"
                        : "Pipeline nodes missing nodeId: " + String.join(", ", blankNodeIds)));
        List<String> blankNodeTypes = nodes.stream()
                .filter(node -> !hasText(node.nodeType()))
                .map(IngestionPipelineNodePayload::nodeId)
                .filter(GateResults::hasText)
                .toList();
        items.add(metricItem("INGESTION_PIPELINE_NODE_TYPES_PRESENT",
                blankNodeTypes.isEmpty(),
                blankNodeTypes.isEmpty()
                        ? "All pipeline nodes define nodeType"
                        : "Pipeline nodes missing nodeType: " + String.join(", ", blankNodeTypes)));
        Set<String> duplicateNodeIds = duplicateNodeIds(nodes);
        items.add(metricItem("INGESTION_PIPELINE_NODE_IDS_UNIQUE",
                duplicateNodeIds.isEmpty(),
                duplicateNodeIds.isEmpty()
                        ? "Pipeline nodeIds are unique"
                        : "Duplicate pipeline nodeIds: " + String.join(", ", duplicateNodeIds)));
        Set<String> nodeIds = nodes.stream()
                .map(IngestionPipelineNodePayload::nodeId)
                .filter(GateResults::hasText)
                .collect(Collectors.toSet());
        List<String> missingNextNodes = nodes.stream()
                .map(IngestionPipelineNodePayload::nextNodeId)
                .filter(GateResults::hasText)
                .filter(nextNodeId -> !nodeIds.contains(nextNodeId))
                .distinct()
                .toList();
        items.add(metricItem("INGESTION_PIPELINE_NEXT_NODES_RESOLVE",
                missingNextNodes.isEmpty(),
                missingNextNodes.isEmpty()
                        ? "All nextNodeId references resolve"
                        : "Missing nextNodeId targets: " + String.join(", ", missingNextNodes)));
        items.add(metricItem("INGESTION_PIPELINE_CHAIN_ACYCLIC",
                !hasCycle(nodes),
                "Pipeline nextNodeId chain must not form a cycle"));
        return List.copyOf(items);
    }

    private static Set<String> duplicateNodeIds(List<IngestionPipelineNodePayload> nodes) {
        Set<String> seen = new HashSet<>();
        return nodes.stream()
                .map(IngestionPipelineNodePayload::nodeId)
                .filter(GateResults::hasText)
                .filter(nodeId -> !seen.add(nodeId))
                .collect(Collectors.toSet());
    }

    private static boolean hasCycle(List<IngestionPipelineNodePayload> nodes) {
        java.util.Map<String, IngestionPipelineNodePayload> nodeById = nodes.stream()
                .filter(node -> hasText(node.nodeId()))
                .collect(Collectors.toMap(
                        IngestionPipelineNodePayload::nodeId,
                        Function.identity(),
                        (left, right) -> left));
        for (String nodeId : nodeById.keySet()) {
            Set<String> visiting = new HashSet<>();
            String current = nodeId;
            while (hasText(current)) {
                if (!visiting.add(current)) {
                    return true;
                }
                IngestionPipelineNodePayload node = nodeById.get(current);
                if (node == null) {
                    break;
                }
                current = node.nextNodeId();
            }
        }
        return false;
    }

    private static List<GateResultItem> aiModelConfigItems(AiModelConfig config) {
        java.util.ArrayList<GateResultItem> items = new java.util.ArrayList<>();
        String configKey = trimToNull(config.getConfigKey());
        String configValue = trimToNull(config.getConfigValue());
        AiModelConfig.ConfigType configType = config.getConfigType();
        items.add(metricItem("MODEL_CONFIG_KEY_PRESENT",
                configKey != null,
                configKey == null ? "Model config key is missing" : "Model config key is " + configKey));
        items.add(metricItem("MODEL_CONFIG_VALUE_PRESENT",
                configValue != null,
                configValue == null ? "Model config value is missing" : "Model config value is present"));
        items.add(metricItem("MODEL_CONFIG_TYPE_PRESENT",
                configType != null,
                configType == null ? "Model config type is missing" : "Model config type is " + configType.name()));
        boolean jsonValid = configType != AiModelConfig.ConfigType.JSON || validJson(configValue);
        items.add(metricItem("MODEL_CONFIG_JSON_VALUE_VALID",
                jsonValid,
                configType == AiModelConfig.ConfigType.JSON
                        ? (jsonValid ? "JSON model config value is valid" : "JSON model config value is invalid")
                        : "Model config type does not require JSON parsing"));
        boolean sensitiveEncrypted = !sensitiveConfigKey(configKey) || config.isEncrypted();
        items.add(metricItem("MODEL_CONFIG_SENSITIVE_VALUE_ENCRYPTED",
                sensitiveEncrypted,
                sensitiveEncrypted
                        ? "Sensitive model config encryption requirement is satisfied"
                        : "Sensitive model config keys must be encrypted"));
        return List.copyOf(items);
    }

    private static List<GateResultItem> toolCatalogItems(ToolCatalogEntry tool) {
        java.util.ArrayList<GateResultItem> items = new java.util.ArrayList<>();
        ToolRiskLevel riskLevel = tool.riskLevel();
        ToolActionType actionType = tool.actionType();
        items.add(metricItem("TOOL_ENABLED",
                tool.enabled(),
                tool.enabled() ? "Tool is enabled" : "Disabled tools cannot be released for production use"));
        items.add(metricItem("TOOL_RISK_LEVEL_DECLARED",
                riskLevel != null,
                riskLevel == null ? "Tool risk level is missing" : "Tool risk level is " + riskLevel.name()));
        items.add(metricItem("TOOL_ACTION_TYPE_DECLARED",
                actionType != null,
                actionType == null ? "Tool action type is missing" : "Tool action type is " + actionType.name()));
        boolean highRisk = riskLevel == ToolRiskLevel.HIGH || riskLevel == ToolRiskLevel.CRITICAL;
        items.add(metricItem("TOOL_HIGH_RISK_APPROVAL_REQUIRED",
                !highRisk || tool.requiresApproval(),
                highRisk
                        ? "High and critical risk tools must require approval"
                        : "Tool risk level does not require mandatory approval"));
        items.add(new GateResultItem("TOOL_OWNER_DECLARED",
                hasText(tool.ownerTeam()) ? "PASS" : "WARN",
                hasText(tool.ownerTeam()) ? "Tool owner team is " + tool.ownerTeam() : "Tool owner team is missing"));
        items.add(metricItem("TOOL_INPUT_SCHEMA_VALID",
                validJson(tool.schemaJson()),
                "Tool input schema must be valid JSON"));
        String outputSchema = trimToNull(tool.outputSchemaJson());
        items.add(metricItem("TOOL_OUTPUT_SCHEMA_VALID",
                outputSchema == null || validJson(outputSchema),
                outputSchema == null ? "Tool output schema is optional" : "Tool output schema must be valid JSON"));
        return List.copyOf(items);
    }

    private static String gateStatus(List<GateResultItem> items) {
        boolean failed = items.stream().anyMatch(item -> "FAIL".equals(item.status()));
        if (failed) {
            return "FAIL";
        }
        boolean warned = items.stream().anyMatch(item -> "WARN".equals(item.status()));
        return warned ? "WARN" : "PASS";
    }

    private static boolean validJson(String value) {
        if (value == null) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean sensitiveConfigKey(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.contains("key")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("password")
                || lower.contains("credential");
    }

    private static String tenantId(AiModelConfig config) {
        return Objects.requireNonNullElse(trimToNull(config.getTenantId()), "default");
    }

    private static Instant instant(LocalDateTime updatedAt, LocalDateTime createdAt) {
        LocalDateTime value = updatedAt != null ? updatedAt : createdAt;
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static RetrievalEvaluationReport reportFor(RetrievalEvaluationComparisonReport report, String strategyName) {
        if (strategyName == null) {
            return null;
        }
        return report.reports().stream()
                .filter(strategyReport -> strategyName.equals(strategyReport.strategyName()))
                .findFirst()
                .orElse(null);
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
