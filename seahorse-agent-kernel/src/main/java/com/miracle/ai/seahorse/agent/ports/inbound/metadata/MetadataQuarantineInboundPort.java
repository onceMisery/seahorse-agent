package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;

/**
 * 元数据隔离区管理入站端口。
 */
public interface MetadataQuarantineInboundPort {

    MetadataQuarantinePage page(String tenantId, String knowledgeBaseId, Boolean resolved, long current, long size);

    MetadataQuarantineRecord queryById(String itemId);

    MetadataQuarantineRecord resolve(String itemId, String operator);

    MetadataQuarantineRecord retry(String itemId, MetadataQuarantineRetryCommand command);
}
