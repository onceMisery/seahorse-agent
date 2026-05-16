package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * Metadata Schema 到检索索引结构的同步端口。
 *
 * <p>kernel 只声明“Schema 字段发生变化，需要同步到底层索引结构”的领域意图，
 * 具体是新增 mapping、降级索引能力还是删除旧索引，由 adapter 结合后端能力决定。
 */
public interface MetadataSchemaIndexSyncPort {

    void syncField(MetadataSchemaFieldRecord field);

    /**
     * 字段更新时同步变更前后快照，便于适配器判断是否需要降级或清理旧索引。
     */
    default void syncFieldChange(MetadataSchemaFieldRecord previousField, MetadataSchemaFieldRecord currentField) {
        Objects.requireNonNull(previousField, "previousField must not be null");
        syncField(Objects.requireNonNull(currentField, "currentField must not be null"));
    }

    /**
     * 字段被软删除或彻底下线时触发索引侧清理。
     */
    default void deleteField(MetadataSchemaFieldRecord field) {
        Objects.requireNonNull(field, "field must not be null");
    }

    static MetadataSchemaIndexSyncPort noop() {
        return new MetadataSchemaIndexSyncPort() {
            @Override
            public void syncField(MetadataSchemaFieldRecord field) {
                Objects.requireNonNull(field, "field must not be null");
            }
        };
    }
}
