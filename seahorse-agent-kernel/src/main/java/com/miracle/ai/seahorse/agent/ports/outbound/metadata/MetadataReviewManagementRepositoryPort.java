package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Optional;
import java.util.List;

/**
 * 复核管理仓储端口，只承载治理表读写，不直接编排入库流程。
 */
public interface MetadataReviewManagementRepositoryPort {

    MetadataReviewPage pageReviewItems(MetadataReviewQuery query);

    Optional<MetadataReviewRecord> findReviewItem(String itemId);

    List<MetadataReviewAuditRecord> listReviewAudits(String itemId);

    MetadataReviewRecord applyReviewDecision(MetadataReviewDecision decision);

    static MetadataReviewManagementRepositoryPort empty() {
        return new MetadataReviewManagementRepositoryPort() {
            @Override
            public MetadataReviewPage pageReviewItems(MetadataReviewQuery query) {
                return MetadataReviewPage.empty(query.current(), query.size());
            }

            @Override
            public Optional<MetadataReviewRecord> findReviewItem(String itemId) {
                return Optional.empty();
            }

            @Override
            public List<MetadataReviewAuditRecord> listReviewAudits(String itemId) {
                return List.of();
            }

            @Override
            public MetadataReviewRecord applyReviewDecision(MetadataReviewDecision decision) {
                throw new IllegalArgumentException("元数据复核项不存在: " + decision.itemId());
            }
        };
    }
}
