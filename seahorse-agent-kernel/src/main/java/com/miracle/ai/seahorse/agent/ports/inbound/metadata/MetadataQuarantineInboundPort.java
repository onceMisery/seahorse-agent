package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;

/**
 * 元数据隔离区管理入站端口。
 */
public interface MetadataQuarantineInboundPort {

    MetadataQuarantinePage page(String tenantId, String knowledgeBaseId, Boolean resolved, long current, long size);

    default MetadataQuarantinePage page(String tenantId,
                                        String knowledgeBaseId,
                                        Boolean resolved,
                                        String stage,
                                        String reasonCode,
                                        String documentId,
                                        String jobId,
                                        long current,
                                        long size) {
        // 兼容外部旧实现：未覆盖增强筛选方法时，仍回落到原分页契约。
        return page(tenantId, knowledgeBaseId, resolved, current, size);
    }

    MetadataQuarantineRecord queryById(String itemId);

    MetadataQuarantineRecord resolve(String itemId, String operator);

    MetadataQuarantineRecord retry(String itemId, MetadataQuarantineRetryCommand command);
}
