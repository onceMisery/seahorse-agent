package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

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

    static MetadataExtractionResultRepositoryPort noop() {
        return record -> {
        };
    }
}
