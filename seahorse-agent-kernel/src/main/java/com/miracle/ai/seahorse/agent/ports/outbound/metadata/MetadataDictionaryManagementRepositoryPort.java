package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.List;
import java.util.Optional;

/**
 * 元数据标准化字典管理仓储端口。
 */
public interface MetadataDictionaryManagementRepositoryPort {

    List<MetadataDictionaryItemRecord> listDictionaryItems(String tenantId,
                                                           String dictionaryCode,
                                                           boolean includeDisabled);

    Optional<MetadataDictionaryItemRecord> findDictionaryItem(String itemId);

    String createDictionaryItem(MetadataDictionaryItemPayload payload);

    MetadataDictionaryItemRecord updateDictionaryItem(String itemId, MetadataDictionaryItemPayload payload);

    boolean disableDictionaryItem(String itemId);

    static MetadataDictionaryManagementRepositoryPort empty() {
        return new MetadataDictionaryManagementRepositoryPort() {
            @Override
            public List<MetadataDictionaryItemRecord> listDictionaryItems(String tenantId,
                                                                          String dictionaryCode,
                                                                          boolean includeDisabled) {
                return List.of();
            }

            @Override
            public Optional<MetadataDictionaryItemRecord> findDictionaryItem(String itemId) {
                return Optional.empty();
            }

            @Override
            public String createDictionaryItem(MetadataDictionaryItemPayload payload) {
                throw new IllegalStateException("Metadata Dictionary 管理仓储未配置");
            }

            @Override
            public MetadataDictionaryItemRecord updateDictionaryItem(String itemId,
                                                                     MetadataDictionaryItemPayload payload) {
                throw new IllegalArgumentException("Metadata Dictionary 字典项不存在: " + itemId);
            }

            @Override
            public boolean disableDictionaryItem(String itemId) {
                return false;
            }
        };
    }
}
