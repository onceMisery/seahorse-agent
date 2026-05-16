package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverageDelta;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityComparisonDelta;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityComparisonReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据治理质量报表服务。
 */
public class KernelMetadataQualityService implements MetadataQualityInboundPort {

    private static final String EVENT_QUALITY_REPORT = "metadata.quality.report.generated";
    private static final String EVENT_QUALITY_COMPARE = "metadata.quality.compare.generated";

    private final MetadataQualityReportRepositoryPort reportRepositoryPort;
    private final ObservationPort observationPort;

    public KernelMetadataQualityService(MetadataQualityReportRepositoryPort reportRepositoryPort) {
        this(reportRepositoryPort, null);
    }

    public KernelMetadataQualityService(MetadataQualityReportRepositoryPort reportRepositoryPort,
                                        ObservationPort observationPort) {
        this.reportRepositoryPort = Objects.requireNonNullElse(reportRepositoryPort,
                MetadataQualityReportRepositoryPort.empty());
        this.observationPort = observationPort;
    }

    @Override
    public MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN) {
        return report(tenantId, knowledgeBaseId, quarantineTopN, null, "", "");
    }

    @Override
    public MetadataQualityReport report(String tenantId,
                                        String knowledgeBaseId,
                                        int quarantineTopN,
                                        Integer schemaVersion,
                                        String extractorVersion) {
        return report(tenantId, knowledgeBaseId, quarantineTopN, schemaVersion, extractorVersion, "");
    }

    @Override
    public MetadataQualityReport report(String tenantId,
                                        String knowledgeBaseId,
                                        int quarantineTopN,
                                        Integer schemaVersion,
                                        String extractorVersion,
                                        String llmPromptVersion) {
        int safeTopN = quarantineTopN <= 0 ? 5 : Math.min(quarantineTopN, 50);
        Integer safeSchemaVersion = schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
        String safeExtractorVersion = Objects.requireNonNullElse(extractorVersion, "");
        String safeLlmPromptVersion = Objects.requireNonNullElse(llmPromptVersion, "");
        MetadataQualityReport report = reportRepositoryPort.report(
                tenantId, knowledgeBaseId, safeTopN, safeSchemaVersion, safeExtractorVersion, safeLlmPromptVersion);
        recordQualityReport(report, safeSchemaVersion, safeExtractorVersion, safeLlmPromptVersion);
        return report;
    }

    @Override
    public MetadataQualityComparisonReport compare(String tenantId,
                                                   String knowledgeBaseId,
                                                   int quarantineTopN,
                                                   Integer baselineSchemaVersion,
                                                   String baselineExtractorVersion,
                                                   String baselineLlmPromptVersion,
                                                   Integer candidateSchemaVersion,
                                                   String candidateExtractorVersion,
                                                   String candidateLlmPromptVersion) {
        MetadataQualityReport baseline = report(
                tenantId, knowledgeBaseId, quarantineTopN,
                baselineSchemaVersion, baselineExtractorVersion, baselineLlmPromptVersion);
        MetadataQualityReport candidate = report(
                tenantId, knowledgeBaseId, quarantineTopN,
                candidateSchemaVersion, candidateExtractorVersion, candidateLlmPromptVersion);
        MetadataQualityComparisonReport comparison = new MetadataQualityComparisonReport(
                Objects.requireNonNullElse(tenantId, ""),
                Objects.requireNonNullElse(knowledgeBaseId, ""),
                baseline,
                candidate,
                new MetadataQualityComparisonDelta(
                        candidate.totalDocuments() - baseline.totalDocuments(),
                        candidate.extractedDocuments() - baseline.extractedDocuments(),
                        delta(candidate.averageFieldCoverage(), baseline.averageFieldCoverage()),
                        delta(candidate.lowConfidenceRatio(), baseline.lowConfidenceRatio()),
                        delta(candidate.reviewPassRate(), baseline.reviewPassRate()),
                        delta(candidate.reviewCorrectionRate(), baseline.reviewCorrectionRate()),
                        candidate.pendingReviewCount() - baseline.pendingReviewCount(),
                        candidate.unresolvedQuarantineCount() - baseline.unresolvedQuarantineCount(),
                        candidate.indexSyncFailureCount() - baseline.indexSyncFailureCount()),
                fieldDeltas(baseline, candidate));
        recordQualityComparison(comparison);
        return comparison;
    }

    private void recordQualityReport(MetadataQualityReport report,
                                     Integer schemaVersion,
                                     String extractorVersion,
                                     String llmPromptVersion) {
        if (observationPort == null || report == null) {
            return;
        }
        try {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("tenantId", report.tenantId());
            attributes.put("knowledgeBaseId", report.knowledgeBaseId());
            attributes.put("totalDocuments", Integer.toString(report.totalDocuments()));
            attributes.put("extractedDocuments", Integer.toString(report.extractedDocuments()));
            attributes.put("averageFieldCoverage", Double.toString(report.averageFieldCoverage()));
            attributes.put("lowConfidenceRatio", Double.toString(report.lowConfidenceRatio()));
            attributes.put("reviewPassRate", Double.toString(report.reviewPassRate()));
            attributes.put("reviewCorrectionRate", Double.toString(report.reviewCorrectionRate()));
            attributes.put("pendingReviewCount", Integer.toString(report.pendingReviewCount()));
            attributes.put("unresolvedQuarantineCount", Integer.toString(report.unresolvedQuarantineCount()));
            attributes.put("indexSyncFailureCount", Integer.toString(report.indexSyncFailureCount()));
            // 将版本筛选条件写入观测事件，便于区分不同口径的质量报表。
            if (schemaVersion != null) {
                attributes.put("schemaVersion", Integer.toString(schemaVersion));
            }
            if (extractorVersion != null && !extractorVersion.isBlank()) {
                attributes.put("extractorVersion", extractorVersion);
            }
            if (llmPromptVersion != null && !llmPromptVersion.isBlank()) {
                attributes.put("llmPromptVersion", llmPromptVersion);
            }
            observationPort.recordEvent(new ObservationEvent(EVENT_QUALITY_REPORT, null, attributes));
        } catch (RuntimeException ignored) {
            // 质量报表查询不能被观测端口异常打断。
        }
    }

    private List<MetadataFieldCoverageDelta> fieldDeltas(MetadataQualityReport baseline,
                                                         MetadataQualityReport candidate) {
        Map<String, MetadataFieldCoverage> baselineByField = indexByField(baseline.fieldCoverages());
        Map<String, MetadataFieldCoverage> candidateByField = indexByField(candidate.fieldCoverages());
        LinkedHashSet<String> fieldKeys = new LinkedHashSet<>();
        fieldKeys.addAll(baselineByField.keySet());
        fieldKeys.addAll(candidateByField.keySet());
        return fieldKeys.stream()
                .map(fieldKey -> toFieldDelta(
                        baselineByField.get(fieldKey),
                        candidateByField.get(fieldKey)))
                .toList();
    }

    private Map<String, MetadataFieldCoverage> indexByField(List<MetadataFieldCoverage> coverages) {
        Map<String, MetadataFieldCoverage> indexed = new LinkedHashMap<>();
        for (MetadataFieldCoverage coverage : coverages) {
            indexed.put(coverage.fieldKey(), coverage);
        }
        return indexed;
    }

    private MetadataFieldCoverageDelta toFieldDelta(MetadataFieldCoverage baseline,
                                                    MetadataFieldCoverage candidate) {
        MetadataFieldCoverage safeBaseline = baseline == null
                ? new MetadataFieldCoverage("", "", false, 0, 0, 0D)
                : baseline;
        MetadataFieldCoverage safeCandidate = candidate == null
                ? new MetadataFieldCoverage(
                safeBaseline.fieldKey(),
                safeBaseline.displayName(),
                safeBaseline.required(),
                0, 0, 0D, 0, 0D, 0, 0, 0D)
                : candidate;
        String fieldKey = safeCandidate.fieldKey().isBlank() ? safeBaseline.fieldKey() : safeCandidate.fieldKey();
        String displayName = safeCandidate.displayName().isBlank()
                ? safeBaseline.displayName()
                : safeCandidate.displayName();
        return new MetadataFieldCoverageDelta(
                fieldKey,
                displayName,
                safeCandidate.coveredDocuments() - safeBaseline.coveredDocuments(),
                safeCandidate.lowConfidenceDocuments() - safeBaseline.lowConfidenceDocuments(),
                safeCandidate.reviewedDocuments() - safeBaseline.reviewedDocuments(),
                safeCandidate.correctedDocuments() - safeBaseline.correctedDocuments(),
                delta(safeCandidate.coverageRate(), safeBaseline.coverageRate()),
                delta(safeCandidate.lowConfidenceRate(), safeBaseline.lowConfidenceRate()),
                delta(safeCandidate.correctionRate(), safeBaseline.correctionRate()));
    }

    /**
     * 使用十进制差值规避双精度尾差，保证报表输出稳定。
     */
    private double delta(double candidateValue, double baselineValue) {
        return BigDecimal.valueOf(candidateValue)
                .subtract(BigDecimal.valueOf(baselineValue))
                .doubleValue();
    }

    private void recordQualityComparison(MetadataQualityComparisonReport comparison) {
        if (observationPort == null || comparison == null) {
            return;
        }
        try {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("tenantId", comparison.tenantId());
            attributes.put("knowledgeBaseId", comparison.knowledgeBaseId());
            attributes.put("coverageDelta", Double.toString(comparison.delta().averageFieldCoverageDelta()));
            attributes.put("lowConfidenceDelta", Double.toString(comparison.delta().lowConfidenceRatioDelta()));
            attributes.put("reviewPassRateDelta", Double.toString(comparison.delta().reviewPassRateDelta()));
            attributes.put("reviewCorrectionRateDelta", Double.toString(comparison.delta().reviewCorrectionRateDelta()));
            attributes.put("fieldDeltaCount", Integer.toString(comparison.fieldDeltas().size()));
            observationPort.recordEvent(new ObservationEvent(EVENT_QUALITY_COMPARE, null, attributes));
        } catch (RuntimeException ignored) {
            // 对比报表属于运维能力，观测失败不能反向打断查询。
        }
    }
}
