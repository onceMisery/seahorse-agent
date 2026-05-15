package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Optional;

/**
 * 元数据抽取结果只读管理仓储端口。
 */
public interface MetadataExtractionResultManagementRepositoryPort {

    MetadataExtractionResultPage pageExtractionResults(MetadataExtractionResultQuery query);

    Optional<MetadataExtractionResultRecord> findExtractionResult(String resultId);

    static MetadataExtractionResultManagementRepositoryPort empty() {
        return new MetadataExtractionResultManagementRepositoryPort() {
            @Override
            public MetadataExtractionResultPage pageExtractionResults(MetadataExtractionResultQuery query) {
                return MetadataExtractionResultPage.empty(query.current(), query.size());
            }

            @Override
            public Optional<MetadataExtractionResultRecord> findExtractionResult(String resultId) {
                return Optional.empty();
            }
        };
    }
}
