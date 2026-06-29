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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcTenantSupport;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeBaseValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseUpdateValues;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 JDBC 的知识库管理仓储 adapter。
 */
public class JdbcKnowledgeBaseRepositoryAdapter implements KnowledgeBaseRepositoryPort {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SQL_INSERT = """
            INSERT INTO t_knowledge_base
            (id, name, embedding_model, collection_name, created_by, updated_by, create_time, update_time, deleted, tenant_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT kb.id, kb.name, kb.embedding_model, kb.collection_name, kb.created_by,
                   kb.create_time, kb.update_time, COUNT(doc.id) AS document_count
            FROM t_knowledge_base kb
            LEFT JOIN t_knowledge_document doc ON doc.kb_id = kb.id AND doc.deleted = 0
            WHERE kb.id = ? AND kb.deleted = 0 AND kb.tenant_id = ?
            GROUP BY kb.id, kb.name, kb.embedding_model, kb.collection_name,
                     kb.created_by, kb.create_time, kb.update_time
            """;
    private static final String SQL_COUNT_BY_NAME = """
            SELECT COUNT(1)
            FROM t_knowledge_base
            WHERE deleted = 0 AND REPLACE(name, ' ', '') = ? AND (CAST(? AS VARCHAR) IS NULL OR id <> ?) AND tenant_id = ?
            """;
    private static final String SQL_COUNT_PAGE_BASE = """
            SELECT COUNT(1)
            FROM t_knowledge_base
            WHERE deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_PAGE_BASE = """
            SELECT kb.id, kb.name, kb.embedding_model, kb.collection_name, kb.created_by,
                   kb.create_time, kb.update_time, COUNT(doc.id) AS document_count
            FROM t_knowledge_base kb
            LEFT JOIN t_knowledge_document doc ON doc.kb_id = kb.id AND doc.deleted = 0
            WHERE kb.deleted = 0 AND kb.tenant_id = ?
            """;
    private static final String SQL_HAS_DOCUMENTS =
            "SELECT COUNT(1) FROM t_knowledge_document WHERE kb_id = ? AND deleted = 0 AND tenant_id = ?";
    private static final String SQL_HAS_VECTORIZED_DOCUMENTS = """
            SELECT COUNT(1)
            FROM t_knowledge_document
            WHERE kb_id = ? AND deleted = 0 AND chunk_count > 0 AND tenant_id = ?
            """;
    private static final String SQL_UPDATE = """
            UPDATE t_knowledge_base
            SET name = COALESCE(?, name),
                embedding_model = COALESCE(?, embedding_model),
                updated_by = ?,
                update_time = ?
            WHERE id = ? AND deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_DELETE = """
            UPDATE t_knowledge_base
            SET deleted = 1, updated_by = ?, update_time = ?
            WHERE id = ? AND deleted = 0 AND tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeBaseRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long create(CreateKnowledgeBaseValues values) {
        CreateKnowledgeBaseValues safeValues = Objects.requireNonNull(values, "values must not be null");
        Long id = nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT,
                id,
                requireText(safeValues.name(), "name"),
                blankToNull(safeValues.embeddingModel()),
                requireText(safeValues.collectionName(), "collectionName"),
                parseOperatorId(safeValues.operator()),
                parseOperatorId(safeValues.operator()),
                now,
                now,
                JdbcTenantSupport.resolveTenantId());
        return id;
    }

    @Override
    public boolean nameExists(String normalizedName, Long excludedKbId) {
        Integer count = jdbcTemplate.queryForObject(SQL_COUNT_BY_NAME, Integer.class,
                requireText(normalizedName, "normalizedName"), excludedKbId, excludedKbId,
                JdbcTenantSupport.resolveTenantId());
        return count != null && count > 0;
    }

    @Override
    public Optional<KnowledgeBaseRecord> findById(Long kbId) {
        if (kbId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::toRecord, kbId,
                JdbcTenantSupport.resolveTenantId()).stream().findFirst();
    }

    @Override
    public KnowledgeBasePage page(long current, long size, String name) {
        long safeCurrent = current <= 0 ? 1 : current;
        long safeSize = clampSize(size);
        String whereName = hasText(name) ? " AND name LIKE ?" : "";
        long total = queryTotal(whereName, name);
        List<KnowledgeBaseRecord> records = queryRecords(safeCurrent, safeSize, whereName, name);
        long pages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;
        return new KnowledgeBasePage(records, total, safeSize, safeCurrent, pages);
    }

    @Override
    public boolean hasDocuments(Long kbId) {
        if (kbId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(SQL_HAS_DOCUMENTS, Integer.class, kbId,
                JdbcTenantSupport.resolveTenantId());
        return count != null && count > 0;
    }

    @Override
    public List<Long> listDocumentIds(Long kbId) {
        if (kbId == null) {
            return List.of();
        }
        String sql = "SELECT id FROM t_knowledge_document WHERE kb_id = ? AND deleted = 0 AND tenant_id = ?";
        return jdbcTemplate.queryForList(sql, Long.class, kbId, JdbcTenantSupport.resolveTenantId());
    }

    @Override
    public boolean hasVectorizedDocuments(Long kbId) {
        if (kbId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(SQL_HAS_VECTORIZED_DOCUMENTS,
                Integer.class, kbId, JdbcTenantSupport.resolveTenantId());
        return count != null && count > 0;
    }

    @Override
    public boolean update(Long kbId, KnowledgeBaseUpdateValues values) {
        if (kbId == null) {
            return false;
        }
        KnowledgeBaseUpdateValues safeValues = Objects.requireNonNull(values, "values must not be null");
        int updated = jdbcTemplate.update(SQL_UPDATE,
                blankToNull(safeValues.name()),
                blankToNull(safeValues.embeddingModel()),
                parseOperatorId(safeValues.operator()),
                Timestamp.from(Instant.now()),
                kbId,
                JdbcTenantSupport.resolveTenantId());
        return updated > 0;
    }

    @Override
    public boolean delete(Long kbId, String operator) {
        if (kbId == null) {
            return false;
        }
        int updated = jdbcTemplate.update(SQL_DELETE,
                parseOperatorId(operator),
                Timestamp.from(Instant.now()),
                kbId,
                JdbcTenantSupport.resolveTenantId());
        return updated > 0;
    }

    private long queryTotal(String whereName, String name) {
        if (whereName.isEmpty()) {
            Long total = jdbcTemplate.queryForObject(SQL_COUNT_PAGE_BASE, Long.class,
                    JdbcTenantSupport.resolveTenantId());
            return total == null ? 0 : total;
        }
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_PAGE_BASE + whereName, Long.class,
                JdbcTenantSupport.resolveTenantId(), like(name));
        return total == null ? 0 : total;
    }

    private List<KnowledgeBaseRecord> queryRecords(long current, long size, String whereName, String name) {
        String sql = SQL_PAGE_BASE + whereName + """

