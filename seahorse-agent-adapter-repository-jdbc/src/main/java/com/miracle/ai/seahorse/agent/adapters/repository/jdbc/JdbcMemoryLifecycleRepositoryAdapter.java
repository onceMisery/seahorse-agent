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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionFragment;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class JdbcMemoryLifecycleRepositoryAdapter
        implements MemoryLifecyclePort, MemoryGarbageCollectionPort, MemoryCompactionPort {

    private static final String DEFAULT_TENANT_ID = "default";
    private static final int DEFAULT_SCAN_LIMIT = 100;
    private static final int DEFAULT_COMPACTION_MIN_GROUP_SIZE = 3;
    private static final double READ_REINFORCEMENT_BOOST = 0.30D;
    private static final double MAX_MEMORY_SCORE = 1.0D;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryLifecycleRepositoryAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcMemoryLifecycleRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
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
        Instant safeReferencedAt = Objects.requireNonNullElseGet(referencedAt, Instant::now);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE %s
                SET last_referenced_at = ?,
                    status = CASE WHEN status = 'ACTIVE' THEN 'REFERENCED' ELSE status END,
                    update_time = ?
                WHERE id = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                """.formatted(table),
                JdbcMemorySupport.timestamp(safeReferencedAt),
                JdbcMemorySupport.timestamp(now),
                memoryId);
        if ("t_short_term_memory".equals(table)) {
            jdbcTemplate.update("""
                    UPDATE t_short_term_memory
                    SET access_count = COALESCE(access_count, 0) + 1,
                        last_access_time = ?
                    WHERE id = ?
                      AND deleted = 0
                      AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                    """,
                    JdbcMemorySupport.timestamp(safeReferencedAt),
                    memoryId);
        } else if ("t_long_term_memory".equals(table)) {
            jdbcTemplate.update("""
                    UPDATE t_long_term_memory
                    SET confidence_level = LEAST(?, COALESCE(confidence_level, 0) + ?),
                        importance_score = LEAST(?, COALESCE(importance_score, 0) + ?),
                        update_time = ?
                    WHERE id = ?
                      AND deleted = 0
                      AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                    """,
                    MAX_MEMORY_SCORE,
                    READ_REINFORCEMENT_BOOST,
                    MAX_MEMORY_SCORE,
                    READ_REINFORCEMENT_BOOST,
                    JdbcMemorySupport.timestamp(now),
                    memoryId);
        } else if ("t_semantic_memory".equals(table)) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET confidence_level = LEAST(?, COALESCE(confidence_level, 0) + ?),
                        update_time = ?
                    WHERE id = ?
                      AND deleted = 0
                      AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                    """.formatted(table),
                    MAX_MEMORY_SCORE,
                    READ_REINFORCEMENT_BOOST,
                    JdbcMemorySupport.timestamp(now),
                    memoryId);
        }
    }

    @Override
    public List<MemoryGarbageCollectionCandidate> scanDerivedIndexDeleteCandidates(
            Instant now,
            Duration retention,
            int limit) {
        Instant cutoff = Objects.requireNonNullElseGet(now, Instant::now)
                .minus(Objects.requireNonNullElse(retention, Duration.ZERO));
        int safeLimit = limit <= 0 ? DEFAULT_SCAN_LIMIT : limit;
        List<MemoryGarbageCollectionCandidate> candidates = new ArrayList<>();
        candidates.addAll(scanLayerForDerivedIndexDeletes(
                "t_short_term_memory",
                "short_term",
                "id",
                "user_id",
                safeLimit,
                cutoff));
        if (candidates.size() >= safeLimit) {
            return candidates.stream().limit(safeLimit).toList();
        }
        candidates.addAll(scanLayerForDerivedIndexDeletes(
                "t_long_term_memory",
                "long_term",
                "id",
                "user_id",
                safeLimit - candidates.size(),
                cutoff));
        if (candidates.size() >= safeLimit) {
            return candidates.stream().limit(safeLimit).toList();
        }
        candidates.addAll(scanLayerForDerivedIndexDeletes(
                "t_semantic_memory",
                "semantic",
                "id",
                "user_id",
                safeLimit - candidates.size(),
                cutoff));
        return candidates.stream().limit(safeLimit).toList();
    }

    @Override
    public int markDerivedIndexesDeleted(List<String> memoryIds, Instant markedAt) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return 0;
        }
        Instant now = Objects.requireNonNullElseGet(markedAt, Instant::now);
        int updated = 0;
        for (String memoryId : memoryIds) {
            if (!JdbcMemorySupport.hasText(memoryId)) {
                continue;
            }
            updated += markLayerDerivedIndexesDeleted("t_short_term_memory", memoryId, now);
            updated += markLayerDerivedIndexesDeleted("t_long_term_memory", memoryId, now);
            updated += markLayerDerivedIndexesDeleted("t_semantic_memory", memoryId, now);
        }
        return updated;
    }

    @Override
    public List<MemoryGarbageCollectionCandidate> scanLifecycleArchiveCandidates(
            Instant now,
            Duration idleRetention,
            double scoreThreshold,
            int limit) {
        Instant cutoff = Objects.requireNonNullElseGet(now, Instant::now)
                .minus(Objects.requireNonNullElse(idleRetention, Duration.ZERO));
        int safeLimit = limit <= 0 ? DEFAULT_SCAN_LIMIT : limit;
        double safeThreshold = scoreThreshold <= 0D ? 0.15D : Math.min(1D, scoreThreshold);
        List<MemoryGarbageCollectionCandidate> candidates = new ArrayList<>();
        candidates.addAll(scanLongTermArchiveCandidates(safeLimit, cutoff, safeThreshold));
        if (candidates.size() >= safeLimit) {
            return candidates.stream().limit(safeLimit).toList();
        }
        candidates.addAll(scanSemanticArchiveCandidates(safeLimit - candidates.size(), cutoff, safeThreshold));
        return candidates.stream().limit(safeLimit).toList();
    }

    @Override
    public int markArchived(List<String> memoryIds, Instant archivedAt, String reason) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return 0;
        }
        Instant now = Objects.requireNonNullElseGet(archivedAt, Instant::now);
        String safeReason = JdbcMemorySupport.hasText(reason) ? reason : "generational gc archive";
        int updated = 0;
        for (String memoryId : memoryIds) {
            if (!JdbcMemorySupport.hasText(memoryId)) {
                continue;
            }
            updated += markLayerArchived("t_long_term_memory", memoryId, now, safeReason);
            updated += markLayerArchived("t_semantic_memory", memoryId, now, safeReason);
        }
        return updated;
    }

    @Override
    public List<MemoryGarbageCollectionCandidate> scanPhysicalDeleteCandidates(
            Instant now,
            Duration retention,
            int limit) {
        Instant cutoff = Objects.requireNonNullElseGet(now, Instant::now)
                .minus(Objects.requireNonNullElse(retention, Duration.ZERO));
        int safeLimit = limit <= 0 ? DEFAULT_SCAN_LIMIT : limit;
        List<MemoryGarbageCollectionCandidate> candidates = new ArrayList<>();
        candidates.addAll(scanLayerForPhysicalDeletes(
                "t_short_term_memory",
                "short_term",
                "id",
                "user_id",
                safeLimit,
                cutoff));
        if (candidates.size() >= safeLimit) {
            return candidates.stream().limit(safeLimit).toList();
        }
        candidates.addAll(scanLayerForPhysicalDeletes(
                "t_long_term_memory",
                "long_term",
                "id",
                "user_id",
                safeLimit - candidates.size(),
                cutoff));
        if (candidates.size() >= safeLimit) {
            return candidates.stream().limit(safeLimit).toList();
        }
        candidates.addAll(scanLayerForPhysicalDeletes(
                "t_semantic_memory",
                "semantic",
                "id",
                "user_id",
                safeLimit - candidates.size(),
                cutoff));
        return candidates.stream().limit(safeLimit).toList();
    }

    @Override
    public int markPhysicallyDeleted(List<String> memoryIds, Instant deletedAt) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return 0;
        }
        Instant now = Objects.requireNonNullElseGet(deletedAt, Instant::now);
        int updated = 0;
        for (String memoryId : memoryIds) {
            if (!JdbcMemorySupport.hasText(memoryId)) {
                continue;
            }
            updated += markLayerPhysicallyDeleted("t_short_term_memory", memoryId, now);
            updated += markLayerPhysicallyDeleted("t_long_term_memory", memoryId, now);
            updated += markLayerPhysicallyDeleted("t_semantic_memory", memoryId, now);
        }
        return updated;
    }

    @Override
    public List<MemoryCompactionCandidate> scanCandidates(int limit) {
        return scanCompactionCandidates(limit, DEFAULT_COMPACTION_MIN_GROUP_SIZE);
    }

    @Override
    public List<MemoryCompactionCandidate> scanCandidates(int limit, int minGroupSize) {
        return scanCompactionCandidates(limit, minGroupSize);
    }

    public List<MemoryCompactionCandidate> scanCompactionCandidates(int limit, int minGroupSize) {
        int safeLimit = limit <= 0 ? DEFAULT_SCAN_LIMIT : limit;
        int safeMinGroupSize = minGroupSize <= 1 ? DEFAULT_COMPACTION_MIN_GROUP_SIZE : minGroupSize;
        List<MemoryCompactionFragmentRow> rows = new ArrayList<>();
        rows.addAll(scanShortTermCompactionRows(safeLimit));
        rows.addAll(scanLongTermCompactionRows(safeLimit));
        rows.addAll(scanSemanticCompactionRows(safeLimit));

        Map<String, List<MemoryCompactionFragmentRow>> groups = new LinkedHashMap<>();
        for (MemoryCompactionFragmentRow row : rows) {
            if (!JdbcMemorySupport.hasText(row.groupKey())) {
                continue;
            }
            groups.computeIfAbsent(row.userId() + "\u0000" + row.tenantId() + "\u0000" + row.groupKey(),
                    ignored -> new ArrayList<>()).add(row);
        }
        return groups.values().stream()
                .filter(group -> group.size() >= safeMinGroupSize)
                .limit(safeLimit)
                .map(this::candidate)
                .toList();
    }

    @Override
    public int markCompacted(MemoryCompactionCandidate candidate, String masterMemoryId, Instant compactedAt) {
        if (candidate == null || candidate.fragments().isEmpty()) {
            return 0;
        }
        String safeReason = "compacted into " + Objects.requireNonNullElse(masterMemoryId, "");
        Instant now = Objects.requireNonNullElseGet(compactedAt, Instant::now);
        int updated = 0;
        for (MemoryCompactionFragment fragment : candidate.fragments()) {
            updated += markFragmentCompacted(fragment, candidate.userId(), candidate.tenantId(), safeReason, now);
        }
        return updated;
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
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
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
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
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
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
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

    private List<MemoryGarbageCollectionCandidate> scanLongTermArchiveCandidates(
            int limit,
            Instant cutoff,
            double scoreThreshold) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id AS memory_id,
                       user_id,
                       COALESCE(tenant_id, 'default') AS tenant_id,
                       COALESCE(status, '') AS status,
                       update_time
                FROM t_long_term_memory
                WHERE deleted = 0
                  AND COALESCE(status, 'ACTIVE') IN ('ACTIVE', 'REFERENCED')
                  AND COALESCE(last_referenced_at, update_time, create_time) <= ?
                  AND ((COALESCE(importance_score, 0) + COALESCE(confidence_level, 0)) / 2.0) <= ?
                ORDER BY COALESCE(last_referenced_at, update_time, create_time) ASC,
                         importance_score ASC,
                         confidence_level ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new MemoryGarbageCollectionCandidate(
                        rs.getString("memory_id"),
                        rs.getString("user_id"),
                        Objects.requireNonNullElse(rs.getString("tenant_id"), DEFAULT_TENANT_ID),
                        "long_term",
                        rs.getString("status"),
                        JdbcMemorySupport.instant(rs.getTimestamp("update_time"))),
                JdbcMemorySupport.timestamp(cutoff),
                scoreThreshold,
                limit);
    }

    private List<MemoryGarbageCollectionCandidate> scanSemanticArchiveCandidates(
            int limit,
            Instant cutoff,
            double scoreThreshold) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id AS memory_id,
                       user_id,
                       COALESCE(tenant_id, 'default') AS tenant_id,
                       COALESCE(status, '') AS status,
                       update_time
                FROM t_semantic_memory
                WHERE deleted = 0
                  AND COALESCE(status, 'ACTIVE') IN ('ACTIVE', 'REFERENCED')
                  AND COALESCE(last_referenced_at, update_time, create_time) <= ?
                  AND COALESCE(confidence_level, 0) <= ?
                ORDER BY COALESCE(last_referenced_at, update_time, create_time) ASC,
                         confidence_level ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new MemoryGarbageCollectionCandidate(
                        rs.getString("memory_id"),
                        rs.getString("user_id"),
                        Objects.requireNonNullElse(rs.getString("tenant_id"), DEFAULT_TENANT_ID),
                        "semantic",
                        rs.getString("status"),
                        JdbcMemorySupport.instant(rs.getTimestamp("update_time"))),
                JdbcMemorySupport.timestamp(cutoff),
                scoreThreshold,
                limit);
    }

    private int markLayerArchived(String tableName, String memoryId, Instant archivedAt, String reason) {
        return jdbcTemplate.update("""
                UPDATE %s
                SET status = 'ARCHIVED',
                    obsolete_reason = ?,
                    valid_until = ?,
                    update_time = ?
                WHERE id = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') IN ('ACTIVE', 'REFERENCED')
                """.formatted(tableName),
                reason,
                JdbcMemorySupport.timestamp(archivedAt),
                JdbcMemorySupport.timestamp(archivedAt),
                memoryId);
    }

    private List<MemoryGarbageCollectionCandidate> scanLayerForPhysicalDeletes(
            String tableName,
            String layer,
            String idColumn,
            String userIdColumn,
            int limit,
            Instant cutoff) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT %s AS memory_id,
                       %s AS user_id,
                       COALESCE(tenant_id, 'default') AS tenant_id,
                       COALESCE(status, '') AS status,
                       update_time
                FROM %s
                WHERE deleted = 0
                  AND COALESCE(status, 'ACTIVE') IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED', 'DELETED')
                  AND derived_indexes_deleted_at IS NOT NULL
                  AND update_time <= ?
                ORDER BY update_time ASC
                LIMIT ?
                """.formatted(idColumn, userIdColumn, tableName),
                (rs, rowNum) -> new MemoryGarbageCollectionCandidate(
                        rs.getString("memory_id"),
                        rs.getString("user_id"),
                        Objects.requireNonNullElse(rs.getString("tenant_id"), DEFAULT_TENANT_ID),
                        layer,
                        rs.getString("status"),
                        JdbcMemorySupport.instant(rs.getTimestamp("update_time"))),
                JdbcMemorySupport.timestamp(cutoff),
                limit);
    }

    private int markLayerPhysicallyDeleted(String tableName, String memoryId, Instant deletedAt) {
        return jdbcTemplate.update("""
                UPDATE %s
                SET status = 'PHYSICAL_DELETED',
                    deleted = 1,
                    update_time = ?
                WHERE id = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED', 'DELETED')
                  AND derived_indexes_deleted_at IS NOT NULL
                """.formatted(tableName),
                JdbcMemorySupport.timestamp(deletedAt),
                memoryId);
    }

    private List<MemoryGarbageCollectionCandidate> scanLayerForDerivedIndexDeletes(
            String tableName,
            String layer,
            String idColumn,
            String userIdColumn,
            int limit,
            Instant cutoff) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT %s AS memory_id,
                       %s AS user_id,
                       COALESCE(tenant_id, 'default') AS tenant_id,
                       COALESCE(status, '') AS status,
                       update_time
                FROM %s
                WHERE deleted = 0
                  AND COALESCE(status, 'ACTIVE') IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED')
                  AND derived_indexes_deleted_at IS NULL
                  AND update_time <= ?
                ORDER BY update_time ASC
                LIMIT ?
                """.formatted(idColumn, userIdColumn, tableName),
                (rs, rowNum) -> new MemoryGarbageCollectionCandidate(
                        rs.getString("memory_id"),
                        rs.getString("user_id"),
                        Objects.requireNonNullElse(rs.getString("tenant_id"), DEFAULT_TENANT_ID),
                        layer,
                        rs.getString("status"),
                        JdbcMemorySupport.instant(rs.getTimestamp("update_time"))),
                JdbcMemorySupport.timestamp(cutoff),
                limit);
    }

    private int markLayerDerivedIndexesDeleted(String tableName, String memoryId, Instant markedAt) {
        return jdbcTemplate.update("""
                UPDATE %s
                SET derived_indexes_deleted_at = ?,
                    update_time = ?
                WHERE id = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED')
                  AND derived_indexes_deleted_at IS NULL
                """.formatted(tableName),
                JdbcMemorySupport.timestamp(markedAt),
                JdbcMemorySupport.timestamp(Instant.now()),
                memoryId);
    }

    private List<MemoryCompactionFragmentRow> scanShortTermCompactionRows(int limit) {
        return jdbcTemplate.query("""
                SELECT id AS memory_id,
                       'short_term' AS layer_name,
                       user_id,
                       COALESCE(tenant_id, 'default') AS tenant_id,
                       memory_type AS memory_type,
                       content,
                       metadata_json AS metadata_text,
                       update_time
                FROM t_short_term_memory
                WHERE deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                  AND metadata_json IS NOT NULL
                ORDER BY update_time ASC
                LIMIT ?
                """, this::mapCompactionRow, limit);
    }

    private List<MemoryCompactionFragmentRow> scanLongTermCompactionRows(int limit) {
        return jdbcTemplate.query("""
                SELECT id AS memory_id,
                       'long_term' AS layer_name,
                       user_id,
                       COALESCE(tenant_id, 'default') AS tenant_id,
                       memory_category AS memory_type,
                       content,
                       tags AS metadata_text,
                       update_time
                FROM t_long_term_memory
                WHERE deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                  AND tags IS NOT NULL
                ORDER BY update_time ASC
                LIMIT ?
                """, this::mapCompactionRow, limit);
    }

    private List<MemoryCompactionFragmentRow> scanSemanticCompactionRows(int limit) {
        return jdbcTemplate.query("""
                SELECT id AS memory_id,
                       'semantic' AS layer_name,
                       user_id,
                       COALESCE(tenant_id, 'default') AS tenant_id,
                       semantic_type AS memory_type,
                       CAST(value_json AS VARCHAR) AS content,
                       CAST(value_json AS VARCHAR) AS metadata_text,
                       update_time,
                       semantic_key
                FROM t_semantic_memory
                WHERE deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                  AND semantic_key IS NOT NULL
                ORDER BY update_time ASC
                LIMIT ?
                """, (rs, rowNum) -> {
                    MemoryCompactionFragmentRow row = mapCompactionRow(rs, rowNum);
                    String semanticKey = Objects.requireNonNullElse(rs.getString("semantic_key"), "").trim();
                    return row.withGroupKey(semanticKey.isBlank() ? row.groupKey() : "semanticKey:" + semanticKey);
                }, limit);
    }

    private MemoryCompactionFragmentRow mapCompactionRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String metadata = Objects.requireNonNullElse(rs.getString("metadata_text"), "");
        String groupKey = groupKey(metadata);
        return new MemoryCompactionFragmentRow(
                rs.getString("memory_id"),
                rs.getString("layer_name"),
                rs.getString("user_id"),
                Objects.requireNonNullElse(rs.getString("tenant_id"), DEFAULT_TENANT_ID),
                Objects.requireNonNullElse(rs.getString("memory_type"), ""),
                Objects.requireNonNullElse(rs.getString("content"), ""),
                metadata,
                groupKey,
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
    }

    private MemoryCompactionCandidate candidate(List<MemoryCompactionFragmentRow> group) {
        MemoryCompactionFragmentRow first = group.get(0);
        return new MemoryCompactionCandidate(
                first.userId(),
                first.tenantId(),
                first.groupKey(),
                strategy(first.groupKey()),
                group.stream()
                        .map(row -> new MemoryCompactionFragment(
                                row.memoryId(),
                                row.layer(),
                                row.type(),
                                row.content(),
                                Map.of("compactionGroupKey", row.groupKey()),
                                row.updatedAt()))
                        .toList());
    }

    private int markFragmentCompacted(MemoryCompactionFragment fragment,
                                      String userId,
                                      String tenantId,
                                      String reason,
                                      Instant now) {
        String table = table(fragment.layer());
        if (table.isBlank() || !JdbcMemorySupport.hasText(fragment.memoryId())) {
            return 0;
        }
        return jdbcTemplate.update("""
                UPDATE %s
                SET status = 'COMPACTED',
                    obsolete_reason = ?,
                    valid_until = ?,
                    update_time = ?
                WHERE id = ?
                  AND user_id = ?
                  AND COALESCE(tenant_id, 'default') = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                """.formatted(table),
                reason,
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now),
                fragment.memoryId(),
                userId,
                JdbcMemorySupport.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID);
    }

    private String groupKey(String metadata) {
        Map<String, Object> values = JdbcMemorySupport.parseJson(objectMapper, metadata);
        String semanticKey = mapValue(values, "semanticKey");
        if (JdbcMemorySupport.hasText(semanticKey)) {
            return "semanticKey:" + semanticKey;
        }
        String profileSlot = mapValue(values, "profileSlot");
        if (JdbcMemorySupport.hasText(profileSlot)) {
            return "profileSlot:" + profileSlot;
        }
        String canonicalEntityId = mapValue(values, "canonicalEntityId");
        if (JdbcMemorySupport.hasText(canonicalEntityId)) {
            return "canonicalEntityId:" + canonicalEntityId;
        }
        return "";
    }

    private String strategy(String groupKey) {
        int delimiter = groupKey.indexOf(':');
        return delimiter <= 0 ? "rule" : groupKey.substring(0, delimiter);
    }

    private String mapValue(Map<String, Object> values, String key) {
        Object value = Objects.requireNonNullElse(values, Map.<String, Object>of()).get(key);
        return value == null ? "" : value.toString().trim();
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

    private record MemoryCompactionFragmentRow(
            String memoryId,
            String layer,
            String userId,
            String tenantId,
            String type,
            String content,
            String metadata,
            String groupKey,
            Instant updatedAt) {

        MemoryCompactionFragmentRow {
            memoryId = Objects.requireNonNullElse(memoryId, "");
            layer = Objects.requireNonNullElse(layer, "");
            userId = Objects.requireNonNullElse(userId, "");
            tenantId = Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID);
            type = Objects.requireNonNullElse(type, "");
            content = Objects.requireNonNullElse(content, "");
            metadata = Objects.requireNonNullElse(metadata, "");
            groupKey = Objects.requireNonNullElse(groupKey, "");
            updatedAt = Objects.requireNonNullElse(updatedAt, Instant.EPOCH);
        }

        MemoryCompactionFragmentRow withGroupKey(String newGroupKey) {
            return new MemoryCompactionFragmentRow(
                    memoryId,
                    layer,
                    userId,
                    tenantId,
                    type,
                    content,
                    metadata,
                    Objects.requireNonNullElse(newGroupKey, ""),
                    updatedAt);
        }
    }
}
