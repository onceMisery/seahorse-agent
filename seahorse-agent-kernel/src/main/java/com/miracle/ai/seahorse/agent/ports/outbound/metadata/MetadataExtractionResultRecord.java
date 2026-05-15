package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据抽取结果只读记录。
 */
public record MetadataExtractionResultRecord(
        String id,
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String jobId,
        int schemaVersion,
        String extractorVersion,
        String status,
        Map<String, Object> normalizedMetadata,
        List<Map<String, Object>> rawCandidates,
        List<Map<String, Object>> fieldQuality,
        List<Map<String, Object>> validationIssues,
        Map<String, Object> approvedMetadata,
        String approvedBy,
        Instant approvedTime,
        Instant createTime,
        Instant updateTime
) {

    public MetadataExtractionResultRecord {
        id = Objects.requireNonNullElse(id, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        documentId = Objects.requireNonNullElse(documentId, "");
        jobId = Objects.requireNonNullElse(jobId, "");
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        extractorVersion = Objects.requireNonNullElse(extractorVersion, "");
        status = Objects.requireNonNullElse(status, "");
        normalizedMetadata = Map.copyOf(Objects.requireNonNullElse(normalizedMetadata, Map.of()));
        rawCandidates = List.copyOf(Objects.requireNonNullElse(rawCandidates, List.of()));
        fieldQuality = List.copyOf(Objects.requireNonNullElse(fieldQuality, List.of()));
        validationIssues = List.copyOf(Objects.requireNonNullElse(validationIssues, List.of()));
        approvedMetadata = Map.copyOf(Objects.requireNonNullElse(approvedMetadata, Map.of()));
        approvedBy = Objects.requireNonNullElse(approvedBy, "");
        createTime = Objects.requireNonNullElseGet(createTime, Instant::now);
        updateTime = Objects.requireNonNullElseGet(updateTime, Instant::now);
    }
}
