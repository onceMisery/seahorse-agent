package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.util.LinkedHashMap;
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
        recordQualityReport(report, safeSchemaVersion, safeExtractorVersion);
        return report;
    }

    private void recordQualityReport(MetadataQualityReport report,
                                     Integer schemaVersion,
                                     String extractorVersion) {
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
            attributes.put("pendingReviewCount", Integer.toString(report.pendingReviewCount()));
            attributes.put("unresolvedQuarantineCount", Integer.toString(report.unresolvedQuarantineCount()));
            attributes.put("indexSyncFailureCount", Integer.toString(report.indexSyncFailureCount()));
            // 版本筛选条件进入观测事件，便于区分不同 Schema/Extractor 的质量报表口径。
            if (schemaVersion != null) {
                attributes.put("schemaVersion", Integer.toString(schemaVersion));
            }
            if (extractorVersion != null && !extractorVersion.isBlank()) {
                attributes.put("extractorVersion", extractorVersion);
            }
            observationPort.recordEvent(new ObservationEvent(EVENT_QUALITY_REPORT, null, attributes));
        } catch (RuntimeException ignored) {
            // 质量报表查询不能被观测端口异常打断。
        }
    }
}
