package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Optional;

/**
 * 元数据隔离区聚合仓储端口。
 *
 * <p>入库流水线的隔离写入与治理侧的隔离项读取、解决、重试调度落在同一张隔离表与同一事务边界，
 * 因此由同一个聚合所有者承载，不再拆成写入端口与管理端口两个所有者。
 */
public interface MetadataQuarantinePort {

    void quarantine(MetadataQuarantineItem item);

    default MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query) {
        return MetadataQuarantinePage.empty(query.current(), query.size());
    }

    default Optional<MetadataQuarantineRecord> findQuarantineItem(String itemId) {
        return Optional.empty();
    }

    default MetadataQuarantineRecord resolveQuarantineItem(MetadataQuarantineResolution resolution) {
        throw new IllegalArgumentException("元数据隔离项不存在: " + resolution.itemId());
    }

    default MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry) {
        throw new IllegalArgumentException("元数据隔离项不存在: " + retry.itemId());
    }

    static MetadataQuarantinePort noop() {
        return item -> {
        };
    }

    static MetadataQuarantinePort empty() {
        return noop();
    }
}
