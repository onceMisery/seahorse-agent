package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldQuality;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValidationDecision;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MetadataExtractionRecord(
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String taskId,
        int schemaVersion,
        String extractorVersion,
        MetadataValidationDecision status,
        Map<String, Object> normalizedMetadata,
        Map<String, Object> acceptedMetadata,
        List<MetadataFieldQuality> fieldQualities,
        List<MetadataIssue> issues,
        List<MetadataFieldCandidate> rawCandidates
) {

    public MetadataExtractionRecord {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        documentId = Objects.requireNonNullElse(documentId, "");
        taskId = Objects.requireNonNullElse(taskId, "");
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        extractorVersion = Objects.requireNonNullElse(extractorVersion, "");
        status = Objects.requireNonNullElse(status, MetadataValidationDecision.ACCEPT);
        normalizedMetadata = Map.copyOf(Objects.requireNonNullElse(normalizedMetadata, Map.of()));
        acceptedMetadata = Map.copyOf(Objects.requireNonNullElse(acceptedMetadata, Map.of()));
        fieldQualities = List.copyOf(Objects.requireNonNullElse(fieldQualities, List.of()));
        issues = List.copyOf(Objects.requireNonNullElse(issues, List.of()));
        rawCandidates = List.copyOf(Objects.requireNonNullElse(rawCandidates, List.of()));
    }

    public MetadataExtractionRecord(String tenantId,
                                    String knowledgeBaseId,
                                    String documentId,
                                    String taskId,
                                    int schemaVersion,
                                    String extractorVersion,
                                    MetadataValidationDecision status,
                                    Map<String, Object> normalizedMetadata,
                                    Map<String, Object> acceptedMetadata,
                                    List<MetadataFieldQuality> fieldQualities,
                                    List<MetadataIssue> issues) {
        this(tenantId, knowledgeBaseId, documentId, taskId, schemaVersion, extractorVersion, status,
                normalizedMetadata, acceptedMetadata, fieldQualities, issues, List.of());
    }
}
