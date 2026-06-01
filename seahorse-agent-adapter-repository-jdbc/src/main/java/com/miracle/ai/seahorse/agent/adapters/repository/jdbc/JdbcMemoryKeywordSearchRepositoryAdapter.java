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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordSearchPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class JdbcMemoryKeywordSearchRepositoryAdapter implements MemoryKeywordSearchPort {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String DEFAULT_TENANT_ID = "default";
    private static final int DEFAULT_TOP_K = 20;
    private static final int MIN_CANDIDATES_PER_TERM = 32;
    private static final int CANDIDATE_LIMIT_MULTIPLIER = 8;
    private static final int MAX_CANDIDATES_PER_TERM = 256;
    private static final double BM25_K1 = 1.2D;
    private static final double BM25_B = 0.75D;
    private static final String SCORE_STRATEGY_BM25_LITE = "BM25_LITE";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryKeywordSearchRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MemoryKeywordHit> search(String userId, String tenantId, String query, int topK) {
        if (!JdbcMemorySupport.hasText(userId) || !JdbcMemorySupport.hasText(query)) {
            return List.of();
        }
        List<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        int limit = topK > 0 ? topK : DEFAULT_TOP_K;
        int candidateLimit = candidateLimit(limit);
        String safeTenantId = JdbcMemorySupport.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
        List<KeywordDocument> documents = new ArrayList<>();
        documents.addAll(searchKeywordIndex(userId, safeTenantId, terms, candidateLimit));
        if (!documents.isEmpty()) {
            return rank(documents, terms, limit);
        }
        documents.addAll(searchShortTerm(userId, safeTenantId, terms, candidateLimit));
        documents.addAll(searchLongTerm(userId, safeTenantId, terms, candidateLimit));
        documents.addAll(searchSemantic(userId, safeTenantId, terms, candidateLimit));
        return rank(documents, terms, limit);
    }

    private int candidateLimit(int topK) {
        int scaledLimit = Math.max(topK, DEFAULT_TOP_K) * CANDIDATE_LIMIT_MULTIPLIER;
        return Math.min(MAX_CANDIDATES_PER_TERM, Math.max(MIN_CANDIDATES_PER_TERM, scaledLimit));
    }

    private List<MemoryKeywordHit> rank(List<KeywordDocument> documents, List<String> terms, int limit) {
        if (documents.isEmpty()) {
            return List.of();
        }
        List<PreparedKeywordDocument> prepared = documents.stream()
                .map(document -> {
                    String haystack = haystack(document.content(), document.metadata());
                    return new PreparedKeywordDocument(document, haystack, documentLength(haystack));
                })
                .toList();
        double averageDocumentLength = prepared.stream()
                .mapToInt(PreparedKeywordDocument::documentLength)
                .average()
                .orElse(1D);
        Map<String, Integer> documentFrequencies = documentFrequencies(prepared, terms);
        int corpusSize = prepared.size();
        return prepared.stream()
                .map(document -> toHit(document, bm25(document, terms, documentFrequencies, corpusSize,
                        averageDocumentLength)))
                .sorted(Comparator.comparing(MemoryKeywordHit::score).reversed()
                        .thenComparing(MemoryKeywordHit::memoryId))
                .limit(limit)
                .toList();
    }

    private List<KeywordDocument> searchKeywordIndex(String userId, String tenantId, List<String> terms, int limit) {
        if (!tableExists("t_memory_keyword_index")) {
            return List.of();
        }
        Map<String, KeywordDocument> documents = new LinkedHashMap<>();
        for (String term : terms) {
            jdbcTemplate.query("""
                    SELECT memory_id AS id, layer_name, memory_type AS type, content, metadata_json,
                           status, source_update_time
                    FROM t_memory_keyword_index
                    WHERE user_id = ?
                      AND tenant_id = ?
                      AND deleted = 0
                      AND status = 'ACTIVE'
                      AND %s
                    ORDER BY update_time DESC
                    LIMIT ?
                    """.formatted(termFilter(List.of(term), "LOWER(content)", "LOWER(CAST(metadata_json AS VARCHAR))")),
                    (rs, rowNum) -> mapKeywordIndexDocument(rs),
                    searchArgs(userId, tenantId, List.of(term), 2, limit))
                    .forEach(document -> documents.putIfAbsent(document.memoryId(), document));
        }
        return List.copyOf(documents.values());
    }

    private List<KeywordDocument> searchShortTerm(String userId, String tenantId, List<String> terms, int limit) {
        return jdbcTemplate.query("""
                SELECT id, memory_type AS type, content, metadata_json AS metadata_json,
                       status, generation_id, last_referenced_at, update_time
                FROM t_short_term_memory
                WHERE user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                  AND %s
                ORDER BY update_time DESC
                LIMIT ?
                """.formatted(termFilter(terms, "LOWER(content)", "LOWER(CAST(metadata_json AS VARCHAR))")),
                (rs, rowNum) -> mapDocument(rs, "SHORT_TERM"),
                searchArgs(userId, tenantId, terms, 2, limit));
    }

    private List<KeywordDocument> searchLongTerm(String userId, String tenantId, List<String> terms, int limit) {
        return jdbcTemplate.query("""
                SELECT id, memory_category AS type, content, tags AS metadata_json,
                       status, generation_id, last_referenced_at, update_time
                FROM t_long_term_memory
                WHERE user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                  AND %s
                ORDER BY importance_score DESC, update_time DESC
                LIMIT ?
                """.formatted(termFilter(terms, "LOWER(content)", "LOWER(title)", "LOWER(CAST(tags AS VARCHAR))")),
                (rs, rowNum) -> mapDocument(rs, "LONG_TERM"),
                searchArgs(userId, tenantId, terms, 3, limit));
    }

    private List<KeywordDocument> searchSemantic(String userId, String tenantId, List<String> terms, int limit) {
        return jdbcTemplate.query("""
                SELECT id, semantic_type AS type, value_json AS content, value_json AS metadata_json,
                       status, generation_id, last_referenced_at, update_time
                FROM t_semantic_memory
                WHERE user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                  AND %s
                ORDER BY update_time DESC
                LIMIT ?
                """.formatted(termFilter(terms, "LOWER(CAST(value_json AS VARCHAR))", "LOWER(semantic_key)")),
                (rs, rowNum) -> mapDocument(rs, "SEMANTIC"),
                searchArgs(userId, tenantId, terms, 2, limit));
    }

    private KeywordDocument mapDocument(ResultSet rs, String layer) throws SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>(JdbcMemorySupport.parseJson(
                objectMapper,
                rs.getString("metadata_json")));
        metadata.put("type", rs.getString("type"));
        metadata.put("status", Objects.requireNonNullElse(rs.getString("status"), ACTIVE_STATUS));
        metadata.put("generationId", Objects.requireNonNullElse(rs.getString("generation_id"), ""));
        metadata.put("lastReferencedAt", JdbcMemorySupport.instant(rs.getTimestamp("last_referenced_at")));
        metadata.put("updatedAt", JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
        return new KeywordDocument(
                rs.getString("id"),
                layer,
                rs.getString("content"),
                metadata);
    }

    private KeywordDocument mapKeywordIndexDocument(ResultSet rs) throws SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>(JdbcMemorySupport.parseJson(
                objectMapper,
                rs.getString("metadata_json")));
        metadata.put("type", rs.getString("type"));
        metadata.put("status", Objects.requireNonNullElse(rs.getString("status"), ACTIVE_STATUS));
        metadata.put("updatedAt", JdbcMemorySupport.instant(rs.getTimestamp("source_update_time")));
        return new KeywordDocument(
                rs.getString("id"),
                Objects.requireNonNullElse(rs.getString("layer_name"), ""),
                rs.getString("content"),
                metadata);
    }

    private MemoryKeywordHit toHit(PreparedKeywordDocument prepared, double score) {
        Map<String, Object> metadata = new LinkedHashMap<>(prepared.document().metadata());
        metadata.put("keywordScoreStrategy", SCORE_STRATEGY_BM25_LITE);
        return new MemoryKeywordHit(
                prepared.document().memoryId(),
                score,
                prepared.document().layer(),
                metadata);
    }

    private double bm25(PreparedKeywordDocument document,
                        List<String> terms,
                        Map<String, Integer> documentFrequencies,
                        int corpusSize,
                        double averageDocumentLength) {
        double score = 0D;
        for (String term : terms) {
            int termFrequency = frequency(document.haystack(), term);
            if (termFrequency > 0) {
                int documentFrequency = Math.max(1, documentFrequencies.getOrDefault(term, 1));
                double idf = Math.log(1D + (corpusSize - documentFrequency + 0.5D)
                        / (documentFrequency + 0.5D));
                double lengthRatio = document.documentLength() / Math.max(1D, averageDocumentLength);
                double denominator = termFrequency + BM25_K1 * (1D - BM25_B + BM25_B * lengthRatio);
                score += idf * (termFrequency * (BM25_K1 + 1D)) / denominator;
            }
        }
        return score;
    }

    private Map<String, Integer> documentFrequencies(List<PreparedKeywordDocument> documents, List<String> terms) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String term : terms) {
            int count = 0;
            for (PreparedKeywordDocument document : documents) {
                if (document.haystack().contains(term)) {
                    count++;
                }
            }
            frequencies.put(term, count);
        }
        return frequencies;
    }

    private int frequency(String haystack, String term) {
        int count = 0;
        int index = haystack.indexOf(term);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(term, index + term.length());
        }
        return count;
    }

    private String haystack(String content, Map<String, Object> metadata) {
        return (Objects.requireNonNullElse(content, "") + " " + metadata)
                .toLowerCase(Locale.ROOT);
    }

    private int documentLength(String haystack) {
        String normalized = Objects.requireNonNullElse(haystack, "")
                .replaceAll("[^\\p{L}\\p{N}_\\-.]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return 1;
        }
        return Math.max(1, normalized.split("\\s+").length);
    }

    private List<String> terms(String query) {
        String normalized = query.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}_\\-.]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String term : normalized.split("\\s+")) {
            if (term.length() >= 2 && !values.contains(term)) {
                values.add(term);
            }
        }
        return values;
    }

    private String termFilter(List<String> terms, String... columns) {
        List<String> clauses = new ArrayList<>();
        for (String ignored : terms) {
            List<String> termClauses = new ArrayList<>();
            for (String column : columns) {
                termClauses.add(column + " LIKE ?");
            }
            clauses.add("(" + String.join(" OR ", termClauses) + ")");
        }
        return "(" + String.join(" OR ", clauses) + ")";
    }

    private Object[] searchArgs(String userId, String tenantId, List<String> terms, int columnCount, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(JdbcMemorySupport.toLongId(userId));
        args.add(tenantId);
        for (String term : terms) {
            for (int i = 0; i < columnCount; i++) {
                args.add(like(term));
            }
        }
        args.add(limit);
        return args.toArray();
    }

    private String like(String term) {
        return "%" + term.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private boolean tableExists(String tableName) {
        return !jdbcTemplate.query(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE lower(table_name) = lower(?)
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        tableName)
                .isEmpty();
    }

    private record KeywordDocument(String memoryId,
                                   String layer,
                                   String content,
                                   Map<String, Object> metadata) {
    }

    private record PreparedKeywordDocument(KeywordDocument document, String haystack, int documentLength) {
    }
}