                GROUP BY kb.id, kb.name, kb.embedding_model, kb.collection_name,
                         kb.created_by, kb.create_time, kb.update_time
                ORDER BY kb.update_time DESC
                LIMIT ? OFFSET ?
                """;
        long offset = (current - 1) * size;
        if (whereName.isEmpty()) {
            return jdbcTemplate.query(sql, this::toRecord, JdbcTenantSupport.resolveTenantId(), size, offset);
        }
        return jdbcTemplate.query(sql, this::toRecord, JdbcTenantSupport.resolveTenantId(), like(name), size, offset);
    }

    private KnowledgeBaseRecord toRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeBaseRecord record = new KnowledgeBaseRecord();
        record.setId(resultSet.getLong("id"));
        record.setName(resultSet.getString("name"));
        record.setEmbeddingModel(resultSet.getString("embedding_model"));
        record.setCollectionName(resultSet.getString("collection_name"));
        record.setDocumentCount(resultSet.getLong("document_count"));
        record.setCreatedBy(resultSet.getString("created_by"));
        record.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        record.setUpdateTime(toInstant(resultSet.getTimestamp("update_time")));
        return record;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Long nextId() {
        return SnowflakeIds.nextId();
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private long clampSize(long size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 将 operator 字符串安全地转换为 Long 类型。
     * 数据库 created_by / updated_by 列为 BIGINT，需要数值型用户 ID。
     * 非数值或空值返回 null（用于可空列）或 0（用于 NOT NULL 列由调用方兜底）。
     */
    private Long parseOperatorId(String operator) {
        if (operator == null || operator.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(operator.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
