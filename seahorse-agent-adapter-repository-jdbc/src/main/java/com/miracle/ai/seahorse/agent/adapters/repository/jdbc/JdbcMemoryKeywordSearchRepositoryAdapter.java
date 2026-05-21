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
        String safeTenantId = JdbcMemorySupport.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
        List<MemoryKeywordHit> hits = new ArrayList<>();
        hits.addAll(searchShortTerm(userId, safeTenantId, terms, limit));
        hits.addAll(searchLongTerm(userId, safeTenantId, terms, limit));
        hits.addAll(searchSemantic(userId, safeTenantId, terms, limit));
        return hits.stream()
                .sorted(Comparator.comparing(MemoryKeywordHit::score).reversed()
                        .thenComparing(MemoryKeywordHit::memoryId))
                .limit(limit)
                .toList();
    }

    private List<MemoryKeywordHit> searchShortTerm(String userId, String tenantId, List<String> terms, int limit) {
        return jdbcTemplate.query("""
                SELECT id, memory_type AS type, content, metadata_json AS metadata_json,
                       status, generation_id, last_referenced_at, update_time
                FROM t_short_term_memory
                WHERE user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'DELETED', 'PHYSICAL_DELETED')
                  AND (LOWER(content) LIKE ? OR LOWER(metadata_json) LIKE ?)
                ORDER BY update_time DESC
                LIMIT ?
                """, (rs, rowNum) -> mapHit(rs, "SHORT_TERM", terms), userId, tenantId,
                likeAny(terms), likeAny(terms), limit);
    }

    private List<MemoryKeywordHit> searchLongTerm(String userId, String tenantId, List<String> terms, int limit) {
        return jdbcTemplate.query("""
                SELECT id, memory_category AS type, content, tags AS metadata_json,
                       status, generation_id, last_referenced_at, update_time
                FROM t_long_term_memory
                WHERE user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'DELETED', 'PHYSICAL_DELETED')
                  AND (LOWER(content) LIKE ? OR LOWER(title) LIKE ? OR LOWER(tags) LIKE ?)
                ORDER BY importance_score DESC, update_time DESC
                LIMIT ?
                """, (rs, rowNum) -> mapHit(rs, "LONG_TERM", terms), userId, tenantId,
                likeAny(terms), likeAny(terms), likeAny(terms), limit);
    }

    private List<MemoryKeywordHit> searchSemantic(String userId, String tenantId, List<String> terms, int limit) {
        return jdbcTemplate.query("""
                SELECT id, semantic_type AS type, value_json AS content, value_json AS metadata_json,
                       status, generation_id, last_referenced_at, update_time
                FROM t_semantic_memory
                WHERE user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'DELETED', 'PHYSICAL_DELETED')
                  AND (LOWER(value_json) LIKE ? OR LOWER(semantic_key) LIKE ?)
                ORDER BY update_time DESC
                LIMIT ?
                """, (rs, rowNum) -> mapHit(rs, "SEMANTIC", terms), userId, tenantId,
                likeAny(terms), likeAny(terms), limit);
    }

    private MemoryKeywordHit mapHit(ResultSet rs, String layer, List<String> terms) throws SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>(JdbcMemorySupport.parseJson(
                objectMapper,
                rs.getString("metadata_json")));
        metadata.put("type", rs.getString("type"));
        metadata.put("status", Objects.requireNonNullElse(rs.getString("status"), ACTIVE_STATUS));
        metadata.put("generationId", Objects.requireNonNullElse(rs.getString("generation_id"), ""));
        metadata.put("lastReferencedAt", JdbcMemorySupport.instant(rs.getTimestamp("last_referenced_at")));
        metadata.put("updatedAt", JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
        return new MemoryKeywordHit(
                rs.getString("id"),
                score(rs.getString("content"), metadata, terms),
                layer,
                metadata);
    }

    private double score(String content, Map<String, Object> metadata, List<String> terms) {
        String haystack = (Objects.requireNonNullElse(content, "") + " " + metadata)
                .toLowerCase(Locale.ROOT);
        double score = 0D;
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += term.length() >= 4 ? 2D : 1D;
            }
        }
        return score;
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

    private String likeAny(List<String> terms) {
        String term = terms.stream()
                .max(Comparator.comparingInt(String::length))
                .orElse("");
        return "%" + term.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }
}
