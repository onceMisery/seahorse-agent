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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeChunkValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkContext;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.UpdateKnowledgeChunkValues;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 JDBC 的知识库 Chunk 写入适配器。
 */
public class JdbcKnowledgeChunkRepositoryAdapter implements KnowledgeChunkRepositoryPort {

    private static final int ENABLED_VALUE = 1;
    private static final int DEFAULT_DELETED_VALUE = 0;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SQL_DELETE_BY_DOC_ID = "DELETE FROM t_knowledge_chunk WHERE doc_id = ?";
    private static final String SQL_INSERT_CHUNK = """
            INSERT INTO t_knowledge_chunk
            (id, kb_id, doc_id, chunk_index, content, content_hash, char_count, enabled, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_INSERT_CHUNK_WITH_METADATA = """
            INSERT INTO t_knowledge_chunk
            (id, kb_id, doc_id, chunk_index, content, content_hash, char_count, metadata_json, enabled, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_DOCUMENT_CONTEXT = """
            SELECT doc.id AS doc_id, doc.kb_id, doc.status, doc.enabled,
                   kb.collection_name, kb.embedding_model
            FROM t_knowledge_document doc
            LEFT JOIN t_knowledge_base kb ON kb.id = doc.kb_id AND kb.deleted = 0
            WHERE doc.id = ? AND doc.deleted = 0
            """;
    private static final String SQL_FIND_CHUNK = """
            SELECT id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
                   token_count, enabled, created_by, updated_by, create_time, update_time
            FROM t_knowledge_chunk
            WHERE id = ? AND doc_id = ? AND deleted = 0
            """;
    private static final String SQL_COUNT_CHUNKS = """
            SELECT COUNT(1)
            FROM t_knowledge_chunk
            WHERE doc_id = ? AND deleted = 0
            """;
    private static final String SQL_PAGE_CHUNKS_BASE = """
            SELECT id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
                   token_count, enabled, created_by, updated_by, create_time, update_time
            FROM t_knowledge_chunk
            WHERE doc_id = ? AND deleted = 0
            """;
    private static final String SQL_MAX_CHUNK_INDEX = """
            SELECT MAX(chunk_index)
            FROM t_knowledge_chunk
            WHERE doc_id = ? AND deleted = 0
            """;
    private static final String SQL_INSERT_MANAGED_CHUNK = """
            INSERT INTO t_knowledge_chunk
            (id, kb_id, doc_id, chunk_index, content, content_hash, char_count, token_count,
             enabled, created_by, updated_by, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SQL_UPDATE_CHUNK = """
            UPDATE t_knowledge_chunk
            SET content = ?, content_hash = ?, char_count = ?, token_count = ?,
                updated_by = ?, update_time = ?
            WHERE id = ? AND doc_id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_CHUNK = """
            UPDATE t_knowledge_chunk
            SET deleted = 1, update_time = ?
            WHERE id = ? AND doc_id = ? AND deleted = 0
            """;
    private static final String SQL_FIND_CHUNKS_BY_IDS = """
            SELECT id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
                   token_count, enabled, created_by, updated_by, create_time, update_time
            FROM t_knowledge_chunk
            WHERE doc_id = ? AND deleted = 0 AND id IN (%s)
            ORDER BY chunk_index ASC
            """;
    private static final String SQL_UPDATE_ENABLED_BY_IDS = """
            UPDATE t_knowledge_chunk
            SET enabled = ?, updated_by = ?, update_time = ?
            WHERE doc_id = ? AND deleted = 0 AND id IN (%s)
            """;
    private static final String SQL_UPDATE_DOCUMENT_COUNT =
            "UPDATE t_knowledge_document SET chunk_count = ? WHERE id = ?";
    private static final String SQL_INCREMENT_DOCUMENT_COUNT =
            "UPDATE t_knowledge_document SET chunk_count = COALESCE(chunk_count, 0) + 1 WHERE id = ?";
    private static final String SQL_DECREMENT_DOCUMENT_COUNT = """
            UPDATE t_knowledge_document
            SET chunk_count = CASE WHEN COALESCE(chunk_count, 0) > 0 THEN chunk_count - 1 ELSE 0 END
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Boolean metadataJsonColumnExists;

    public JdbcKnowledgeChunkRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void replaceDocumentChunks(Long kbId, Long docId, List<VectorChunk> chunks) {
        Long safeKbId = requireId(kbId, "kbId");
        Long safeDocId = requireId(docId, "docId");
        List<VectorChunk> safeChunks = Objects.requireNonNullElse(chunks, List.of());
        jdbcTemplate.update(SQL_DELETE_BY_DOC_ID, safeDocId);
        if (!safeChunks.isEmpty()) {
            boolean writeMetadata = metadataJsonColumnExists();
            jdbcTemplate.batchUpdate(writeMetadata ? SQL_INSERT_CHUNK_WITH_METADATA : SQL_INSERT_CHUNK,
                    safeChunks, safeChunks.size(),
                    (statement, chunk) -> bindChunk(statement, safeKbId, safeDocId, chunk, writeMetadata));
        }
        jdbcTemplate.update(SQL_UPDATE_DOCUMENT_COUNT, safeChunks.size(), safeDocId);
    }

    @Override
    public Optional<KnowledgeDocumentChunkContext> findDocumentContext(Long docId) {
        if (docId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_DOCUMENT_CONTEXT, this::toDocumentContext, docId)
                .stream()
                .findFirst();
    }

    @Override
    public KnowledgeChunkPage page(Long docId, long current, long size, Boolean enabled) {
        Long safeDocId = requireId(docId, "docId");
        long safeCurrent = current <= 0 ? 1 : current;
        long safeSize = clampSize(size);
        String enabledWhere = enabled == null ? "" : " AND enabled = ?";
        long total = queryChunkTotal(safeDocId, enabledWhere, enabled);
        List<KnowledgeChunkRecord> records = queryChunkPage(safeDocId, safeCurrent, safeSize, enabledWhere, enabled);
        long pages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;
        return new KnowledgeChunkPage(records, total, safeSize, safeCurrent, pages);
    }

    @Override
    public KnowledgeChunkRecord create(Long docId, CreateKnowledgeChunkValues values) {
        Long safeDocId = requireId(docId, "docId");
        CreateKnowledgeChunkValues safeValues = Objects.requireNonNull(values, "values must not be null");
        KnowledgeDocumentChunkContext context = findDocumentContext(safeDocId)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + safeDocId));
        String content = requireText(safeValues.content(), "content");
        Long chunkId = safeValues.chunkId() != null ? safeValues.chunkId() : nextId();
        int chunkIndex = safeValues.index() == null ? nextChunkIndex(safeDocId) : safeValues.index();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT_MANAGED_CHUNK,
                chunkId,
                context.kbId(),
                safeDocId,
                chunkIndex,
                content,
                sha256(content),
                content.length(),
                content.length(),
                ENABLED_VALUE,
                safeValues.operator(),
                safeValues.operator(),
                now,
                now);
        jdbcTemplate.update(SQL_INCREMENT_DOCUMENT_COUNT, safeDocId);
        return findChunk(safeDocId, chunkId)
                .orElseThrow(() -> new IllegalStateException("chunk created but invisible: " + chunkId));
    }

    @Override
    public Optional<KnowledgeChunkRecord> findChunk(Long docId, Long chunkId) {
        if (docId == null || chunkId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_CHUNK, this::toChunkRecord, chunkId, docId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean update(Long docId, Long chunkId, UpdateKnowledgeChunkValues values) {
        UpdateKnowledgeChunkValues safeValues = Objects.requireNonNull(values, "values must not be null");
        String content = requireText(safeValues.content(), "content");
        int updated = jdbcTemplate.update(SQL_UPDATE_CHUNK,
                content,
                sha256(content),
                content.length(),
                content.length(),
                Objects.requireNonNullElse(safeValues.operator(), ""),
                Timestamp.from(Instant.now()),
                requireId(chunkId, "chunkId"),
                requireId(docId, "docId"));
        return updated > 0;
    }

    @Override
    public boolean delete(Long docId, Long chunkId) {
        int updated = jdbcTemplate.update(SQL_DELETE_CHUNK,
                Timestamp.from(Instant.now()),
                requireId(chunkId, "chunkId"),
                requireId(docId, "docId"));
        if (updated > 0) {
            jdbcTemplate.update(SQL_DECREMENT_DOCUMENT_COUNT, docId);
        }
        return updated > 0;
    }

    @Override
    public List<KnowledgeChunkRecord> findChunksByIds(Long docId, List<Long> chunkIds) {
        if (docId == null || chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(chunkIds.size());
        Object[] args = new Object[chunkIds.size() + 1];
        args[0] = docId;
        for (int index = 0; index < chunkIds.size(); index++) {
            args[index + 1] = chunkIds.get(index);
        }
        return jdbcTemplate.query(SQL_FIND_CHUNKS_BY_IDS.formatted(placeholders), this::toChunkRecord, args);
    }

    @Override
    public boolean updateEnabled(Long docId, List<Long> chunkIds, boolean enabled, String operator) {
        if (docId == null || chunkIds == null || chunkIds.isEmpty()) {
            return false;
        }
        String placeholders = placeholders(chunkIds.size());
        Object[] args = new Object[chunkIds.size() + 4];
        args[0] = enabled ? ENABLED_VALUE : 0;
        args[1] = Objects.requireNonNullElse(operator, "");
        args[2] = Timestamp.from(Instant.now());
        args[3] = docId;
        for (int index = 0; index < chunkIds.size(); index++) {
            args[index + 4] = chunkIds.get(index);
        }
        return jdbcTemplate.update(SQL_UPDATE_ENABLED_BY_IDS.formatted(placeholders), args) > 0;
    }

    private void bindChunk(java.sql.PreparedStatement statement, Long kbId, Long docId, VectorChunk chunk,
                           boolean writeMetadata)
            throws java.sql.SQLException {
        VectorChunk safeChunk = Objects.requireNonNull(chunk, "chunk must not be null");
        String content = Objects.requireNonNullElse(safeChunk.getContent(), "");
        Long chunkId = safeChunk.getChunkId() != null ? Long.parseLong(safeChunk.getChunkId()) : nextId();
        statement.setLong(1, chunkId);
        statement.setLong(2, kbId);
        statement.setLong(3, docId);
        statement.setObject(4, safeChunk.getIndex());
        statement.setString(5, content);
        statement.setString(6, sha256(content));
        statement.setInt(7, content.length());
        int offset = 8;
        if (writeMetadata) {
            statement.setString(offset, metadataJson(safeChunk));
            offset++;
        }
        statement.setInt(offset, ENABLED_VALUE);
        statement.setInt(offset + 1, DEFAULT_DELETED_VALUE);
    }

    private String metadataJson(VectorChunk chunk) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(chunk.getMetadata(), Map.of()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize chunk metadata failed", ex);
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

    private KnowledgeDocumentChunkContext toDocumentContext(ResultSet resultSet, int rowNumber) throws SQLException {
        return new KnowledgeDocumentChunkContext(
                resultSet.getLong("doc_id"),
                resultSet.getLong("kb_id"),
                resultSet.getString("status"),
                resultSet.getInt("enabled"),
                resultSet.getString("collection_name"),
                resultSet.getString("embedding_model"));
    }

    private KnowledgeChunkRecord toChunkRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeChunkRecord record = new KnowledgeChunkRecord();
        record.setId(resultSet.getLong("id"));
        record.setKbId(resultSet.getLong("kb_id"));
        record.setDocId(resultSet.getLong("doc_id"));
        record.setChunkIndex(resultSet.getObject("chunk_index", Integer.class));
        record.setContent(resultSet.getString("content"));
        record.setContentHash(resultSet.getString("content_hash"));
        record.setCharCount(resultSet.getObject("char_count", Integer.class));
        record.setTokenCount(resultSet.getObject("token_count", Integer.class));
        record.setEnabled(resultSet.getObject("enabled", Integer.class));
        record.setCreatedBy(resultSet.getString("created_by"));
        record.setUpdatedBy(resultSet.getString("updated_by"));
        record.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        record.setUpdateTime(toInstant(resultSet.getTimestamp("update_time")));
        return record;
    }

    private long queryChunkTotal(Long docId, String enabledWhere, Boolean enabled) {
        Long total = enabled == null
                ? jdbcTemplate.queryForObject(SQL_COUNT_CHUNKS, Long.class, docId)
                : jdbcTemplate.queryForObject(SQL_COUNT_CHUNKS + enabledWhere, Long.class, docId, enabled ? 1 : 0);
        return total == null ? 0 : total;
    }

    private List<KnowledgeChunkRecord> queryChunkPage(
            Long docId, long current, long size, String enabledWhere, Boolean enabled) {
        String sql = SQL_PAGE_CHUNKS_BASE + enabledWhere + " ORDER BY chunk_index ASC LIMIT ? OFFSET ?";
        long offset = (current - 1) * size;
        if (enabled == null) {
            return jdbcTemplate.query(sql, this::toChunkRecord, docId, size, offset);
        }
        return jdbcTemplate.query(sql, this::toChunkRecord, docId, enabled ? 1 : 0, size, offset);
    }

    private int nextChunkIndex(Long docId) {
        Integer currentMax = jdbcTemplate.queryForObject(SQL_MAX_CHUNK_INDEX, Integer.class, docId);
        return currentMax == null ? 0 : currentMax + 1;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private Long requireId(Long value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private long clampSize(long size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Long nextId() {
        long millis = System.currentTimeMillis();
        int suffix = ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
        return millis * 1_000_000 + suffix;
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
