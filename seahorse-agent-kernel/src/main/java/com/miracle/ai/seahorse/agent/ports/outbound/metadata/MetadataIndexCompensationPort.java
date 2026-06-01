package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

/**
 * 元数据索引补偿端口。
 *
 * <p>复核通过或修正后，需要重建文档检索索引；Schema 变更后，需要触发异步回填补偿。
 */
public interface MetadataIndexCompensationPort {

    void rebuildDocument(Long documentId);

    /**
     * 允许调用方显式携带租户和知识库上下文，兼容需要按 schema 重算向量元数据的实现。
     */
    default void rebuildDocument(String tenantId, Long knowledgeBaseId, Long documentId) {
        rebuildDocument(documentId);
    }

    /**
     * 字段新增时触发 schema 补偿。
     */
    default void compensateSchemaChange(MetadataSchemaFieldRecord field) {
        if (field == null) {
            return;
        }
        compensateSchemaChange(null, field);
    }

    /**
     * 字段新增、更新、删除时触发 schema 补偿。
     */
    default void compensateSchemaChange(MetadataSchemaFieldRecord previousField,
                                        MetadataSchemaFieldRecord currentField) {
        // 默认空实现，避免影响只需要文档级补偿的旧适配器。
    }

    static MetadataIndexCompensationPort noop() {
        return new MetadataIndexCompensationPort() {
            @Override
            public void rebuildDocument(Long documentId) {
            }
        };
    }
}