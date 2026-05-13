package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;

import java.util.List;

/**
 * Metadata Schema 字段管理入站端口。
 */
public interface MetadataSchemaInboundPort {

    List<MetadataSchemaFieldRecord> listFields(String tenantId, String knowledgeBaseId);

    MetadataSchemaFieldRecord createField(String knowledgeBaseId, MetadataSchemaFieldPayload payload);

    MetadataSchemaFieldRecord updateField(String fieldId, MetadataSchemaFieldPayload payload);

    boolean deleteField(String fieldId);
}
