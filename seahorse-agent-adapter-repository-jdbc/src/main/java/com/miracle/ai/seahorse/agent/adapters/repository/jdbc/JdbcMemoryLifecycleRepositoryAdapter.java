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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public class JdbcMemoryLifecycleRepositoryAdapter implements MemoryLifecyclePort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemoryLifecycleRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public int markObsoleteByProfileSlot(String userId,
                                         String tenantId,
                                         String profileSlot,
                                         String activeGenerationId,
                                         String reason) {
        if (!JdbcMemorySupport.hasText(userId) || !JdbcMemorySupport.hasText(profileSlot)) {
            return 0;
        }
        String safeTenantId = JdbcMemorySupport.hasText(tenantId) ? tenantId : "default";
        String safeReason = Objects.requireNonNullElse(reason, "profile slot updated");
        Instant now = Instant.now();
        return markShortTermObsolete(userId, safeTenantId, profileSlot, activeGenerationId, safeReason, now)
                + markLongTermObsolete(userId, safeTenantId, profileSlot, activeGenerationId, safeReason, now)
                + markSemanticObsolete(userId, safeTenantId, profileSlot, activeGenerationId, safeReason, now);
    }

    @Override
    public void recordRead(String layer, String memoryId, Instant referencedAt) {
        if (!JdbcMemorySupport.hasText(memoryId)) {
            return;
        }
        String table = table(layer);
        if (table.isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE %s
                SET last_referenced_at = ?,
                    status = CASE WHEN status = 'ACTIVE' THEN 'REFERENCED' ELSE status END,
                    update_time = ?
                WHERE id = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'DELETED', 'PHYSICAL_DELETED')
                """.formatted(table),
                JdbcMemorySupport.timestamp(Objects.requireNonNullElseGet(referencedAt, Instant::now)),
                JdbcMemorySupport.timestamp(Instant.now()),
                memoryId);
        if ("t_short_term_memory".equals(table)) {
            jdbcTemplate.update("""
                    UPDATE t_short_term_memory
                    SET access_count = COALESCE(access_count, 0) + 1,
                        last_access_time = ?
                    WHERE id = ?
                      AND deleted = 0
                      AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'DELETED', 'PHYSICAL_DELETED')
                    """,
                    JdbcMemorySupport.timestamp(Objects.requireNonNullElseGet(referencedAt, Instant::now)),
                    memoryId);
        }
    }

    private int markShortTermObsolete(String userId,
                                      String tenantId,
                                      String profileSlot,
                                      String activeGenerationId,
                                      String reason,
                                      Instant now) {
        return jdbcTemplate.update("""
                UPDATE t_short_term_memory
                SET status = 'OBSOLETE',
                    obsolete_reason = ?,
                    valid_until = ?,
                    update_time = ?
                WHERE user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'DELETED', 'PHYSICAL_DELETED')
                  AND COALESCE(generation_id, '') <> ?
                  AND (
                        memory_type = 'PROFILE'
                     OR
                        CAST(metadata_json AS VARCHAR) LIKE ?
                     OR CAST(metadata_json AS VARCHAR) LIKE ?
                     OR CAST(metadata_json AS VARCHAR) LIKE ?
                     OR CAST(metadata_json AS VARCHAR) LIKE ?
                     OR CAST(metadata_json AS VARCHAR) LIKE ?
                     OR CAST(metadata_json AS VARCHAR) LIKE ?
                  )
                """,
                reason,
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now),
                userId,
                tenantId,
                Objects.requireNonNullElse(activeGenerationId, ""),
                containsJsonValue("profileSlot", profileSlot),
                containsJsonValue("semanticKey", profileSlot),
                containsJsonValue("semanticKey", legacyProfileSemanticKey(profileSlot)),
                containsJsonValueWithSpace("profileSlot", profileSlot),
                containsJsonValueWithSpace("semanticKey", profileSlot),
                containsJsonValueWithSpace("semanticKey", legacyProfileSemanticKey(profileSlot)));
    }

    private int markLongTermObsolete(String userId,
                                     String tenantId,
                                     String profileSlot,
                                     String activeGenerationId,
                                     String reason,
                                     Instant now) {
        return jdbcTemplate.update("""
                UPDATE t_long_term_memory
                SET status = 'OBSOLETE',
                    obsolete_reason = ?,
                    valid_until = ?,
                    update_time = ?
                WHERE user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'DELETED', 'PHYSICAL_DELETED')
                  AND COALESCE(generation_id, '') <> ?
                  AND (
                        memory_category = 'PROFILE'
                     OR
                        CAST(tags AS VARCHAR) LIKE ?
                     OR CAST(tags AS VARCHAR) LIKE ?
                     OR CAST(tags AS VARCHAR) LIKE ?
                     OR CAST(tags AS VARCHAR) LIKE ?
                     OR CAST(tags AS VARCHAR) LIKE ?
                     OR CAST(tags AS VARCHAR) LIKE ?
                  )
                """,
                reason,
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now),
                userId,
                tenantId,
                Objects.requireNonNullElse(activeGenerationId, ""),
                containsJsonValue("profileSlot", profileSlot),
                containsJsonValue("semanticKey", profileSlot),
                containsJsonValue("semanticKey", legacyProfileSemanticKey(profileSlot)),
                containsJsonValueWithSpace("profileSlot", profileSlot),
                containsJsonValueWithSpace("semanticKey", profileSlot),
                containsJsonValueWithSpace("semanticKey", legacyProfileSemanticKey(profileSlot)));
    }

    private int markSemanticObsolete(String userId,
                                     String tenantId,
                                     String profileSlot,
                                     String activeGenerationId,
                                     String reason,
                                     Instant now) {
        return jdbcTemplate.update("""
                UPDATE t_semantic_memory
                SET status = 'OBSOLETE',
                    obsolete_reason = ?,
                    valid_until = ?,
                    update_time = ?
                WHERE user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'DELETED', 'PHYSICAL_DELETED')
                  AND COALESCE(generation_id, '') <> ?
                  AND (
                        semantic_type = 'PROFILE'
                     OR semantic_key = ?
                     OR semantic_key = ?
                     OR CAST(value_json AS VARCHAR) LIKE ?
                     OR CAST(value_json AS VARCHAR) LIKE ?
                     OR CAST(value_json AS VARCHAR) LIKE ?
                     OR CAST(value_json AS VARCHAR) LIKE ?
                     OR CAST(value_json AS VARCHAR) LIKE ?
                     OR CAST(value_json AS VARCHAR) LIKE ?
                  )
                """,
                reason,
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now),
                userId,
                tenantId,
                Objects.requireNonNullElse(activeGenerationId, ""),
                profileSlot,
                legacyProfileSemanticKey(profileSlot),
                containsJsonValue("profileSlot", profileSlot),
                containsJsonValue("semanticKey", profileSlot),
                containsJsonValue("semanticKey", legacyProfileSemanticKey(profileSlot)),
                containsJsonValueWithSpace("profileSlot", profileSlot),
                containsJsonValueWithSpace("semanticKey", profileSlot),
                containsJsonValueWithSpace("semanticKey", legacyProfileSemanticKey(profileSlot)));
    }

    private String table(String layer) {
        return switch (Objects.requireNonNullElse(layer, "")
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")) {
            case "short_term" -> "t_short_term_memory";
            case "long_term" -> "t_long_term_memory";
            case "semantic" -> "t_semantic_memory";
            default -> "";
        };
    }

    private String legacyProfileSemanticKey(String profileSlot) {
        if ("identity.occupation".equals(profileSlot)) {
            return "profile:occupation";
        }
        return profileSlot;
    }

    private String containsJsonValue(String key, String value) {
        String safeKey = Objects.requireNonNullElse(key, "");
        String safeValue = Objects.requireNonNullElse(value, "");
        return "%\"" + safeKey + "\":\"" + safeValue + "\"%";
    }

    private String containsJsonValueWithSpace(String key, String value) {
        String safeKey = Objects.requireNonNullElse(key, "");
        String safeValue = Objects.requireNonNullElse(value, "");
        return "%\"" + safeKey + "\": \"" + safeValue + "\"%";
    }
}
