package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Map;

/**
 * Canonical metadata 写入端口。
 *
 * <p>实现方负责把已通过治理的元数据写入权威存储；如果存在 chunk 级 metadata 存储，
 * 也应同步合并到分块快照，保证后续索引补偿可以读取到最新治理结果。
 */
public interface MetadataCanonicalWritePort {

    void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata);

    static MetadataCanonicalWritePort noop() {
        return (documentId, acceptedMetadata) -> {
        };
    }
}
