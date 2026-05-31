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
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 JDBC 的知识库管理仓储 adapter。
 */
public class JdbcKnowledgeBaseRepositoryAdapter implements KnowledgeBaseRepositoryPort {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SQL_INSERT = """
            INSERT INTO t_knowledge_base
            (id, name, embedding_model, collection_name, created_by, updated_by, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT kb.id, kb.name, kb.embedding_model, kb.collection_name, kb.created_by,
                   kb.create_time, kb.update_time, COUNT(doc.id) AS document_count
            FROM t_knowledge_base kb
            LEFT JOIN t_knowledge_document doc ON doc.kb_id = kb.id AND doc.deleted = 0
            WHERE kb.id = ? AND kb.deleted = 0
            GROUP BY kb.id, kb.name, kb.embedding_model, kb.collection_name,
                     kb.created_by, kb.create_time, kb.update_time
            """;
    private static final String SQL_COUNT_BY_NAME = """
            SELECT COUNT(1)
            FROM t_knowledge_base
            WHERE deleted = 0 AND REPLACE(name, ' ', '') = ? AND (CAST(? AS VARCHAR) IS NULL OR id <> ?)
            """;
    private static final String SQL_COUNT_PAGE_BASE = """
            SELECT COUNT(1)
            FROM t_knowledge_base
            WHERE deleted = 0
            """;
    private static final String SQL_PAGE_BASE = """
            SELECT kb.id, kb.name, kb.embedding_model, kb.collection_name, kb.created_by,
                   kb.create_time, kb.update_time, COUNT(doc.id) AS document_count
            FROM t_knowledge_base kb
            LEFT JOIN t_knowledge_document doc ON doc.kb_id = kb.id AND doc.deleted = 0
            WHERE kb.deleted = 0
            """;
    private static final String SQL_HAS_DOCUMENTS =
            "SELECT COUNT(1) FROM t_knowledge_document WHERE kb_id = ? AND deleted = 0";
    private static final String SQL_HAS_VECTORIZED_DOCUMENTS = """
            SELECT COUNT(1)
            FROM t_knowledge_document
            WHERE kb_id = ? AND deleted = 0 AND chunk_count > 0
            """;
    private static final String SQL_UPDATE = """
            UPDATE t_knowledge_base
            SET name = COALESCE(?, name),
                embedding_model = COALESCE(?, embedding_model),
                updated_by = ?,
                update_time = ?
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE = """
            UPDATE t_knowledge_base
            SET deleted = 1, updated_by = ?, update_time = ?
            WHERE id = ? AND deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeBaseRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public String create(CreateKnowledgeBaseValues values) {
        CreateKnowledgeBaseValues safeValues = Objects.requireNonNull(values, "values must not be null");
        String id = nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT,
                id,
                requireText(safeValues.name(), "name"),
                blankToNull(safeValues.embeddingModel()),
                requireText(safeValues.collectionName(), "collectionName"),
                Objects.requireNonNullElse(safeValues.operator(), ""),
                Objects.requireNonNullElse(safeValues.operator(), ""),
                now,
                now);
        return id;
    }

    @Override
    public boolean nameExists(String normalizedName, String excludedKbId) {
        Integer count = jdbcTemplate.queryForObject(SQL_COUNT_BY_NAME, Integer.class,
                requireText(normalizedName, "normalizedName"), blankToNull(excludedKbId), blankToNull(excludedKbId));
        return count != null && count > 0;
    }

    @Override
    public Optional<KnowledgeBaseRecord> findById(String kbId) {
        if (!hasText(kbId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::toRecord, kbId).stream().findFirst();
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
    public boolean hasDocuments(String kbId) {
        Integer count = jdbcTemplate.queryForObject(SQL_HAS_DOCUMENTS, Integer.class, requireText(kbId, "kbId"));
        return count != null && count > 0;
    }

    @Override
    public boolean hasVectorizedDocuments(String kbId) {
        Integer count = jdbcTemplate.queryForObject(SQL_HAS_VECTORIZED_DOCUMENTS,
                Integer.class, requireText(kbId, "kbId"));
        return count != null && count > 0;
    }

    @Override
    public boolean update(String kbId, KnowledgeBaseUpdateValues values) {
        KnowledgeBaseUpdateValues safeValues = Objects.requireNonNull(values, "values must not be null");
        int updated = jdbcTemplate.update(SQL_UPDATE,
                blankToNull(safeValues.name()),
                blankToNull(safeValues.embeddingModel()),
                Objects.requireNonNullElse(safeValues.operator(), ""),
                Timestamp.from(Instant.now()),
                requireText(kbId, "kbId"));
        return updated > 0;
    }

    @Override
    public boolean delete(String kbId, String operator) {
        int updated = jdbcTemplate.update(SQL_DELETE,
                Objects.requireNonNullElse(operator, ""),
                Timestamp.from(Instant.now()),
                requireText(kbId, "kbId"));
        return updated > 0;
    }

    private long queryTotal(String whereName, String name) {
        if (whereName.isEmpty()) {
            Long total = jdbcTemplate.queryForObject(SQL_COUNT_PAGE_BASE, Long.class);
            return total == null ? 0 : total;
        }
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_PAGE_BASE + whereName, Long.class, like(name));
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
            return jdbcTemplate.query(sql, this::toRecord, size, offset);
        }
        return jdbcTemplate.query(sql, this::toRecord, like(name), size, offset);
    }

    private KnowledgeBaseRecord toRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeBaseRecord record = new KnowledgeBaseRecord();
        record.setId(resultSet.getString("id"));
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

    private String nextId() {
        long millis = System.currentTimeMillis();
        int suffix = ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
        return Long.toString(millis, 36) + suffix;
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
}
