package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldCapabilityRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;

import java.util.List;
import java.util.Objects;

/**
 * Metadata Schema 管理服务。
 *
 * <p>动态 metadata 字段必须先注册到 Schema，后续过滤编译、索引映射和补偿回填都以这里为入口。
 */
public class KernelMetadataSchemaService implements MetadataSchemaInboundPort {

    private final MetadataSchemaManagementRepositoryPort repositoryPort;
    private final MetadataSchemaIndexSyncPort indexSyncPort;
    private final MetadataIndexCompensationPort indexCompensationPort;

    public KernelMetadataSchemaService(MetadataSchemaManagementRepositoryPort repositoryPort) {
        this(repositoryPort, MetadataSchemaIndexSyncPort.noop(), MetadataIndexCompensationPort.noop());
    }

    public KernelMetadataSchemaService(MetadataSchemaManagementRepositoryPort repositoryPort,
                                       MetadataSchemaIndexSyncPort indexSyncPort) {
        this(repositoryPort, indexSyncPort, MetadataIndexCompensationPort.noop());
    }

    public KernelMetadataSchemaService(MetadataSchemaManagementRepositoryPort repositoryPort,
                                       MetadataSchemaIndexSyncPort indexSyncPort,
                                       MetadataIndexCompensationPort indexCompensationPort) {
        this.repositoryPort = Objects.requireNonNullElse(repositoryPort,
                MetadataSchemaManagementRepositoryPort.empty());
        this.indexSyncPort = Objects.requireNonNullElseGet(indexSyncPort, MetadataSchemaIndexSyncPort::noop);
        this.indexCompensationPort = Objects.requireNonNullElseGet(indexCompensationPort,
                MetadataIndexCompensationPort::noop);
    }

    @Override
    public List<MetadataSchemaFieldRecord> listFields(String tenantId, String knowledgeBaseId) {
        requireText(tenantId, "tenantId must not be blank");
        return repositoryPort.listSchemaFields(tenantId, knowledgeBaseId);
    }

    @Override
    public List<MetadataSchemaFieldCapabilityRecord> listFieldCapabilities(String tenantId, String knowledgeBaseId) {
        requireText(tenantId, "tenantId must not be blank");
        return repositoryPort.listSchemaFieldCapabilities(tenantId, knowledgeBaseId);
    }

    @Override
    public MetadataSchemaFieldRecord createField(String knowledgeBaseId, MetadataSchemaFieldPayload payload) {
        MetadataSchemaFieldPayload safePayload = validate(payload).withKnowledgeBaseId(knowledgeBaseId);
        String fieldId = repositoryPort.createSchemaField(safePayload);
        MetadataSchemaFieldRecord created = repositoryPort.findSchemaField(fieldId)
                .orElseThrow(() -> new IllegalStateException("Metadata Schema 字段创建后无法读取: " + fieldId));
        syncIndexMapping(created);
        requestSchemaCompensation(created);
        return created;
    }

    @Override
    public MetadataSchemaFieldRecord updateField(String fieldId, MetadataSchemaFieldPayload payload) {
        requireText(fieldId, "fieldId must not be blank");
        MetadataSchemaFieldRecord previous = repositoryPort.findSchemaField(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Metadata Schema 字段不存在: " + fieldId));
        MetadataSchemaFieldRecord updated = repositoryPort.updateSchemaField(fieldId, validate(payload));
        syncIndexMapping(previous, updated);
        requestSchemaCompensation(previous, updated);
        return updated;
    }

    @Override
    public boolean deleteField(String fieldId) {
        requireText(fieldId, "fieldId must not be blank");
        MetadataSchemaFieldRecord previous = repositoryPort.findSchemaField(fieldId).orElse(null);
        boolean deleted = repositoryPort.deleteSchemaField(fieldId);
        if (deleted && previous != null) {
            deleteIndexMapping(previous);
            requestSchemaCompensation(previous, null);
        }
        return deleted;
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

    private void syncIndexMapping(MetadataSchemaFieldRecord field) {
        // Schema 注册是动态 metadata 进入检索索引的唯一入口，索引结构同步必须跟随字段生命周期。
        indexSyncPort.syncField(field);
    }

    private void syncIndexMapping(MetadataSchemaFieldRecord previous, MetadataSchemaFieldRecord current) {
        // 更新时需要同时携带变更前快照，适配器才能决定是否删旧建新或执行降级。
        indexSyncPort.syncFieldChange(previous, current);
    }

    private void deleteIndexMapping(MetadataSchemaFieldRecord field) {
        // 删除字段时显式下发清理信号，避免底层索引结构长期残留失效定义。
        indexSyncPort.deleteField(field);
    }

    private void requestSchemaCompensation(MetadataSchemaFieldRecord field) {
        if (!affectsRetrieval(field)) {
            return;
        }
        indexCompensationPort.compensateSchemaChange(field);
    }

    private void requestSchemaCompensation(MetadataSchemaFieldRecord previous,
                                           MetadataSchemaFieldRecord current) {
        if (!affectsRetrieval(previous) && !affectsRetrieval(current)) {
            return;
        }
        indexCompensationPort.compensateSchemaChange(previous, current);
    }

    private boolean affectsRetrieval(MetadataSchemaFieldRecord field) {
        if (field == null) {
            return false;
        }
        return field.filterable()
                || field.indexed()
                || field.backendMapping().pushdownToVector()
                || field.backendMapping().pushdownToKeyword()
                || field.backendMapping().guardOnly();
    }
}
