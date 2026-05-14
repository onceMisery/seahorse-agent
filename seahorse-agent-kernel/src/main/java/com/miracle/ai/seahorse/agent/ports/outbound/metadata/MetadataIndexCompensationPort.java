package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

/**
 * 元数据索引补偿端口。
 *
 * <p>Review 通过或修正后，canonical metadata 已经写回关系库，需要通过该端口请求搜索索引重建。
 * 默认实现为空，生产环境可以绑定关键词索引重建、outbox 或补偿任务表。
 */
public interface MetadataIndexCompensationPort {

    void rebuildDocument(String documentId);

    static MetadataIndexCompensationPort noop() {
        return documentId -> {
        };
    }
}
