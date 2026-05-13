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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于 PostgreSQL/JDBC 的轻量关键词检索 fallback。
 * <p>
 * 生产默认仍建议使用 Elasticsearch；该实现用于本地开发或低中间件依赖部署，动态字段只接收编译后的过滤结果。
 */
public class JdbcKeywordSearchAdapter implements KeywordSearchPort {

    private static final int ENABLED_VALUE = 1;
    private static final String META_TENANT_ID = "tenant_id";
    private static final String META_KB_ID = "kb_id";
    private static final String META_DOC_ID = "doc_id";
    private static final String META_CHUNK_INDEX = "chunk_index";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private Boolean metadataJsonColumnExists;
    private Boolean searchTextColumnExists;

    public JdbcKeywordSearchAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<RetrievedChunk> search(KeywordSearchRequest request) {
        KeywordSearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        if (safeRequest.query().isBlank()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        String sql = searchSql(safeRequest, args);
        return jdbcTemplate.query(sql, this::toRetrievedChunk, args.toArray());
    }

    private String searchSql(KeywordSearchRequest request, List<Object> args) {
        boolean hasMetadata = metadataJsonColumnExists();
        String metadataSelect = hasMetadata ? "metadata_json" : "'{}' AS metadata_json";
        String searchVector = searchVectorExpression();
        StringBuilder sql = new StringBuilder("""
                WITH keyword_query AS (SELECT websearch_to_tsquery('simple', ?) AS q)
                SELECT id, kb_id, doc_id, chunk_index, content, enabled, %s,
                       ts_rank_cd(%s, keyword_query.q) AS rank_score
                FROM t_knowledge_chunk, keyword_query
                WHERE deleted = 0 AND enabled = ?
                  AND %s @@ keyword_query.q
                """.formatted(metadataSelect, searchVector, searchVector));
        args.add(request.query().trim());
        args.add(ENABLED_VALUE);
        appendSystemFilter(sql, args, request.compiledFilter().sourceFilter().system());
        sql.append(" ORDER BY rank_score DESC, update_time DESC LIMIT ?");
        args.add(request.topK());
        return sql.toString();
    }

    private void appendSystemFilter(StringBuilder sql, List<Object> args, SystemRetrievalFilter filter) {
        if (filter == null) {
            return;
        }
        appendIn(sql, args, "kb_id", filter.knowledgeBaseIds());
        appendIn(sql, args, "doc_id", filter.documentIds());
        // tenant_id 等字段通常位于 metadata_json，复杂动态过滤交给 MetadataGuardPostProcessor 兜底。
    }

    private void appendIn(StringBuilder sql, List<Object> args, String column, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String placeholders = values.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        sql.append(" AND ").append(column).append(" IN (").append(placeholders).append(")");
        args.addAll(values);
    }

    private RetrievedChunk toRetrievedChunk(ResultSet resultSet, int rowNumber) throws SQLException {
        Map<String, Object> metadata = metadata(resultSet.getString("metadata_json"));
        String kbId = resultSet.getString("kb_id");
        String docId = resultSet.getString("doc_id");
        Integer chunkIndex = resultSet.getObject("chunk_index", Integer.class);
        metadata = withSystemMetadata(metadata, kbId, docId, chunkIndex, resultSet.getInt("enabled") == ENABLED_VALUE);
        return RetrievedChunk.builder()
                .id(resultSet.getString("id"))
                .kbId(kbId)
                .docId(docId)
                .chunkIndex(chunkIndex)
                .text(resultSet.getString("content"))
                .score(score(resultSet, rowNumber))
                .tenantId(string(metadata, META_TENANT_ID))
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> withSystemMetadata(Map<String, Object> metadata,
                                                   String kbId,
                                                   String docId,
                                                   Integer chunkIndex,
                                                   boolean enabled) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>(metadata);
        merged.putIfAbsent(META_KB_ID, kbId);
        merged.putIfAbsent(META_DOC_ID, docId);
        merged.putIfAbsent(META_CHUNK_INDEX, chunkIndex);
        merged.putIfAbsent("enabled", enabled);
        return merged;
    }

    private Map<String, Object> metadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private boolean metadataJsonColumnExists() {
        if (metadataJsonColumnExists != null) {
            return metadataJsonColumnExists;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.columns
                    WHERE lower(table_name) = 't_knowledge_chunk'
                      AND lower(column_name) = 'metadata_json'
                    """, Integer.class);
            metadataJsonColumnExists = count != null && count > 0;
        } catch (RuntimeException ex) {
            metadataJsonColumnExists = false;
        }
        return metadataJsonColumnExists;
    }

    private String searchVectorExpression() {
        if (searchTextColumnExists()) {
            // 优先使用预计算 tsvector；历史数据为空时退回 content 动态向量，避免漏召回。
            return "COALESCE(search_text, to_tsvector('simple', COALESCE(content, '')))";
        }
        return "to_tsvector('simple', COALESCE(content, ''))";
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
            searchTextColumnExists = false;
        }
        return searchTextColumnExists;
    }

    private Float score(ResultSet resultSet, int rowNumber) {
        try {
            float rankScore = resultSet.getFloat("rank_score");
            if (!resultSet.wasNull()) {
                return rankScore;
            }
        } catch (SQLException ex) {
            // 兼容旧 SQL 或测试替身结果集，无法读取 rank_score 时才退回稳定衰减分。
        }
        return Math.max(0.1F, 1.0F - rowNumber * 0.01F);
    }

    private String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : Objects.toString(value);
    }
}
