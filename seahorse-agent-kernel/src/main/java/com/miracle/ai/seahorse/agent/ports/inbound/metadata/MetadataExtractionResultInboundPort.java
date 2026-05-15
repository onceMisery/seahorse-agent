package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRecord;

/**
 * 元数据抽取结果只读查询入站端口。
 */
public interface MetadataExtractionResultInboundPort {

    MetadataExtractionResultPage page(String tenantId,
                                      String knowledgeBaseId,
                                      String documentId,
                                      String jobId,
                                      String status,
                                      long current,
                                      long size);

    default MetadataExtractionResultPage page(String tenantId,
                                             String knowledgeBaseId,
                                             String documentId,
                                             String jobId,
                                             String status,
                                             Integer schemaVersion,
                                             String extractorVersion,
                                             long current,
                                             long size) {
        // 兼容外部旧实现：未覆盖增强查询时仍走原有分页入口。
        return page(tenantId, knowledgeBaseId, documentId, jobId, status, current, size);
    }

    MetadataExtractionResultRecord queryById(String resultId);
}
