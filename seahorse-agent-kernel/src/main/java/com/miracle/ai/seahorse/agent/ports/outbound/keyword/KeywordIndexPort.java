package com.miracle.ai.seahorse.agent.ports.outbound.keyword;

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;

import java.util.List;

/**
 * 关键词索引写入端口。
 * <p>
 * 入库阶段后续可通过 Outbox 异步调用该端口，生产默认接 Elasticsearch，轻量部署可接 PostgreSQL FTS。
 */
public interface KeywordIndexPort {

    void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks);

    void deleteDocumentChunks(String kbId, String docId);

    /**
     * 按文档重建关键词索引。
     *
     * <p>默认 no-op，避免现有生产适配器在未实现重建能力时被强制破坏；需要重建任务的 adapter 可覆盖。
     */
    default void rebuildDocument(String kbId, String docId) {
    }

    /**
     * 按知识库重建关键词索引。
     *
     * <p>用于历史数据回填、Schema/mapping 变更后的批量修复，以及异步索引失败后的管理端补偿。
     */
    default void rebuildKnowledgeBase(String kbId) {
    }

    static KeywordIndexPort noop() {
        return new KeywordIndexPort() {
            @Override
            public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
            }

            @Override
            public void deleteDocumentChunks(String kbId, String docId) {
            }
        };
    }
}
