package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * Metadata Schema 到检索索引结构的同步端口。
 *
 * <p>kernel 只声明“Schema 字段需要同步到索引结构”这一领域意图，
 * Elasticsearch/OpenSearch/PostgreSQL 等具体实现放在 adapter 层。
 */
public interface MetadataSchemaIndexSyncPort {

    void syncField(MetadataSchemaFieldRecord field);

    static MetadataSchemaIndexSyncPort noop() {
        return field -> Objects.requireNonNull(field, "field must not be null");
    }
}
