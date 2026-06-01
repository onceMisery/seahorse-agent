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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkLogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkLogRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentProcessRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentUpdateValues;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 JDBC 的知识库文档仓储适配器。
 *
 * <p>该适配器直接复用既有 t_knowledge_document 表，避免原生入库链路继续依赖旧 MyBatis service。
 */
public class JdbcKnowledgeDocumentRepositoryAdapter implements KnowledgeDocumentRepositoryPort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final int ENABLED_VALUE = 1;
    private static final int DELETED_VALUE = 0;
    private static final String SQL_INSERT_DOCUMENT = """
            INSERT INTO t_knowledge_document(
                id, kb_id, doc_name, source_type, enabled, chunk_count, file_url, file_type, file_size,
                process_mode, pipeline_id, status, created_by, updated_by, deleted, create_time, update_time
            ) VALUES (?, ?, ?, 'file', ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT id, kb_id, doc_name, file_url, file_type, file_size, status, process_mode, pipeline_id
            FROM t_knowledge_document
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_FIND_DETAIL_BY_ID = """
            SELECT doc.id, doc.kb_id, kb.name AS kb_name, kb.collection_name, kb.embedding_model,
                   doc.doc_name, doc.source_type, doc.source_location, doc.schedule_enabled,
                   doc.schedule_cron, doc.enabled, doc.chunk_count, doc.file_url, doc.file_type,
                   doc.file_size, doc.chunk_strategy, doc.process_mode, doc.chunk_config,
                   doc.pipeline_id, doc.status, doc.created_by, doc.updated_by,
                   doc.create_time, doc.update_time
            FROM t_knowledge_document doc
            LEFT JOIN t_knowledge_base kb ON kb.id = doc.kb_id AND kb.deleted = 0
            WHERE doc.id = ? AND doc.deleted = 0
            """;
    private static final String SQL_COUNT_PAGE_BASE = """
            SELECT COUNT(1)
            FROM t_knowledge_document doc
            WHERE doc.kb_id = ? AND doc.deleted = 0
            """;
    private static final String SQL_PAGE_BASE = """
            SELECT doc.id, doc.kb_id, kb.name AS kb_name, kb.collection_name, kb.embedding_model,
                   doc.doc_name, doc.source_type, doc.source_location, doc.schedule_enabled,
                   doc.schedule_cron, doc.enabled, doc.chunk_count, doc.file_url, doc.file_type,
                   doc.file_size, doc.chunk_strategy, doc.process_mode, doc.chunk_config,
                   doc.pipeline_id, doc.status, doc.created_by, doc.updated_by,
                   doc.create_time, doc.update_time
            FROM t_knowledge_document doc
            LEFT JOIN t_knowledge_base kb ON kb.id = doc.kb_id AND kb.deleted = 0
            WHERE doc.kb_id = ? AND doc.deleted = 0
            """;
    private static final String SQL_COUNT_LOGS =
            "SELECT COUNT(1) FROM t_knowledge_document_chunk_log WHERE doc_id = ?";
    private static final String SQL_PAGE_LOGS = """
            SELECT log.id, log.doc_id, log.status, log.process_mode, log.chunk_strategy,
                   log.pipeline_id, pipeline.name AS pipeline_name, log.extract_duration,
                   log.chunk_duration, log.embed_duration, log.persist_duration,
                   log.total_duration, log.chunk_count, log.error_message, log.start_time,
                   log.end_time, log.create_time
            FROM t_knowledge_document_chunk_log log
            LEFT JOIN t_ingestion_pipeline pipeline ON pipeline.id = log.pipeline_id AND pipeline.deleted = 0
            WHERE log.doc_id = ?
            ORDER BY log.create_time DESC
            LIMIT ? OFFSET ?
            """;
    private static final String SQL_MARK_RUNNING = """
            UPDATE t_knowledge_document
            SET status = ?, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted = 0 AND status <> ?
            """;
    private static final String SQL_MARK_SUCCESS = """
            UPDATE t_knowledge_document
            SET status = ?, chunk_count = ?, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_MARK_FAILED = """
            UPDATE t_knowledge_document
            SET status = ?, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_UPDATE_ENABLED_DOCUMENT = """
            UPDATE t_knowledge_document
            SET enabled = ?, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_UPDATE_ENABLED_CHUNKS = """
            UPDATE t_knowledge_chunk
            SET enabled = ?, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE doc_id = ? AND deleted = 0
            """;
    private static final String SQL_REPLACE_FILE_FOR_REFRESH = """
            UPDATE t_knowledge_document
            SET doc_name = ?, file_url = ?, file_type = ?, file_size = ?, updated_by = ?,
                update_time = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_DOCUMENT = """
            UPDATE t_knowledge_document
            SET deleted = 1, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_CHUNKS = """
            UPDATE t_knowledge_chunk
            SET deleted = 1, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE doc_id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_LOGS =
            "DELETE FROM t_knowledge_document_chunk_log WHERE doc_id = ?";
    private static final String SQL_LIST_DOCUMENT_CHUNKS_BASE = """
            SELECT id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
                   token_count, enabled, created_by, updated_by, create_time, update_time
            """;
    private static final String SQL_LIST_DOCUMENT_CHUNKS_TAIL = """
            FROM t_knowledge_chunk
            WHERE doc_id = ? AND deleted = 0
            ORDER BY chunk_index ASC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Boolean chunkMetadataJsonColumnExists;

    public JdbcKnowledgeDocumentRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command) {
        CreateKnowledgeDocumentCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        Long documentId = SnowflakeIds.nextId();
        KnowledgeDocumentProcessRef process = normalizeProcess(safeCommand.process());
        jdbcTemplate.update(SQL_INSERT_DOCUMENT,
                documentId,
                safeCommand.kbId(),
                requireText(safeCommand.docName(), "docName"),
                ENABLED_VALUE,
                safeCommand.file().fileUrl(),
                safeCommand.file().fileType(),
                safeCommand.file().fileSize(),
                process.processMode(),
                blankToNull(process.pipelineId()),
                STATUS_PENDING,
                safeCommand.operator(),
                safeCommand.operator(),
                DELETED_VALUE);
        return findById(documentId).orElseThrow(() -> new IllegalStateException("文档创建后不可见：" + documentId));
    }

    @Override
    public Optional<KnowledgeDocumentRecord> findById(Long docId) {
        if (docId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::toDocumentRecord, docId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<KnowledgeDocumentDetail> findDetailById(Long docId) {
        if (docId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_DETAIL_BY_ID, this::toDocumentDetail, docId)
                .stream()
                .findFirst();
    }

    @Override
    public KnowledgeDocumentPage page(Long kbId, long current, long size, String status, String keyword) {
        long safeCurrent = current <= 0 ? 1 : current;
        long safeSize = clampSize(size);
        QueryParts queryParts = buildPageQuery(status, keyword);
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(kbId);
        countArgs.addAll(queryParts.args());
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_PAGE_BASE + queryParts.where(), Long.class,
                countArgs.toArray());
        List<Object> pageArgs = new ArrayList<>(countArgs);
        pageArgs.add(safeSize);
        pageArgs.add((safeCurrent - 1) * safeSize);
        List<KnowledgeDocumentDetail> records = jdbcTemplate.query(
                SQL_PAGE_BASE + queryParts.where() + " ORDER BY doc.create_time DESC LIMIT ? OFFSET ?",
                this::toDocumentDetail,
                pageArgs.toArray());
        long safeTotal = total == null ? 0 : total;
        long pages = safeTotal == 0 ? 0 : (safeTotal + safeSize - 1) / safeSize;
        return new KnowledgeDocumentPage(records, safeTotal, safeSize, safeCurrent, pages);
    }

    @Override
    public KnowledgeDocumentChunkLogPage chunkLogs(Long docId, long current, long size) {
        long safeCurrent = current <= 0 ? 1 : current;
        long safeSize = clampSize(size);
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_LOGS, Long.class, docId);
        List<KnowledgeDocumentChunkLogRecord> records = jdbcTemplate.query(SQL_PAGE_LOGS, this::toChunkLogRecord,
                docId, safeSize, (safeCurrent - 1) * safeSize);
        long safeTotal = total == null ? 0 : total;
        long pages = safeTotal == 0 ? 0 : (safeTotal + safeSize - 1) / safeSize;
        return new KnowledgeDocumentChunkLogPage(records, safeTotal, safeSize, safeCurrent, pages);
    }

    @Override
    public boolean markRunning(Long docId, String operator) {
        if (docId == null) {
            return false;
        }
        int updated = jdbcTemplate.update(SQL_MARK_RUNNING, STATUS_RUNNING,
                Objects.requireNonNullElse(operator, ""), docId, STATUS_RUNNING);
        return updated > 0;
    }

    @Override
    public void markSuccess(Long docId, int chunkCount, String operator) {
        jdbcTemplate.update(SQL_MARK_SUCCESS, STATUS_SUCCESS, Math.max(chunkCount, 0),
                Objects.requireNonNullElse(operator, ""), docId);
    }

    @Override
    public void markFailed(Long docId, String operator, String errorMessage) {
        jdbcTemplate.update(SQL_MARK_FAILED, STATUS_FAILED,
                Objects.requireNonNullElse(operator, ""), docId);
    }

    @Override
    public boolean update(Long docId, KnowledgeDocumentUpdateValues values) {
        KnowledgeDocumentUpdateValues safeValues = Objects.requireNonNull(values, "values must not be null");
        UpdateSql updateSql = buildUpdateSql(safeValues);
        if (updateSql.args().isEmpty()) {
            return true;
        }
        updateSql.args().add(docId);
        return jdbcTemplate.update(updateSql.sql(), updateSql.args().toArray()) > 0;
    }

    @Override
    public boolean updateEnabled(Long docId, boolean enabled, String operator) {
        int enabledValue = enabled ? 1 : 0;
        String safeOperator = Objects.requireNonNullElse(operator, "");
        int updated = jdbcTemplate.update(SQL_UPDATE_ENABLED_DOCUMENT, enabledValue, safeOperator, docId);
        if (updated > 0) {
            jdbcTemplate.update(SQL_UPDATE_ENABLED_CHUNKS, enabledValue, safeOperator, docId);
        }
        return updated > 0;
    }

    @Override
    public boolean replaceFileForRefresh(Long docId, KnowledgeDocumentFileRef file, String operator) {
        KnowledgeDocumentFileRef safeFile = Objects.requireNonNull(file, "file must not be null");
        String safeOperator = Objects.requireNonNullElse(operator, "");
        String docName = hasText(safeFile.fileUrl()) ? safeFile.fileUrl() : String.valueOf(docId);
        int slashIndex = docName.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < docName.length() - 1) {
            docName = docName.substring(slashIndex + 1);
        }
        return jdbcTemplate.update(SQL_REPLACE_FILE_FOR_REFRESH,
                docName,
                safeFile.fileUrl(),
                safeFile.fileType(),
                safeFile.fileSize(),
                safeOperator,
                docId) > 0;
    }

    @Override
    public boolean delete(Long docId, String operator) {
        String safeOperator = Objects.requireNonNullElse(operator, "");
        int updated = jdbcTemplate.update(SQL_DELETE_DOCUMENT, safeOperator, docId);
        if (updated > 0) {
            jdbcTemplate.update(SQL_DELETE_CHUNKS, safeOperator, docId);
            jdbcTemplate.update(SQL_DELETE_LOGS, docId);
        }
        return updated > 0;
    }

    @Override
    public List<KnowledgeChunkRecord> listEnabledChunks(Long docId) {
        if (docId == null) {
            return List.of();
        }
        return jdbcTemplate.query(listDocumentChunksSql(), this::toChunkRecord, docId);
    }

    private KnowledgeDocumentRecord toDocumentRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeDocumentFileRef file = new KnowledgeDocumentFileRef(
                resultSet.getString("file_url"),
                resultSet.getString("file_type"),
                resultSet.getObject("file_size", Long.class));
        KnowledgeDocumentProcessRef process = new KnowledgeDocumentProcessRef(
                resultSet.getString("status"),
                resultSet.getString("process_mode"),
                resultSet.getString("pipeline_id"));
        return new KnowledgeDocumentRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getObject("kb_id", Long.class),
                resultSet.getString("doc_name"),
                file,
                process);
    }

    private KnowledgeDocumentDetail toDocumentDetail(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setId(resultSet.getObject("id", Long.class));
        detail.setKbId(resultSet.getObject("kb_id", Long.class));
        detail.setKbName(resultSet.getString("kb_name"));
        detail.setCollectionName(resultSet.getString("collection_name"));
        detail.setEmbeddingModel(resultSet.getString("embedding_model"));
        detail.setDocName(resultSet.getString("doc_name"));
        detail.setSourceType(resultSet.getString("source_type"));
        detail.setSourceLocation(resultSet.getString("source_location"));
        detail.setScheduleEnabled(resultSet.getObject("schedule_enabled", Integer.class));
        detail.setScheduleCron(resultSet.getString("schedule_cron"));
        Integer enabled = resultSet.getObject("enabled", Integer.class);
        detail.setEnabled(enabled != null && enabled == 1);
        detail.setChunkCount(resultSet.getObject("chunk_count", Integer.class));
        detail.setFileUrl(resultSet.getString("file_url"));
        detail.setFileType(resultSet.getString("file_type"));
        detail.setFileSize(resultSet.getObject("file_size", Long.class));
        detail.setChunkStrategy(resultSet.getString("chunk_strategy"));
        detail.setProcessMode(resultSet.getString("process_mode"));
        detail.setChunkConfig(resultSet.getString("chunk_config"));
        detail.setPipelineId(resultSet.getString("pipeline_id"));
        detail.setStatus(resultSet.getString("status"));
        detail.setCreatedBy(resultSet.getString("created_by"));
        detail.setUpdatedBy(resultSet.getString("updated_by"));
        detail.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        detail.setUpdateTime(toInstant(resultSet.getTimestamp("update_time")));
        return detail;
    }

    private KnowledgeDocumentChunkLogRecord toChunkLogRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeDocumentChunkLogRecord record = new KnowledgeDocumentChunkLogRecord();
        record.setId(resultSet.getObject("id", Long.class));
        record.setDocId(resultSet.getObject("doc_id", Long.class));
        record.setStatus(resultSet.getString("status"));
        record.setProcessMode(resultSet.getString("process_mode"));
        record.setChunkStrategy(resultSet.getString("chunk_strategy"));
        record.setPipelineId(resultSet.getString("pipeline_id"));
        record.setPipelineName(resultSet.getString("pipeline_name"));
        record.setExtractDuration(resultSet.getObject("extract_duration", Long.class));
        record.setChunkDuration(resultSet.getObject("chunk_duration", Long.class));
        record.setEmbedDuration(resultSet.getObject("embed_duration", Long.class));
        record.setPersistDuration(resultSet.getObject("persist_duration", Long.class));
        record.setTotalDuration(resultSet.getObject("total_duration", Long.class));
        record.setOtherDuration(calculateOtherDuration(record));
        record.setChunkCount(resultSet.getObject("chunk_count", Integer.class));
        record.setErrorMessage(resultSet.getString("error_message"));
        record.setStartTime(toInstant(resultSet.getTimestamp("start_time")));
        record.setEndTime(toInstant(resultSet.getTimestamp("end_time")));
        record.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        return record;
    }

    private KnowledgeChunkRecord toChunkRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeChunkRecord record = new KnowledgeChunkRecord();
        record.setId(resultSet.getObject("id", Long.class));
        record.setKbId(resultSet.getObject("kb_id", Long.class));
        record.setDocId(resultSet.getObject("doc_id", Long.class));
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
        record.setMetadata(readMap(resultSet.getString("metadata_json")));
        return record;
    }

    private String listDocumentChunksSql() {
        String metadataSelect = chunkMetadataJsonColumnExists()
                ? ", metadata_json "
                : ", '{}' AS metadata_json ";
        return SQL_LIST_DOCUMENT_CHUNKS_BASE + metadataSelect + SQL_LIST_DOCUMENT_CHUNKS_TAIL;
    }

    private boolean chunkMetadataJsonColumnExists() {
        if (chunkMetadataJsonColumnExists != null) {
            return chunkMetadataJsonColumnExists;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.columns
                    WHERE lower(table_name) = 't_knowledge_chunk'
                      AND lower(column_name) = 'metadata_json'
                    """, Integer.class);
            chunkMetadataJsonColumnExists = count != null && count > 0;
        } catch (RuntimeException ex) {
            chunkMetadataJsonColumnExists = false;
        }
        return chunkMetadataJsonColumnExists;
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private KnowledgeDocumentProcessRef normalizeProcess(KnowledgeDocumentProcessRef process) {
        KnowledgeDocumentProcessRef safeProcess = Objects.requireNonNullElse(process,
                new KnowledgeDocumentProcessRef(STATUS_PENDING, "pipeline", ""));
        return new KnowledgeDocumentProcessRef(STATUS_PENDING, safeProcess.processMode(), safeProcess.pipelineId());
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private QueryParts buildPageQuery(String status, String keyword) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        if (hasText(status)) {
            where.append(" AND doc.status = ?");
            args.add(status.trim());
        }
        if (hasText(keyword)) {
            where.append(" AND doc.doc_name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        return new QueryParts(where.toString(), args);
    }

    private UpdateSql buildUpdateSql(KnowledgeDocumentUpdateValues values) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addSet(sets, args, "doc_name", values.getDocName());
        addSet(sets, args, "process_mode", values.getProcessMode());
        addSet(sets, args, "chunk_strategy", values.getChunkStrategy());
        addSet(sets, args, "chunk_config", values.getChunkConfig());
        addSet(sets, args, "pipeline_id", values.getPipelineId());
        addSet(sets, args, "source_location", values.getSourceLocation());
        if (values.getScheduleEnabled() != null) {
            sets.add("schedule_enabled = ?");
            args.add(values.getScheduleEnabled());
        }
        addSet(sets, args, "schedule_cron", values.getScheduleCron());
        sets.add("updated_by = ?");
        args.add(Objects.requireNonNullElse(values.getOperator(), ""));
        sets.add("update_time = CURRENT_TIMESTAMP");
        String sql = "UPDATE t_knowledge_document SET " + String.join(", ", sets)
                + " WHERE id = ? AND deleted = 0";
        return new UpdateSql(sql, args);
    }

    private void addSet(List<String> sets, List<Object> args, String column, String value) {
        if (value == null) {
            return;
        }
        sets.add(column + " = ?");
        args.add(value);
    }

    private Long calculateOtherDuration(KnowledgeDocumentChunkLogRecord record) {
        Long total = record.getTotalDuration();
        if (total == null) {
            return null;
        }
        long extract = value(record.getExtractDuration());
        long chunk = value(record.getChunkDuration());
        long embed = value(record.getEmbedDuration());
        long persist = value(record.getPersistDuration());
        long other = "pipeline".equalsIgnoreCase(record.getProcessMode())
                ? total - chunk - persist
                : total - extract - chunk - embed - persist;
        return Math.max(0L, other);
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private long clampSize(long size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record QueryParts(String where, List<Object> args) {
    }

    private record UpdateSql(String sql, List<Object> args) {
    }
}
