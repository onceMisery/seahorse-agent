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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyPromotionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplateInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplatePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationComparisonRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 内核默认检索策略模板服务。
 *
 * <p>服务负责合并内置模板和知识库级覆盖；具体持久化由仓储适配器提供。</p>
 */
public class KernelRetrievalStrategyTemplateService implements RetrievalStrategyTemplateInboundPort {

    private final RetrievalStrategyTemplateRepositoryPort repositoryPort;
    private final RetrievalEvaluationComparisonRepositoryPort comparisonRepositoryPort;
    private final KernelAuditLedgerService auditLedger;
    private final Clock clock;

    public KernelRetrievalStrategyTemplateService() {
        this(RetrievalStrategyTemplateRepositoryPort.empty());
    }

    public KernelRetrievalStrategyTemplateService(RetrievalStrategyTemplateRepositoryPort repositoryPort) {
        this(repositoryPort, RetrievalEvaluationComparisonRepositoryPort.empty(), null, Clock.systemUTC());
    }

    public KernelRetrievalStrategyTemplateService(RetrievalStrategyTemplateRepositoryPort repositoryPort,
                                                  RetrievalEvaluationComparisonRepositoryPort comparisonRepositoryPort,
                                                  KernelAuditLedgerService auditLedger,
                                                  Clock clock) {
        this.repositoryPort = Objects.requireNonNullElse(repositoryPort,
                RetrievalStrategyTemplateRepositoryPort.empty());
        this.comparisonRepositoryPort = Objects.requireNonNullElse(comparisonRepositoryPort,
                RetrievalEvaluationComparisonRepositoryPort.empty());
        this.auditLedger = auditLedger;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public List<RetrievalStrategyTemplate> listTemplates(String kbId) {
        return mergeTemplates(defaultTemplates(), repositoryPort.listTemplates(kbId));
    }

    @Override
    public RetrievalStrategyTemplate upsertTemplate(String kbId, RetrievalStrategyTemplatePayload payload) {
        String safeKbId = requireText(kbId, "kbId must not be blank");
        RetrievalStrategyTemplatePayload safePayload = validate(payload);
        return repositoryPort.upsertTemplate(safeKbId, safePayload);
    }

    @Override
    public RetrievalStrategyTemplate promoteTemplateFromComparison(String kbId,
                                                                   RetrievalStrategyPromotionCommand command) {
        String safeKbId = requireText(kbId, "kbId must not be blank");
        RetrievalStrategyPromotionCommand safeCommand = Objects.requireNonNull(command,
                "command must not be null");
        requireText(safeCommand.datasetId(), "datasetId must not be blank");
        requireText(safeCommand.comparisonId(), "comparisonId must not be blank");
        RetrievalStrategyTemplatePayload safePayload = validate(safeCommand.template());
        RetrievalEvaluationComparisonRecord comparison = comparisonRepositoryPort
                .findComparison(safeKbId, safeCommand.datasetId(), safeCommand.comparisonId())
                .orElseThrow(() -> new IllegalArgumentException("retrieval evaluation comparison not found: "
                        + safeCommand.comparisonId()));
        validatePromotionGates(safePayload.templateKey(), comparison.report());
        RetrievalStrategyTemplate promoted = repositoryPort.promoteRecommendedTemplate(safeKbId, safePayload);
        appendPromotionAudit(safeKbId, safeCommand, comparison);
        return promoted;
    }

    @Override
    public boolean deleteTemplate(String kbId, String templateKey) {
        String safeKbId = requireText(kbId, "kbId must not be blank");
        String safeTemplateKey = requireText(templateKey, "templateKey must not be blank");
        return repositoryPort.deleteTemplate(safeKbId, safeTemplateKey);
    }

    private void validatePromotionGates(String templateKey, RetrievalEvaluationComparisonReport report) {
        RetrievalEvaluationComparisonReport safeReport = Objects.requireNonNull(report, "report must not be null");
        String winner = requireText(safeReport.winnerStrategyName(),
                "comparison winner must not be blank before promotion");
        if (!winner.equals(templateKey)) {
            throw new IllegalStateException("comparison winner does not match promoted template: " + winner);
        }
        String baselineName = requireText(safeReport.baselineStrategyName(),
                "comparison baseline must not be blank before promotion");
        RetrievalEvaluationReport baseline = reportFor(safeReport, baselineName);
        RetrievalEvaluationReport candidate = reportFor(safeReport, winner);
        if (candidate.evaluableCaseCount() <= 0
                || candidate.recallAtK() < baseline.recallAtK()
                || candidate.precisionAtK() < baseline.precisionAtK()
                || candidate.mrr() < baseline.mrr()
                || candidate.ndcgAtK() < baseline.ndcgAtK()
                || candidate.emptyRecallRate() > baseline.emptyRecallRate()) {
            throw new IllegalStateException("retrieval strategy comparison did not pass promotion gates");
        }
    }

    private RetrievalEvaluationReport reportFor(RetrievalEvaluationComparisonReport report, String strategyName) {
        return report.reports().stream()
                .filter(strategyReport -> strategyName.equals(strategyReport.strategyName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "comparison report is missing strategy metrics: " + strategyName));
    }

    private void appendPromotionAudit(String kbId,
                                      RetrievalStrategyPromotionCommand command,
                                      RetrievalEvaluationComparisonRecord comparison) {
        if (auditLedger == null) {
            return;
        }
        auditLedger.append(new AuditEvent(
                "audit_" + SnowflakeIds.nextIdString(),
                command.tenantId(),
                AuditEventType.RETRIEVAL_STRATEGY_PROMOTED,
                AuditActorType.USER,
                command.operatorId(),
                null,
                null,
                "RETRIEVAL_STRATEGY_TEMPLATE",
                kbId + ":" + command.template().templateKey(),
                promotionPayload(kbId, command, comparison),
                clock.instant()));
    }

    private String promotionPayload(String kbId,
                                    RetrievalStrategyPromotionCommand command,
                                    RetrievalEvaluationComparisonRecord comparison) {
        RetrievalEvaluationComparisonReport report = comparison.report();
        RetrievalEvaluationReport candidate = reportFor(report, command.template().templateKey());
        return "{\"knowledgeBaseId\":\"" + escape(kbId)
                + "\",\"datasetId\":\"" + escape(command.datasetId())
                + "\",\"comparisonId\":\"" + escape(command.comparisonId())
                + "\",\"templateKey\":\"" + escape(command.template().templateKey())
                + "\",\"baselineStrategyName\":\"" + escape(report.baselineStrategyName())
                + "\",\"winnerStrategyName\":\"" + escape(report.winnerStrategyName())
                + "\",\"recallAtK\":" + candidate.recallAtK()
                + ",\"precisionAtK\":" + candidate.precisionAtK()
                + ",\"emptyRecallRate\":" + candidate.emptyRecallRate()
                + ",\"comment\":\"" + escape(command.comment()) + "\"}";
    }

    private List<RetrievalStrategyTemplate> defaultTemplates() {
        return List.of(vectorOnly(), hybridRrf(), hybridRerank());
    }

    private List<RetrievalStrategyTemplate> mergeTemplates(List<RetrievalStrategyTemplate> defaults,
                                                           List<RetrievalStrategyTemplate> overrides) {
        Map<String, RetrievalStrategyTemplate> merged = new LinkedHashMap<>();
        for (RetrievalStrategyTemplate template : defaults) {
            merged.put(template.templateKey(), template);
        }
        List<RetrievalStrategyTemplate> safeOverrides = Objects.requireNonNullElse(
                overrides, List.<RetrievalStrategyTemplate>of());
        for (RetrievalStrategyTemplate template : safeOverrides) {
            if (template == null || template.templateKey().isBlank()) {
                continue;
            }
            // 知识库级覆盖按 templateKey 替换内置模板，新模板追加在末尾。
            merged.put(template.templateKey(), template);
        }
        return List.copyOf(merged.values());
    }

    private RetrievalStrategyTemplatePayload validate(RetrievalStrategyTemplatePayload payload) {
        RetrievalStrategyTemplatePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        requireText(safePayload.templateKey(), "templateKey must not be blank");
        requireText(safePayload.displayName(), "displayName must not be blank");
        return safePayload;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String escape(String value) {
        return Objects.requireNonNullElse(value, "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private RetrievalStrategyTemplate vectorOnly() {
        return new RetrievalStrategyTemplate(
                "vector_only",
                "向量召回",
                "只使用向量和意图召回，适合轻量知识库或关键词索引尚未启用的场景。",
                RetrievalOptions.builder()
                        .finalTopK(5)
                        .enableVector(true)
                        .enableIntentDirected(true)
                        .enableKeyword(false)
                        .enableRrf(false)
                        .enableRerank(false)
                        .build());
    }

    private RetrievalStrategyTemplate hybridRrf() {
        return new RetrievalStrategyTemplate(
                "hybrid_rrf",
                "混合召回 RRF",
                "同时启用向量、意图和关键词通道，并通过 RRF 做通道融合。",
                RetrievalOptions.builder()
                        .finalTopK(5)
                        .enableVector(true)
                        .enableIntentDirected(true)
                        .enableKeyword(true)
                        .enableRrf(true)
                        .enableRerank(false)
                        .channelSettings(Map.of(
                                "rrfK", 60,
                                "channelWeights", Map.of(
                                        "VectorGlobalSearch", 1.0D,
                                        "IntentDirectedSearch", 1.2D,
                                        "KeywordSearch", 1.0D)))
                        .build());
    }

    private RetrievalStrategyTemplate hybridRerank() {
        return new RetrievalStrategyTemplate(
                "hybrid_rerank",
                "混合召回精排",
                "在混合召回和 RRF 后启用精排；管理端应用时需要补充具体 rerankModel。",
                RetrievalOptions.builder()
                        .finalTopK(5)
                        .fusionTopK(15)
                        .rerankTopK(5)
                        .enableVector(true)
                        .enableIntentDirected(true)
                        .enableKeyword(true)
                        .enableRrf(true)
                        .enableRerank(true)
                        .channelSettings(Map.of("rrfK", 60))
                        .build());
    }
}
