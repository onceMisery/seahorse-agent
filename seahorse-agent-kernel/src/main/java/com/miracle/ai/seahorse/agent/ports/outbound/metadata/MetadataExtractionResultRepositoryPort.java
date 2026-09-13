package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Optional;

/**
 * 元数据抽取结果聚合仓储端口。
 *
 * <p>入库流水线的结果落库与治理侧的结果追溯查询共享同一张抽取结果表与同一事务边界，
 * 因此由同一个聚合所有者承载，不再拆成写入端口与只读管理端口两个所有者。
 */
public interface MetadataExtractionResultRepositoryPort {

    void save(MetadataExtractionRecord record);

    /**
     * 保存抽取结果并返回持久化结果 ID。
     *
     * <p>旧实现只需要实现 {@link #save(MetadataExtractionRecord)}；默认返回 taskId，避免破坏已有适配器。
     */
    default String saveAndReturnId(MetadataExtractionRecord record) {
        save(record);
        return record == null ? "" : record.taskId();
    }

    default boolean hasAcceptedResult(String tenantId,
                                      Long knowledgeBaseId,
                                      Long documentId,
                                      int schemaVersion,
                                      String extractorVersion) {
        return false;
    }

    default MetadataExtractionResultPage pageExtractionResults(MetadataExtractionResultQuery query) {
        return MetadataExtractionResultPage.empty(query.current(), query.size());
    }

    default Optional<MetadataExtractionResultRecord> findExtractionResult(String resultId) {
        return Optional.empty();
    }

    static MetadataExtractionResultRepositoryPort noop() {
        return record -> {
        };
    }

    static MetadataExtractionResultRepositoryPort empty() {
        return noop();
    }
}
