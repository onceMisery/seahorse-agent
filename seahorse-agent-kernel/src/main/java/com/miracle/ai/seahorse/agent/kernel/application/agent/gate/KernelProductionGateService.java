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

package com.miracle.ai.seahorse.agent.kernel.application.agent.gate;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ProductionGateInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentPublishCheckRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ProductionGateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthReportProviderPort;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelProductionGateService implements ProductionGateInboundPort {

    private static final String REPORT_ID_PREFIX = "pg_";
    private static final List<AgentEvalType> HIGH_RISK_REQUIRED_EVAL_TYPES = List.of(
            AgentEvalType.SAFETY,
            AgentEvalType.TRAJECTORY);
    private static final List<AgentEvalType> LOW_RISK_REQUIRED_EVAL_TYPES = List.of(AgentEvalType.SAFETY);

    private final ProductionGateRepositoryPort repository;
    private final AgentDefinitionRepositoryPort agentRepository;
    private final AgentEvalSummaryRepositoryPort evalSummaryRepository;
    private final QuotaPolicyRepositoryPort quotaPolicyRepository;
    private final SreHealthReportProviderPort sreHealthReportProvider;
    private final AgentPublishCheckRepositoryPort publishCheckRepository;
    private final Clock clock;

    public KernelProductionGateService(ProductionGateRepositoryPort repository, Clock clock) {
        this(repository, null, null, clock);
    }

    public KernelProductionGateService(ProductionGateRepositoryPort repository,
                                       AgentDefinitionRepositoryPort agentRepository,
                                       Clock clock) {
        this(repository, agentRepository, null, clock);
    }

    public KernelProductionGateService(ProductionGateRepositoryPort repository,
                                       AgentDefinitionRepositoryPort agentRepository,
                                       AgentEvalSummaryRepositoryPort evalSummaryRepository,
                                       Clock clock) {
        this(repository, agentRepository, evalSummaryRepository, null, null, clock);
    }

    public KernelProductionGateService(ProductionGateRepositoryPort repository,
                                       AgentDefinitionRepositoryPort agentRepository,
                                       AgentEvalSummaryRepositoryPort evalSummaryRepository,
                                       QuotaPolicyRepositoryPort quotaPolicyRepository,
                                       SreHealthReportProviderPort sreHealthReportProvider,
                                       Clock clock) {
        this(repository, agentRepository, evalSummaryRepository, quotaPolicyRepository, sreHealthReportProvider,
                null, clock);
    }

    public KernelProductionGateService(ProductionGateRepositoryPort repository,
                                       AgentDefinitionRepositoryPort agentRepository,
                                       AgentEvalSummaryRepositoryPort evalSummaryRepository,
                                       QuotaPolicyRepositoryPort quotaPolicyRepository,
                                       SreHealthReportProviderPort sreHealthReportProvider,
                                       AgentPublishCheckRepositoryPort publishCheckRepository,
                                       Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.agentRepository = agentRepository;
        this.evalSummaryRepository = evalSummaryRepository;
        this.quotaPolicyRepository = quotaPolicyRepository;
        this.sreHealthReportProvider = sreHealthReportProvider;
        this.publishCheckRepository = publishCheckRepository;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    public ProductionGateReport generate(AgentDefinition agent) {
        AgentDefinition safeAgent = Objects.requireNonNull(agent, "agent must not be null");
        ProductionGateReport report = new ProductionGateReport(
                reportId(),
                safeAgent.agentId(),
                safeAgent.latestVersionId(),
                null,
                checks(safeAgent),
                clock.instant());
        return repository.save(report);
    }

    @Override
    public ProductionGateReport generate(String agentId) {
        if (agentRepository == null) {
            throw new IllegalStateException("Agent definition repository is not configured");
        }
        AgentDefinition agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent definition not found"));
        return generate(agent);
    }

    @Override
    public Optional<ProductionGateReport> latest(String agentId) {
        return repository.latest(agentId);
    }

    private List<ProductionGateCheckItem> checks(AgentDefinition agent) {
        List<ProductionGateCheckItem> items = new ArrayList<>();
        Optional<AgentPublishCheckReport> publishCheck = publishCheck(agent.agentId());
        items.add(ownerPresent(agent));
        items.add(publishedVersionPresent(agent));
        items.add(publishCheckItem(agent, publishCheck,
                AgentPublishCheckCode.TOOLS_ENABLED,
                ProductionGateCheckCode.TOOL_RISK_REVIEWED,
                ProductionGateCheckItem.pass(
                        ProductionGateCheckCode.TOOL_RISK_REVIEWED,
                        "Tool risk review is owned by publish validation in this foundation slice.")));
        items.add(publishCheckItem(agent, publishCheck,
                AgentPublishCheckCode.HIGH_RISK_APPROVAL_PRESENT,
                ProductionGateCheckCode.HIGH_RISK_APPROVAL_PRESENT,
                ProductionGateCheckItem.pass(
                        ProductionGateCheckCode.HIGH_RISK_APPROVAL_PRESENT,
                        "High risk approval policy is owned by publish validation in this foundation slice.")));
        items.add(ProductionGateCheckItem.pass(
                ProductionGateCheckCode.AUDIT_LEDGER_ENABLED,
                "Audit ledger repository is configured."));
        items.add(publishCheckItem(agent, publishCheck,
                AgentPublishCheckCode.RESOURCE_ACL_PRESENT,
                ProductionGateCheckCode.RESOURCE_ACL_PRESENT,
                ProductionGateCheckItem.warn(
                        ProductionGateCheckCode.RESOURCE_ACL_PRESENT,
                        "Resource ACL gate bridge is not connected in this foundation slice.")));
        items.add(evalPassing(agent));
        items.add(quotaConfigured(agent));
        items.add(sreHealthGreen());
        return items;
    }

    private Optional<AgentPublishCheckReport> publishCheck(String agentId) {
        if (publishCheckRepository == null) {
            return Optional.empty();
        }
        return publishCheckRepository.latest(agentId);
    }

    private ProductionGateCheckItem publishCheckItem(AgentDefinition agent,
                                                    Optional<AgentPublishCheckReport> publishCheck,
                                                    AgentPublishCheckCode sourceCode,
                                                    ProductionGateCheckCode targetCode,
                                                    ProductionGateCheckItem disconnectedFallback) {
        if (publishCheckRepository == null) {
            return disconnectedFallback;
        }
        if (publishCheck.isEmpty()) {
            return ProductionGateCheckItem.warn(targetCode, "Latest publish check is missing: " + sourceCode.name());
        }
        AgentPublishCheckReport report = publishCheck.orElseThrow();
        if (hasText(agent.latestVersionId()) && hasText(report.versionId())
                && !agent.latestVersionId().equals(report.versionId())) {
            return ProductionGateCheckItem.fail(targetCode,
                    "Latest publish check version does not match published version: "
                            + report.checkId() + " " + report.versionId());
        }
        return report.item(sourceCode)
                .map(item -> fromPublishCheck(report, item, targetCode))
                .orElseGet(() -> ProductionGateCheckItem.warn(
                        targetCode,
                        "Publish check item is missing: " + report.checkId() + " " + sourceCode.name()));
    }

    private ProductionGateCheckItem fromPublishCheck(AgentPublishCheckReport report,
                                                    AgentPublishCheckItem item,
                                                    ProductionGateCheckCode targetCode) {
        return new ProductionGateCheckItem(
                targetCode,
                toProductionStatus(item.status()),
                "Publish check " + report.checkId() + ": " + item.message());
    }

    private ProductionGateStatus toProductionStatus(AgentPublishCheckStatus status) {
        return switch (Objects.requireNonNullElse(status, AgentPublishCheckStatus.FAIL)) {
            case PASS -> ProductionGateStatus.PASS;
            case WARN -> ProductionGateStatus.WARN;
            case FAIL -> ProductionGateStatus.FAIL;
        };
    }

    private ProductionGateCheckItem quotaConfigured(AgentDefinition agent) {
        if (quotaPolicyRepository == null) {
            return ProductionGateCheckItem.warn(
                    ProductionGateCheckCode.QUOTA_CONFIGURED,
                    "Quota policy is not connected in this foundation slice.");
        }
        Optional<QuotaPolicy> policy = quotaPolicyRepository.findActive(
                agent.tenantId(),
                QuotaScope.AGENT,
                agent.agentId());
        if (policy.isEmpty()) {
            policy = quotaPolicyRepository.findActive(agent.tenantId(), QuotaScope.TENANT, agent.tenantId());
        }
        if (policy.isPresent()) {
            return ProductionGateCheckItem.pass(
                    ProductionGateCheckCode.QUOTA_CONFIGURED,
                    "Quota policy is configured: " + policy.orElseThrow().policyId());
        }
        if (highRisk(agent.riskLevel())) {
            return ProductionGateCheckItem.fail(
                    ProductionGateCheckCode.QUOTA_CONFIGURED,
                    "Quota policy is required for high risk agent.");
        }
        return ProductionGateCheckItem.warn(
                ProductionGateCheckCode.QUOTA_CONFIGURED,
                "Quota policy is not configured.");
    }

    private ProductionGateCheckItem sreHealthGreen() {
        if (sreHealthReportProvider == null) {
            return ProductionGateCheckItem.warn(
                    ProductionGateCheckCode.SRE_HEALTH_GREEN,
                    "SRE health bridge is not connected in this foundation slice.");
        }
        SreHealthReport report = sreHealthReportProvider.current();
        if (report.status() == SreHealthStatus.GREEN) {
            return ProductionGateCheckItem.pass(
                    ProductionGateCheckCode.SRE_HEALTH_GREEN,
                    "SRE health is green: " + report.reportId());
        }
        if (report.status() == SreHealthStatus.RED) {
            return ProductionGateCheckItem.fail(
                    ProductionGateCheckCode.SRE_HEALTH_GREEN,
                    "SRE health is red: " + report.reportId());
        }
        return ProductionGateCheckItem.warn(
                ProductionGateCheckCode.SRE_HEALTH_GREEN,
                "SRE health is warning: " + report.reportId());
    }

    private ProductionGateCheckItem evalPassing(AgentDefinition agent) {
        if (evalSummaryRepository == null) {
            return ProductionGateCheckItem.warn(
                    ProductionGateCheckCode.EVAL_PASSING,
                    "Evaluation platform is not connected in this foundation slice.");
        }
        if (!hasText(agent.latestVersionId())) {
            return ProductionGateCheckItem.fail(
                    ProductionGateCheckCode.EVAL_PASSING,
                    "Published version is required before evaluating summaries.");
        }
        EvalCheckAccumulator accumulator = new EvalCheckAccumulator();
        for (AgentEvalType evalType : requiredEvalTypes(agent.riskLevel())) {
            Optional<AgentEvalSummary> latest = evalSummaryRepository.findLatest(
                    agent.tenantId(),
                    agent.agentId(),
                    agent.latestVersionId(),
                    evalType);
            if (latest.isEmpty()) {
                accumulator.missing(evalType, highRisk(agent.riskLevel()));
                continue;
            }
            accumulator.record(latest.orElseThrow(), highRisk(agent.riskLevel()), clock);
        }
        return accumulator.toItem();
    }

    private List<AgentEvalType> requiredEvalTypes(AgentRiskLevel riskLevel) {
        return highRisk(riskLevel) ? HIGH_RISK_REQUIRED_EVAL_TYPES : LOW_RISK_REQUIRED_EVAL_TYPES;
    }

    private boolean highRisk(AgentRiskLevel riskLevel) {
        return riskLevel == AgentRiskLevel.HIGH || riskLevel == AgentRiskLevel.CRITICAL;
    }

    private ProductionGateCheckItem ownerPresent(AgentDefinition agent) {
        if (hasText(agent.ownerUserId())) {
            return ProductionGateCheckItem.pass(ProductionGateCheckCode.OWNER_PRESENT, "Owner is present.");
        }
        return ProductionGateCheckItem.fail(ProductionGateCheckCode.OWNER_PRESENT, "Owner is required.");
    }

    private ProductionGateCheckItem publishedVersionPresent(AgentDefinition agent) {
        if (hasText(agent.latestVersionId())) {
            return ProductionGateCheckItem.pass(
                    ProductionGateCheckCode.PUBLISHED_VERSION_PRESENT,
                    "Published version is present.");
        }
        return ProductionGateCheckItem.fail(
                ProductionGateCheckCode.PUBLISHED_VERSION_PRESENT,
                "Published version is required.");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String reportId() {
        return REPORT_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private static final class EvalCheckAccumulator {

        private ProductionGateCheckItem worst = ProductionGateCheckItem.pass(
                ProductionGateCheckCode.EVAL_PASSING,
                "Required eval summaries are passing.");
        private final List<String> evidence = new ArrayList<>();

        private void missing(AgentEvalType evalType, boolean highRisk) {
            ProductionGateCheckItem item = highRisk
                    ? ProductionGateCheckItem.fail(
                    ProductionGateCheckCode.EVAL_PASSING,
                    "Required eval summary is missing: " + evalType.name())
                    : ProductionGateCheckItem.warn(
                    ProductionGateCheckCode.EVAL_PASSING,
                    "Eval summary is missing: " + evalType.name());
            recordWorst(item);
        }

        private void record(AgentEvalSummary summary, boolean highRisk, Clock clock) {
            AgentEvalStatus effectiveStatus = summary.effectiveStatus(clock.instant());
            evidence.add("%s=%s:%s".formatted(summary.evalType().name(), effectiveStatus.name(), summary.summaryId()));
            ProductionGateCheckItem item = switch (effectiveStatus) {
                case FAIL -> ProductionGateCheckItem.fail(
                        ProductionGateCheckCode.EVAL_PASSING,
                        "Eval summary failed: " + summary.evalType().name() + " " + summary.summaryId());
                case STALE -> highRisk
                        ? ProductionGateCheckItem.fail(
                        ProductionGateCheckCode.EVAL_PASSING,
                        "Eval summary is stale for high risk agent: "
                                + summary.evalType().name() + " " + summary.summaryId())
                        : ProductionGateCheckItem.warn(
                        ProductionGateCheckCode.EVAL_PASSING,
                        "Eval summary is stale: " + summary.evalType().name() + " " + summary.summaryId());
                case WARN -> ProductionGateCheckItem.warn(
                        ProductionGateCheckCode.EVAL_PASSING,
                        "Eval summary warned: " + summary.evalType().name() + " " + summary.summaryId());
                case PASS -> ProductionGateCheckItem.pass(
                        ProductionGateCheckCode.EVAL_PASSING,
                        "Eval summary passed: " + summary.evalType().name() + " " + summary.summaryId());
            };
            recordWorst(item);
        }

        private ProductionGateCheckItem toItem() {
            if (evidence.isEmpty() || worst.status() != com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus.PASS) {
                return worst;
            }
            return ProductionGateCheckItem.pass(
                    ProductionGateCheckCode.EVAL_PASSING,
                    "Required eval summaries are passing: " + String.join(", ", evidence));
        }

        private void recordWorst(ProductionGateCheckItem item) {
            if (item.status().isMoreSevereThan(worst.status())) {
                worst = item;
            }
        }
    }
}
