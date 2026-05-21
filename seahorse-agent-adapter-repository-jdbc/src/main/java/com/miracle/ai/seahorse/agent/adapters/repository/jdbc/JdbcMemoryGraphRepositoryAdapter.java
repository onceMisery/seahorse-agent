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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDeleteCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDocument;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class JdbcMemoryGraphRepositoryAdapter implements MemoryGraphPort, MemoryGraphIndexPort {

    private static final String CHANNEL = "graph";
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String DEFAULT_RELATION_TYPE = "MENTIONS";
    private static final double DEFAULT_WEIGHT = 1D;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryGraphRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void upsert(MemoryDerivedIndexDocument document) {
        if (document == null || !JdbcMemorySupport.hasText(document.memoryId())
                || !JdbcMemorySupport.hasText(document.userId())) {
            return;
        }
        String sourceEntityId = text(document.metadata().get("canonicalEntityId"));
        if (!JdbcMemorySupport.hasText(sourceEntityId)) {
            sourceEntityId = text(document.metadata().get("semanticKey"));
        }
        if (!JdbcMemorySupport.hasText(sourceEntityId)) {
            return;
        }
        String canonicalName = text(document.metadata().get("canonicalName"));
        if (!JdbcMemorySupport.hasText(canonicalName)) {
            canonicalName = sourceEntityId;
        }
        List<String> targetEntityIds = targetEntityIds(document.metadata(), sourceEntityId);
        String relationType = text(document.metadata().get("relationType"));
        if (!JdbcMemorySupport.hasText(relationType)) {
            relationType = DEFAULT_RELATION_TYPE;
        }
        delete(new MemoryDerivedIndexDeleteCommand(document.memoryId(), document.userId(), document.tenantId()));
        Instant now = Instant.now();
        for (String targetEntityId : targetEntityIds) {
            insertRelation(document, sourceEntityId, canonicalName, targetEntityId, relationType, now);
        }
    }

    @Override
    public void delete(MemoryDerivedIndexDeleteCommand command) {
        if (command == null || !JdbcMemorySupport.hasText(command.memoryId())) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE t_memory_entity_relation
                SET status = 'DELETED',
                    update_time = ?,
                    deleted = 1
                WHERE memory_id = ?
                  AND user_id = ?
                  AND tenant_id = ?
                  AND deleted = 0
                """,
                JdbcMemorySupport.timestamp(Instant.now()),
                command.memoryId(),
                command.userId(),
                safeTenantId(command.tenantId()));
    }

    @Override
    public List<MemoryRecallCandidate> recallNeighborhood(MemoryRecallRequest request, int maxHops) {
        if (request == null || !JdbcMemorySupport.hasText(request.userId())
                || !JdbcMemorySupport.hasText(request.query())) {
            return List.of();
        }
        String safeTenantId = safeTenantId(request.tenantId());
        List<String> entityIds = entityIdsFromQuery(request.userId(), safeTenantId, request.query());
        if (entityIds.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, request.topK());
        List<MemoryRecallCandidate> candidates = new ArrayList<>();
        for (String entityId : entityIds) {
            candidates.addAll(recallByEntity(request.userId(), safeTenantId, entityId, limit));
            if (candidates.size() >= limit) {
                break;
            }
        }
        return candidates.stream()
                .limit(limit)
                .toList();
    }

    private void insertRelation(MemoryDerivedIndexDocument document,
                                String sourceEntityId,
                                String canonicalName,
                                String targetEntityId,
                                String relationType,
                                Instant now) {
        String id = "mem-rel-" + JdbcMemorySupport.nextId();
        Map<String, Object> metadata = new LinkedHashMap<>(document.metadata());
        metadata.put("canonicalEntityId", sourceEntityId);
        metadata.put("canonicalName", canonicalName);
        jdbcTemplate.update("""
                INSERT INTO t_memory_entity_relation
                    (id, user_id, tenant_id, memory_id, layer_name, memory_type, content, source_entity_id,
                     target_entity_id, relation_type, weight, confidence_level, metadata_json, status,
                     create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 0)
                """,
                id,
                document.userId(),
                safeTenantId(document.tenantId()),
                document.memoryId(),
                document.layer(),
                document.type(),
                document.content(),
                sourceEntityId,
                targetEntityId,
                relationType,
                number(document.metadata().get("weight")).orElse(DEFAULT_WEIGHT),
                number(document.metadata().get("confidenceLevel")).orElse(DEFAULT_WEIGHT),
                JdbcMemorySupport.writeJson(objectMapper, metadata),
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now));
    }

    private List<MemoryRecallCandidate> recallByEntity(String userId, String tenantId, String entityId, int limit) {
        return jdbcTemplate.query("""
                SELECT memory_id, layer_name, memory_type, content, source_entity_id, target_entity_id, relation_type,
                       weight, confidence_level, metadata_json, status
                FROM t_memory_entity_relation
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND deleted = 0
                  AND status = 'ACTIVE'
                  AND (source_entity_id = ? OR target_entity_id = ?)
                ORDER BY weight DESC, confidence_level DESC, update_time DESC
                LIMIT ?
                """, (rs, rowNum) -> mapCandidate(rs, userId, tenantId, rowNum + 1),
                userId,
                tenantId,
                entityId,
                entityId,
                limit);
    }

    private MemoryRecallCandidate mapCandidate(ResultSet rs, String userId, String tenantId, int rank)
            throws SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>(JdbcMemorySupport.parseJson(
                objectMapper,
                rs.getString("metadata_json")));
        metadata.put("canonicalEntityId", rs.getString("source_entity_id"));
        metadata.put("targetEntityId", rs.getString("target_entity_id"));
        metadata.put("relationType", rs.getString("relation_type"));
        metadata.put("graphMatch", "alias");
        double score = rs.getDouble("weight") * rs.getDouble("confidence_level");
        return new MemoryRecallCandidate(
                rs.getString("memory_id"),
                CHANNEL,
                rank,
                score,
                userId,
                tenantId,
                rs.getString("layer_name"),
                rs.getString("memory_type"),
                rs.getString("content"),
                "",
                rs.getString("status"),
                metadata);
    }

    private List<String> entityIdsFromQuery(String userId, String tenantId, String query) {
        List<String> entityIds = new ArrayList<>();
        for (String alias : aliases(query)) {
            resolveCanonicalEntityId(userId, tenantId, alias).ifPresent(entityId -> {
                if (!entityIds.contains(entityId)) {
                    entityIds.add(entityId);
                }
            });
        }
        if (entityIds.isEmpty()) {
            for (String term : aliases(query)) {
                if (!entityIds.contains(term)) {
                    entityIds.add(term);
                }
            }
        }
        return entityIds;
    }

    private Optional<String> resolveCanonicalEntityId(String userId, String tenantId, String alias) {
        String normalizedAlias = JdbcMemoryAliasRepositoryAdapter.normalizeAlias(alias);
        if (!JdbcMemorySupport.hasText(normalizedAlias)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT canonical_entity_id
                FROM t_memory_entity_alias
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND normalized_alias = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                ORDER BY confidence_level DESC, update_time DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("canonical_entity_id"),
                userId,
                tenantId,
                normalizedAlias).stream().findFirst();
    }

    private List<String> aliases(String query) {
        String normalized = Objects.requireNonNullElse(query, "")
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> aliases = new ArrayList<>();
        addAlias(aliases, normalized);
        for (String token : normalized.split("[^\\p{L}\\p{N}_\\-.]+")) {
            addAlias(aliases, token);
        }
        return aliases;
    }

    private void addAlias(List<String> aliases, String value) {
        String alias = Objects.requireNonNullElse(value, "").trim();
        if (alias.length() >= 2 && !aliases.contains(alias)) {
            aliases.add(alias);
        }
    }

    private List<String> targetEntityIds(Map<String, Object> metadata, String sourceEntityId) {
        Object value = metadata.get("relatedEntityIds");
        if (value instanceof Iterable<?> iterable) {
            List<String> ids = new ArrayList<>();
            for (Object item : iterable) {
                String id = text(item);
                if (JdbcMemorySupport.hasText(id) && !ids.contains(id)) {
                    ids.add(id);
                }
            }
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        String targetEntityId = text(metadata.get("targetEntityId"));
        if (JdbcMemorySupport.hasText(targetEntityId)) {
            return List.of(targetEntityId);
        }
        return List.of(sourceEntityId);
    }

    private Optional<Double> number(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        if (value != null) {
            try {
                return Optional.of(Double.parseDouble(value.toString()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String text(Object value) {
        return Objects.requireNonNullElse(value, "").toString().trim();
    }

    private String safeTenantId(String tenantId) {
        String normalized = Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID).trim();
        return normalized.isBlank() ? DEFAULT_TENANT_ID : normalized;
    }
}
