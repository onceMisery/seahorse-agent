package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.List;
import java.util.Optional;

/**
 * 元数据复核聚合仓储端口。
 *
 * <p>入库流水线的复核入队与治理侧的复核项读取、审计追溯、决策落库共享同一张复核表与同一事务边界，
 * 因此由同一个聚合所有者承载，不再拆成入队端口与管理端口两个所有者。
 */
public interface MetadataReviewQueuePort {

    void enqueue(MetadataReviewItem item);

    default MetadataReviewPage pageReviewItems(MetadataReviewQuery query) {
        return MetadataReviewPage.empty(query.current(), query.size());
    }

    default Optional<MetadataReviewRecord> findReviewItem(String itemId) {
        return Optional.empty();
    }

    default List<MetadataReviewAuditRecord> listReviewAudits(String itemId) {
        return List.of();
    }

    default MetadataReviewRecord applyReviewDecision(MetadataReviewDecision decision) {
        throw new IllegalArgumentException("元数据复核项不存在: " + decision.itemId());
    }

    static MetadataReviewQueuePort noop() {
        return item -> {
        };
    }

    static MetadataReviewQueuePort empty() {
        return noop();
    }
}
