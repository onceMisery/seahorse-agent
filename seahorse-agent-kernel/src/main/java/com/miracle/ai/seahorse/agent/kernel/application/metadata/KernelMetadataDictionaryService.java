package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataDictionaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;

import java.util.List;
import java.util.Objects;

/**
 * 元数据标准化字典管理服务。
 *
 * <p>字典只负责把可信别名映射为 canonical value，动态 metadata 过滤仍必须走 Schema 与 Filter Compiler。
 */
public class KernelMetadataDictionaryService implements MetadataDictionaryInboundPort {

    private final MetadataDictionaryManagementRepositoryPort repositoryPort;

    public KernelMetadataDictionaryService(MetadataDictionaryManagementRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNullElse(repositoryPort,
                MetadataDictionaryManagementRepositoryPort.empty());
    }

    @Override
    public List<MetadataDictionaryItemRecord> listItems(String tenantId,
                                                        String dictionaryCode,
                                                        boolean includeDisabled) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        return repositoryPort.listDictionaryItems(safeTenantId, dictionaryCode, includeDisabled);
    }

    @Override
    public MetadataDictionaryItemRecord createItem(MetadataDictionaryItemPayload payload) {
        MetadataDictionaryItemPayload safePayload = validate(payload);
        String itemId = repositoryPort.createDictionaryItem(safePayload);
        return repositoryPort.findDictionaryItem(itemId)
                .orElseThrow(() -> new IllegalStateException("Metadata Dictionary 字典项创建后无法读取: " + itemId));
    }

    @Override
    public MetadataDictionaryItemRecord updateItem(String itemId, MetadataDictionaryItemPayload payload) {
        String safeItemId = requireText(itemId, "itemId must not be blank");
        return repositoryPort.updateDictionaryItem(safeItemId, validate(payload));
    }

    @Override
    public boolean deleteItem(String itemId) {
        String safeItemId = requireText(itemId, "itemId must not be blank");
        return repositoryPort.disableDictionaryItem(safeItemId);
    }

    private MetadataDictionaryItemPayload validate(MetadataDictionaryItemPayload payload) {
        MetadataDictionaryItemPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        requireText(safePayload.tenantId(), "tenantId must not be blank");
        requireText(safePayload.dictionaryCode(), "dictionaryCode must not be blank");
        requireText(safePayload.rawValue(), "rawValue must not be blank");
        requireText(safePayload.canonicalValue(), "canonicalValue must not be blank");
        return safePayload;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
