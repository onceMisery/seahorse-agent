package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;

import java.util.List;
import java.util.Objects;

/**
 * Metadata Schema 管理服务。
 *
 * <p>动态 metadata 字段必须先注册到 Schema，后续过滤编译和索引映射才能消费。
 */
public class KernelMetadataSchemaService implements MetadataSchemaInboundPort {

    private final MetadataSchemaManagementRepositoryPort repositoryPort;

    public KernelMetadataSchemaService(MetadataSchemaManagementRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNullElse(repositoryPort,
                MetadataSchemaManagementRepositoryPort.empty());
    }

    @Override
    public List<MetadataSchemaFieldRecord> listFields(String tenantId, String knowledgeBaseId) {
        requireText(tenantId, "tenantId must not be blank");
        return repositoryPort.listSchemaFields(tenantId, knowledgeBaseId);
    }

    @Override
    public MetadataSchemaFieldRecord createField(String knowledgeBaseId, MetadataSchemaFieldPayload payload) {
        MetadataSchemaFieldPayload safePayload = validate(payload).withKnowledgeBaseId(knowledgeBaseId);
        String fieldId = repositoryPort.createSchemaField(safePayload);
        return repositoryPort.findSchemaField(fieldId)
                .orElseThrow(() -> new IllegalStateException("Metadata Schema 字段创建后无法读取: " + fieldId));
    }

    @Override
    public MetadataSchemaFieldRecord updateField(String fieldId, MetadataSchemaFieldPayload payload) {
        requireText(fieldId, "fieldId must not be blank");
        return repositoryPort.updateSchemaField(fieldId, validate(payload));
    }

    @Override
    public boolean deleteField(String fieldId) {
        requireText(fieldId, "fieldId must not be blank");
        return repositoryPort.deleteSchemaField(fieldId);
    }

    private MetadataSchemaFieldPayload validate(MetadataSchemaFieldPayload payload) {
        MetadataSchemaFieldPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        requireText(safePayload.tenantId(), "tenantId must not be blank");
        requireText(safePayload.fieldKey(), "fieldKey must not be blank");
        return safePayload;
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
