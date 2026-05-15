package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.util.Map;
import java.util.Objects;

/**
 * 元数据治理质量报表服务。
 */
public class KernelMetadataQualityService implements MetadataQualityInboundPort {

    private static final String EVENT_QUALITY_REPORT = "metadata.quality.report.generated";

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
        return report(tenantId, knowledgeBaseId, quarantineTopN, null, "");
    }

    @Override
    public MetadataQualityReport report(String tenantId,
                                        String knowledgeBaseId,
                                        int quarantineTopN,
                                        Integer schemaVersion,
                                        String extractorVersion) {
        int safeTopN = quarantineTopN <= 0 ? 5 : Math.min(quarantineTopN, 50);
        Integer safeSchemaVersion = schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
        String safeExtractorVersion = Objects.requireNonNullElse(extractorVersion, "");
        MetadataQualityReport report = reportRepositoryPort.report(
                tenantId, knowledgeBaseId, safeTopN, safeSchemaVersion, safeExtractorVersion);
        recordQualityReport(report);
        return report;
    }

    private void recordQualityReport(MetadataQualityReport report) {
        if (observationPort == null || report == null) {
            return;
        }
        try {
            observationPort.recordEvent(new ObservationEvent(EVENT_QUALITY_REPORT, null, Map.ofEntries(
                    Map.entry("tenantId", report.tenantId()),
                    Map.entry("knowledgeBaseId", report.knowledgeBaseId()),
                    Map.entry("totalDocuments", Integer.toString(report.totalDocuments())),
                    Map.entry("extractedDocuments", Integer.toString(report.extractedDocuments())),
                    Map.entry("averageFieldCoverage", Double.toString(report.averageFieldCoverage())),
                    Map.entry("lowConfidenceRatio", Double.toString(report.lowConfidenceRatio())),
                    Map.entry("reviewPassRate", Double.toString(report.reviewPassRate())),
                    Map.entry("pendingReviewCount", Integer.toString(report.pendingReviewCount())),
                    Map.entry("unresolvedQuarantineCount", Integer.toString(report.unresolvedQuarantineCount())),
                    Map.entry("indexSyncFailureCount", Integer.toString(report.indexSyncFailureCount())))));
        } catch (RuntimeException ignored) {
            // 质量报表查询不能被观测端口异常打断。
        }
    }
}
