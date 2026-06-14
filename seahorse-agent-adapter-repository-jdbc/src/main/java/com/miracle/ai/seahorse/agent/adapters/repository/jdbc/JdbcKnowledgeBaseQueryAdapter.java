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

import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * 基于 JDBC 的知识库只读查询适配器。
 *
 * <p>不依赖旧 Service、Mapper 或 VO，用于让 Seahorse
 * 原生检索流程直接读取知识库元数据。
 */
public class JdbcKnowledgeBaseQueryAdapter implements KnowledgeBaseQueryPort {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;
    private static final int ENABLED_VALUE = 1;
    private static final String SQL_SEARCH_DOCUMENTS = """
            SELECT doc.id, doc.kb_id, doc.doc_name, kb.name AS kb_name
            FROM t_knowledge_document doc
            LEFT JOIN t_knowledge_base kb ON kb.id = doc.kb_id AND kb.deleted = 0
            WHERE doc.deleted = 0 AND doc.doc_name LIKE ?
            ORDER BY doc.update_time DESC
            LIMIT ?
            """;
    private static final String SQL_LIST_CHUNKS = """
            SELECT id, kb_id, doc_id, chunk_index, content, enabled
            FROM t_knowledge_chunk
            WHERE deleted = 0 AND doc_id = ?
            ORDER BY chunk_index ASC
            """;
    private static final String SQL_LIST_SEARCHABLE_KNOWLEDGE_BASES = """
            SELECT id, name, collection_name
            FROM t_knowledge_base kb
            WHERE kb.deleted = 0
              AND kb.collection_name IS NOT NULL
              AND kb.collection_name <> ''
              AND EXISTS (
                  SELECT 1
                  FROM t_knowledge_chunk chunk
                  WHERE chunk.kb_id = kb.id
                    AND chunk.deleted = 0
                    AND chunk.enabled = 1
              )
            ORDER BY (
                SELECT MAX(chunk.update_time)
                FROM t_knowledge_chunk chunk
                WHERE chunk.kb_id = kb.id
                  AND chunk.deleted = 0
                  AND chunk.enabled = 1
            ) DESC NULLS LAST,
            kb.update_time DESC
            """;
    private static final String SQL_LIST_SEARCHABLE_KNOWLEDGE_BASES_BY_EMBEDDING_MODEL = """
            SELECT id, name, collection_name
            FROM t_knowledge_base kb
            WHERE kb.deleted = 0
              AND kb.embedding_model = ?
              AND kb.collection_name IS NOT NULL
              AND kb.collection_name <> ''
              AND EXISTS (
                  SELECT 1
                  FROM t_knowledge_chunk chunk
                  WHERE chunk.kb_id = kb.id
                    AND chunk.deleted = 0
                    AND chunk.enabled = 1
              )
            ORDER BY (
                SELECT MAX(chunk.update_time)
                FROM t_knowledge_chunk chunk
                WHERE chunk.kb_id = kb.id
                  AND chunk.deleted = 0
                  AND chunk.enabled = 1
            ) DESC NULLS LAST,
            kb.update_time DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeBaseQueryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public List<KnowledgeDocumentSummary> searchDocuments(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_SEARCH_DOCUMENTS, this::toDocumentSummary, like(keyword), clampLimit(limit));
    }

    @Override
    public List<KnowledgeBaseRef> listSearchableKnowledgeBases() {
        return jdbcTemplate.query(SQL_LIST_SEARCHABLE_KNOWLEDGE_BASES, this::toKnowledgeBaseRef);
    }

    @Override
    public List<KnowledgeBaseRef> listSearchableKnowledgeBases(String embeddingModel) {
        if (embeddingModel == null || embeddingModel.isBlank()) {
            return listSearchableKnowledgeBases();
        }
        return jdbcTemplate.query(SQL_LIST_SEARCHABLE_KNOWLEDGE_BASES_BY_EMBEDDING_MODEL,
                this::toKnowledgeBaseRef, embeddingModel.trim());
    }

    @Override
    public List<KnowledgeChunkSummary> listChunksByDocId(Long docId) {
        if (docId == null) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_CHUNKS, this::toChunkSummary, docId);
    }

    private KnowledgeDocumentSummary toDocumentSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new KnowledgeDocumentSummary(
                resultSet.getLong("id"),
                resultSet.getLong("kb_id"),
                resultSet.getString("doc_name"),
                resultSet.getString("kb_name"));
    }

    private KnowledgeChunkSummary toChunkSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new KnowledgeChunkSummary(
                resultSet.getLong("id"),
                resultSet.getLong("kb_id"),
                resultSet.getLong("doc_id"),
                resultSet.getObject("chunk_index", Integer.class),
                resultSet.getString("content"),
                resultSet.getInt("enabled") == ENABLED_VALUE);
    }

    private KnowledgeBaseRef toKnowledgeBaseRef(ResultSet resultSet, int rowNumber) throws SQLException {
        return new KnowledgeBaseRef(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("collection_name"));
    }

    private String like(String keyword) {
        return "%" + keyword.trim() + "%";
    }

    private int clampLimit(int limit) {
        if (limit < MIN_LIMIT) {
            return MIN_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
