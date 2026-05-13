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
