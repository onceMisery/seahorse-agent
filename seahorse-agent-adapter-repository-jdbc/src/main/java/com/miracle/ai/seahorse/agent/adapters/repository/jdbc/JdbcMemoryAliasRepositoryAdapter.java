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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolution;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class JdbcMemoryAliasRepositoryAdapter implements MemoryAliasPort {

    private static final String DEFAULT_TENANT_ID = "default";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryAliasRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<MemoryAliasResolution> resolveAlias(String userId, String tenantId, String aliasText) {
        String normalizedAlias = normalizeAlias(aliasText);
        if (!JdbcMemorySupport.hasText(userId) || !JdbcMemorySupport.hasText(normalizedAlias)) {
            return Optional.empty();
        }
        String safeTenantId = JdbcMemorySupport.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
        return jdbcTemplate.query("""
                SELECT alias_text,
                       normalized_alias,
                       canonical_entity_id,
                       canonical_name,
                       entity_type,
                       confidence_level
                FROM t_memory_entity_alias
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND normalized_alias = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                ORDER BY confidence_level DESC, update_time DESC
                LIMIT 1
                """, (rs, rowNum) -> new MemoryAliasResolution(
                        rs.getString("alias_text"),
                        rs.getString("normalized_alias"),
                        rs.getString("canonical_entity_id"),
                        rs.getString("canonical_name"),
                        rs.getString("entity_type"),
                        rs.getDouble("confidence_level")),
                userId,
                safeTenantId,
                normalizedAlias).stream().findFirst();
    }

    @Override
    public void upsertAlias(MemoryAliasCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String normalizedAlias = normalizeAlias(command.aliasText());
        if (!JdbcMemorySupport.hasText(command.userId())
                || !JdbcMemorySupport.hasText(normalizedAlias)
                || !JdbcMemorySupport.hasText(command.canonicalEntityId())) {
            return;
        }
        String id = existingId(command.userId(), command.tenantId(), normalizedAlias)
                .orElseGet(() -> "mem-alias-" + JdbcMemorySupport.nextId());
        Instant now = Instant.now();
        Map<String, Object> metadata = Map.of(
                "metadata", command.metadata(),
                "sourceMemoryIds", command.sourceMemoryIds());
        int updated = jdbcTemplate.update("""
                UPDATE t_memory_entity_alias
                SET alias_text = ?,
                    canonical_entity_id = ?,
                    canonical_name = ?,
                    entity_type = ?,
                    confidence_level = ?,
                    source_type = ?,
                    source_memory_ids = ?,
                    metadata_json = ?,
                    status = ?,
                    update_time = ?,
                    deleted = 0
                WHERE id = ?
                """,
                command.aliasText(),
                command.canonicalEntityId(),
                command.canonicalName(),
                command.entityType(),
                command.confidenceLevel(),
                command.sourceType(),
                JdbcMemorySupport.writeJson(objectMapper, Map.of("ids", command.sourceMemoryIds())),
                JdbcMemorySupport.writeJson(objectMapper, metadata),
                ACTIVE_STATUS,
                JdbcMemorySupport.timestamp(now),
                id);
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO t_memory_entity_alias
                    (id, user_id, tenant_id, alias_text, normalized_alias, canonical_entity_id, canonical_name,
                     entity_type, confidence_level, source_type, source_memory_ids, metadata_json, status,
                     create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                id,
                command.userId(),
                command.tenantId(),
                command.aliasText(),
                normalizedAlias,
                command.canonicalEntityId(),
                command.canonicalName(),
                command.entityType(),
                command.confidenceLevel(),
                command.sourceType(),
                JdbcMemorySupport.writeJson(objectMapper, Map.of("ids", command.sourceMemoryIds())),
                JdbcMemorySupport.writeJson(objectMapper, metadata),
                ACTIVE_STATUS,
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now));
    }

    @Override
    public List<MemoryAliasCandidate> findMergeCandidates(String userId, String tenantId, int limit) {
        if (!JdbcMemorySupport.hasText(userId)) {
            return List.of();
        }
        String safeTenantId = JdbcMemorySupport.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
        int safeLimit = limit <= 0 ? 20 : limit;
        return jdbcTemplate.query("""
                SELECT alias_text, canonical_entity_id, canonical_name, entity_type, confidence_level
                FROM t_memory_entity_alias
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                ORDER BY confidence_level DESC, update_time DESC
                LIMIT ?
                """, (rs, rowNum) -> new MemoryAliasCandidate(
                        rs.getString("alias_text"),
                        rs.getString("canonical_entity_id"),
                        rs.getString("canonical_name"),
                        rs.getString("entity_type"),
                        rs.getDouble("confidence_level")),
                userId,
                safeTenantId,
                safeLimit);
    }

    Optional<String> resolveCanonicalEntityId(String userId, String tenantId, String aliasText) {
        return resolveAlias(userId, tenantId, aliasText).map(MemoryAliasResolution::canonicalEntityId);
    }

    static String normalizeAlias(String aliasText) {
        return Objects.requireNonNullElse(aliasText, "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private Optional<String> existingId(String userId, String tenantId, String normalizedAlias) {
        return jdbcTemplate.query("""
                SELECT id
                FROM t_memory_entity_alias
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND normalized_alias = ?
                  AND deleted = 0
                ORDER BY update_time DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("id"),
                userId,
                tenantId,
                normalizedAlias).stream().findFirst();
    }
}
