package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewAuditRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;

import java.util.List;

/**
 * 元数据人工复核管理入站端口。
 */
public interface MetadataReviewInboundPort {

    MetadataReviewPage page(String tenantId, String knowledgeBaseId, MetadataReviewStatus status, long current,
                            long size);

    MetadataReviewPage page(String tenantId,
                            String knowledgeBaseId,
                            MetadataReviewStatus status,
                            String reasonCode,
                            String documentId,
                            long current,
                            long size);

    MetadataReviewRecord queryById(String itemId);

    List<MetadataReviewAuditRecord> listAudits(String itemId);

    MetadataReviewRecord approve(String itemId, MetadataReviewDecisionCommand command);

    MetadataReviewRecord correct(String itemId, MetadataReviewDecisionCommand command);

    MetadataReviewRecord ignoreField(String itemId, MetadataReviewDecisionCommand command);

    MetadataReviewRecord reExtract(String itemId, MetadataReviewDecisionCommand command);

    MetadataReviewRecord reject(String itemId, MetadataReviewDecisionCommand command);

    MetadataReviewRecord quarantine(String itemId, MetadataReviewDecisionCommand command);
}
