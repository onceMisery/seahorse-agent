/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import static com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemorySupport.toLongId;

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于 PostgreSQL FTS 的轻量关键词索引维护适配器。
 *
 * <p>生产环境后续可以替换为 Elasticsearch 等专用实现；这里保持 JDBC fallback 的最小闭环，
 * 在分片入库后同步维护 {@code t_knowledge_chunk.search_text}，让关键词检索优先命中预计算 tsvector。
 */
public class JdbcKeywordIndexAdapter implements KeywordIndexPort {

    private final JdbcTemplate jdbcTemplate;
    private Boolean searchTextColumnExists;

    public JdbcKeywordIndexAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
        List<Long> chunkIds = chunkIds(chunks);
        if (chunkIds.isEmpty() || !searchTextColumnExists()) {
            return;
        }
        Object[] args = new Object[2 + chunkIds.size()];
        args[0] = toLongId(kbId);
        args[1] = toLongId(docId);
        for (int index = 0; index < chunkIds.size(); index++) {
            args[index + 2] = chunkIds.get(index);
        }
        jdbcTemplate.update(indexSql(chunkIds.size()), args);
    }

    @Override
    public void deleteDocumentChunks(String kbId, String docId) {
        if (!searchTextColumnExists()) {
            return;
        }
        jdbcTemplate.update(deleteSql(), toLongId(kbId), toLongId(docId));
    }

    @Override
    public void rebuildDocument(String kbId, String docId) {
        if (!hasText(kbId) || !hasText(docId) || !searchTextColumnExists()) {
            return;
        }
        jdbcTemplate.update(rebuildDocumentSql(), toLongId(kbId), toLongId(docId));
    }

    @Override
    public void rebuildKnowledgeBase(String kbId) {
        if (!hasText(kbId) || !searchTextColumnExists()) {
            return;
        }
        jdbcTemplate.update(rebuildKnowledgeBaseSql(), toLongId(kbId));
    }

    

    String indexSql(int chunkCount) {
        String placeholders = java.util.stream.IntStream.range(0, chunkCount)
                .mapToObj(ignored -> "?")
                .collect(Collectors.joining(", "));
        return """
                UPDATE t_knowledge_chunk
                   SET search_text = to_tsvector('simple', COALESCE(content, ''))
                 WHERE kb_id = ? AND doc_id = ? AND id IN (%s) AND deleted = 0
                """.formatted(placeholders);
    }

    String deleteSql() {
        return """
                UPDATE t_knowledge_chunk
                   SET search_text = NULL
                 WHERE kb_id = ? AND doc_id = ? AND deleted = 0
                """;
    }

    String rebuildDocumentSql() {
        return """
                UPDATE t_knowledge_chunk
                   SET search_text = to_tsvector('simple', COALESCE(content, ''))
                 WHERE kb_id = ? AND doc_id = ? AND deleted = 0
                """;
    }

    String rebuildKnowledgeBaseSql() {
        return """
                UPDATE t_knowledge_chunk
                   SET search_text = to_tsvector('simple', COALESCE(content, ''))
                 WHERE kb_id = ? AND deleted = 0
                """;
    }

    private List<Long> chunkIds(List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .filter(Objects::nonNull)
                .map(VectorChunk::getChunkId)
                .filter(chunkId -> chunkId != null && !chunkId.isBlank())
                .map(JdbcMemorySupport::toLongId)
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean searchTextColumnExists() {
        if (searchTextColumnExists != null) {
            return searchTextColumnExists;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.columns
                    WHERE lower(table_name) = 't_knowledge_chunk'
                      AND lower(column_name) = 'search_text'
                    """, Integer.class);
            searchTextColumnExists = count != null && count > 0;
        } catch (RuntimeException ex) {
            // 老库或测试替身不支持 information_schema 时降级跳过，避免索引维护影响主入库链路。
            searchTextColumnExists = false;
        }
        return searchTextColumnExists;
    }
}
