package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 元数据回填任务查询条件，管理端按知识库和状态分页查看运维进度。
 */
public record MetadataBackfillJobQuery(
        String tenantId,
        String knowledgeBaseId,
        MetadataBackfillJobStatus status,
        String pipelineId,
        String operator,
        String documentId,
        String pauseReason,
        String failureKeyword,
        Boolean hasFailures,
        Boolean reExtract,
        long current,
        long size
) {

    public MetadataBackfillJobQuery {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        pipelineId = Objects.requireNonNullElse(pipelineId, "");
        operator = Objects.requireNonNullElse(operator, "");
        documentId = Objects.requireNonNullElse(documentId, "");
        pauseReason = Objects.requireNonNullElse(pauseReason, "");
        failureKeyword = Objects.requireNonNullElse(failureKeyword, "");
        current = Math.max(1L, current);
        size = Math.max(1L, Math.min(100L, size));
    }

    public MetadataBackfillJobQuery(String tenantId,
                                    String knowledgeBaseId,
                                    MetadataBackfillJobStatus status,
                                    long current,
                                    long size) {
        this(tenantId, knowledgeBaseId, status, "", "", "", "", "", null, null, current, size);
    }

    public long offset() {
        return (current - 1L) * size;
    }
}
