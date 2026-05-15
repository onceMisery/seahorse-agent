package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.Objects;

/**
 * 元数据 Schema 缺失异常。
 *
 * <p>用于区分可等待治理配置补齐的任务级阻塞，避免历史回填把 Schema 缺失误当成普通文档失败。
 */
public class MetadataSchemaMissingException extends RuntimeException {

    private final String tenantId;
    private final String knowledgeBaseId;

    public MetadataSchemaMissingException(String tenantId, String knowledgeBaseId) {
        super("metadata schema missing: tenantId=" + Objects.requireNonNullElse(tenantId, "")
                + ", knowledgeBaseId=" + Objects.requireNonNullElse(knowledgeBaseId, ""));
        this.tenantId = Objects.requireNonNullElse(tenantId, "");
        this.knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
    }

    public String tenantId() {
        return tenantId;
    }

    public String knowledgeBaseId() {
        return knowledgeBaseId;
    }
}
