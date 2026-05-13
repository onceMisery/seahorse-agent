package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Optional;

/**
 * 隔离区管理仓储端口。
 */
public interface MetadataQuarantineManagementRepositoryPort {

    MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query);

    Optional<MetadataQuarantineRecord> findQuarantineItem(String itemId);

    MetadataQuarantineRecord resolveQuarantineItem(MetadataQuarantineResolution resolution);

    MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry);

    static MetadataQuarantineManagementRepositoryPort empty() {
        return new MetadataQuarantineManagementRepositoryPort() {
            @Override
            public MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query) {
                return MetadataQuarantinePage.empty(query.current(), query.size());
            }

            @Override
            public Optional<MetadataQuarantineRecord> findQuarantineItem(String itemId) {
                return Optional.empty();
            }

            @Override
            public MetadataQuarantineRecord resolveQuarantineItem(MetadataQuarantineResolution resolution) {
                throw new IllegalArgumentException("元数据隔离项不存在: " + resolution.itemId());
            }

            @Override
            public MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry) {
                throw new IllegalArgumentException("元数据隔离项不存在: " + retry.itemId());
            }
        };
    }
}
