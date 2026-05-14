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

package com.miracle.ai.seahorse.agent.adapters.vector.pgvector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldContains;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldEq;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldExists;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldIn;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldRange;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.MetadataFilterExpr;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * PostgreSQL pgvector adapter。
 *
 * <p>该实现将 pgvector 的 {@code ::vector}、{@code embedding <=> ?::vector} 等方言
 * 封装在 L3 adapter 内，L1/L2 仅依赖统一向量端口。
 */
public class PgVectorAdapter implements VectorSearchPort, VectorIndexPort, VectorCollectionAdminPort {

    private static final String META_COLLECTION_NAME = "collection_name";
    private static final String META_TENANT_ID = "tenant_id";
    private static final String META_KB_ID = "kb_id";
    private static final String META_DOC_ID = "doc_id";
    private static final String META_CHUNK_INDEX = "chunk_index";
    private static final String META_ENABLED = "enabled";
    private static final String META_ACL_SUBJECTS = "acl_subjects";
    private static final String INDEX_NAME = "idx_seahorse_knowledge_vector_hnsw";
    private static final String SQL_EXTENSION_EXISTS = "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'";
    private static final String SQL_SET_EF_SEARCH = "SET hnsw.ef_search = 200";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final PgVectorProperties properties;

    public PgVectorAdapter(DataSource dataSource, ObjectMapper objectMapper, PgVectorProperties properties) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public List<RetrievedChunk> search(VectorSearchRequest request) {
        VectorSearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        if (safeRequest.vector().isEmpty()) {
            return List.of();
        }
        String vectorLiteral = toVectorLiteral(safeRequest.vector());
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, SQL_SET_EF_SEARCH);
            return query(connection, safeRequest, vectorLiteral);
        } catch (SQLException ex) {
            throw new IllegalStateException("pgvector search failed", ex);
        }
    }

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql())) {
            for (VectorChunk chunk : chunks) {
                bindChunk(statement, collectionName, docId, chunk);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new IllegalStateException("pgvector batch index failed", ex);
        }
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql())) {
            bindChunk(statement, collectionName, docId, chunk);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("pgvector update chunk failed", ex);
        }
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        executeUpdate(
                "DELETE FROM " + tableName() + " WHERE metadata->>'collection_name' = ? AND metadata->>'doc_id' = ?",
                requireText(collectionName, "collectionName"), requireText(docId, "docId"));
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        executeUpdate("DELETE FROM " + tableName() + " WHERE id = ?", requireText(chunkId, "chunkId"));
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        List<String> ids = chunkIds.stream()
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream()
                .map(ignored -> "?")
                .collect(Collectors.joining(", "));
        executeUpdate("DELETE FROM " + tableName() + " WHERE id IN (" + placeholders + ")", ids.toArray());
    }

    @Override
    public boolean collectionExists(String collectionName) {
        try (Connection connection = dataSource.getConnection()) {
            requirePostgreSql(connection);
            requirePgVectorExtension(connection);
            return tableExists(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("pgvector collection check failed", ex);
        }
    }

    @Override
    public void ensureCollection(String collectionName) {
        try (Connection connection = dataSource.getConnection()) {
            requirePostgreSql(connection);
            requirePgVectorExtension(connection);
            execute(connection, createTableSql());
            execute(connection, createIndexSql());
        } catch (SQLException ex) {
            throw new IllegalStateException("pgvector ensure collection failed", ex);
        }
    }

    private List<RetrievedChunk> query(Connection connection, VectorSearchRequest request, String vectorLiteral)
            throws SQLException {
        List<RetrievedChunk> chunks = new ArrayList<>();
        SqlFilter sqlFilter = sqlFilter(request);
        try (PreparedStatement statement = connection.prepareStatement(searchSql(sqlFilter))) {
            statement.setString(1, vectorLiteral);
            statement.setString(2, requireText(request.collectionName(), "collectionName"));
            int index = 3;
            for (Object arg : sqlFilter.args()) {
                statement.setObject(index++, arg);
            }
            statement.setString(index++, vectorLiteral);
            statement.setInt(index, topK(request.topK()));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    chunks.add(retrievedChunk(resultSet));
                }
            }
        }
        return chunks;
    }

    private RetrievedChunk retrievedChunk(ResultSet resultSet) throws SQLException {
        Map<String, Object> metadata = metadata(resultSet.getString("metadata"));
        return RetrievedChunk.builder()
                .id(resultSet.getString("id"))
                .text(resultSet.getString("content"))
                .score(resultSet.getFloat("score"))
                .tenantId(string(metadata, META_TENANT_ID))
                .kbId(string(metadata, META_KB_ID))
                .docId(string(metadata, META_DOC_ID))
                .collectionName(string(metadata, META_COLLECTION_NAME))
                .chunkIndex(integer(metadata, META_CHUNK_INDEX))
                .metadata(metadata)
                .build();
    }

    private void bindChunk(PreparedStatement statement, String collectionName, String docId, VectorChunk chunk)
            throws SQLException {
        VectorChunk safeChunk = Objects.requireNonNull(chunk, "chunk must not be null");
        statement.setString(1, requireText(safeChunk.getChunkId(), "chunkId"));
        statement.setString(2, Objects.requireNonNullElse(safeChunk.getContent(), ""));
        statement.setString(3, metadataJson(collectionName, docId, safeChunk));
        statement.setString(4, toVectorLiteral(requireVector(safeChunk.getEmbedding())));
    }

    private String metadataJson(String collectionName, String docId, VectorChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (chunk.getMetadata() != null) {
            metadata.putAll(chunk.getMetadata());
        }
        metadata.put(META_COLLECTION_NAME, requireText(collectionName, "collectionName"));
        metadata.put(META_DOC_ID, requireText(docId, "docId"));
        metadata.put(META_CHUNK_INDEX, chunk.getIndex());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize vector metadata failed", ex);
        }
    }

    private void executeUpdate(String sql, Object... args) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("pgvector update failed", ex);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void requirePostgreSql(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        if (productName == null || !productName.toLowerCase().contains("postgresql")) {
            throw new IllegalStateException("pgvector adapter requires PostgreSQL");
        }
    }

    private void requirePgVectorExtension(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL_EXTENSION_EXISTS)) {
            if (!resultSet.next() || resultSet.getInt(1) <= 0) {
                throw new IllegalStateException("pgvector extension is not installed");
            }
        }
    }

    private boolean tableExists(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private String upsertSql() {
        return "INSERT INTO " + tableName()
                + " (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector) "
                + "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, "
                + "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding";
    }

    private String searchSql(SqlFilter sqlFilter) {
        return "SELECT id, content, metadata, 1 - (embedding <=> ?::vector) AS score FROM " + tableName()
                + " WHERE metadata->>'collection_name' = ?" + sqlFilter.whereSql()
                + " ORDER BY embedding <=> ?::vector LIMIT ?";
    }

    private String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + tableName()
                + " (id varchar(128) PRIMARY KEY, content text NOT NULL, metadata jsonb NOT NULL, "
                + "embedding vector(" + properties.dimension() + ") NOT NULL)";
    }

    private String createIndexSql() {
        return "CREATE INDEX IF NOT EXISTS " + INDEX_NAME + " ON " + tableName()
                + " USING hnsw (embedding vector_cosine_ops)";
    }

    private float[] requireVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        if (vector.length != properties.dimension()) {
            throw new IllegalArgumentException("embedding dimension mismatch, expected " + properties.dimension());
        }
        return vector;
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            appendVectorValue(builder, index, embedding[index]);
        }
        return builder.append("]").toString();
    }

    private String toVectorLiteral(List<Float> embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.size(); index++) {
            appendVectorValue(builder, index, embedding.get(index));
        }
        return builder.append("]").toString();
    }

    private void appendVectorValue(StringBuilder builder, int index, float value) {
        if (index > 0) {
            builder.append(',');
        }
        builder.append(value);
    }

    private int topK(int topK) {
        return topK <= 0 ? 5 : topK;
    }

    private String tableName() {
        String tableName = properties.tableName();
        if (!tableName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("invalid pgvector table name: " + tableName);
        }
        return tableName;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private SqlFilter sqlFilter(VectorSearchRequest request) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        // 系统字段来自 RetrievalFilter，动态字段只消费编译后的 AST，避免原始 Map 直通 SQL。
        SystemRetrievalFilter system = request.compiledFilter().sourceFilter().system();
        appendEq(clauses, args, META_TENANT_ID, system.tenantId());
        appendIn(clauses, args, META_KB_ID, system.knowledgeBaseIds());
        appendIn(clauses, args, META_DOC_ID, system.documentIds());
        appendIn(clauses, args, "file_type", system.fileTypes());
        appendIn(clauses, args, "source_type", system.sourceTypes());
        appendAcl(clauses, args, system.aclSubjectIds());
        if (system.enabledOnly()) {
            clauses.add("(metadata->>'" + META_ENABLED + "' IS NULL OR metadata->>'" + META_ENABLED + "' = 'true')");
        }
        appendExpression(clauses, args, request.compiledFilter(), request.compiledFilter().expression());
        String whereSql = clauses.isEmpty() ? "" : " AND " + String.join(" AND ", clauses);
        return new SqlFilter(whereSql, args);
    }

    private void appendExpression(List<String> clauses,
                                  List<Object> args,
                                  CompiledMetadataFilter filter,
                                  MetadataFilterExpr expression) {
        if (filter == null || expression == null) {
            return;
        }
        if (expression instanceof FilterAnd filterAnd) {
            filterAnd.children().forEach(child -> appendExpression(clauses, args, filter, child));
            return;
        }
        if (expression instanceof FieldEq fieldEq) {
            appendEq(clauses, args, fieldKey(fieldEq.field().backendMapping().canonicalName()), fieldEq.value());
        } else if (expression instanceof FieldIn fieldIn) {
            appendIn(clauses, args, fieldKey(fieldIn.field().backendMapping().canonicalName()), fieldIn.values());
        } else if (expression instanceof FieldRange fieldRange) {
            String key = fieldKey(fieldRange.field().backendMapping().canonicalName());
            if (fieldRange.from() != null) {
                clauses.add("metadata->>'" + key + "' >= ?");
                args.add(fieldRange.from());
            }
            if (fieldRange.to() != null) {
                clauses.add("metadata->>'" + key + "' <= ?");
                args.add(fieldRange.to());
            }
        } else if (expression instanceof FieldContains fieldContains) {
            String key = fieldKey(fieldContains.field().backendMapping().canonicalName());
            clauses.add("metadata->>'" + key + "' LIKE ?");
            args.add("%" + fieldContains.value() + "%");
        } else if (expression instanceof FieldExists fieldExists) {
            String key = fieldKey(fieldExists.field().backendMapping().canonicalName());
            clauses.add("metadata ? '" + key + "'");
        }
    }

    private void appendEq(List<String> clauses, List<Object> args, String key, Object value) {
        if (value == null || Objects.toString(value, "").isBlank()) {
            return;
        }
        clauses.add("metadata->>'" + fieldKey(key) + "' = ?");
        args.add(value);
    }

    private void appendIn(List<String> clauses, List<Object> args, String key, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String placeholders = values.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        clauses.add("metadata->>'" + fieldKey(key) + "' IN (" + placeholders + ")");
        args.addAll(values);
    }

    private void appendAcl(List<String> clauses, List<Object> args, Collection<String> aclSubjectIds) {
        if (aclSubjectIds == null || aclSubjectIds.isEmpty()) {
            return;
        }
        String arrayPlaceholders = aclSubjectIds.stream()
                .map(ignored -> "?::text")
                .collect(Collectors.joining(", "));
        String scalarPlaceholders = aclSubjectIds.stream()
                .map(ignored -> "?")
                .collect(Collectors.joining(", "));
        // ACL 是权限边界字段，数组交集下推后仍由 MetadataGuard 做兜底校验。
        clauses.add("(jsonb_exists_any(metadata->'" + META_ACL_SUBJECTS + "', ARRAY[" + arrayPlaceholders
                + "]) OR metadata->>'" + META_ACL_SUBJECTS + "' IN (" + scalarPlaceholders + "))");
        args.addAll(aclSubjectIds);
        args.addAll(aclSubjectIds);
    }

    private String fieldKey(String key) {
        String safeKey = Objects.requireNonNullElse(key, "").trim();
        if (!safeKey.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("invalid metadata field key: " + key);
        }
        return safeKey;
    }

    private Map<String, Object> metadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : Objects.toString(value);
    }

    private Integer integer(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(Objects.toString(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record SqlFilter(String whereSql, List<Object> args) {
    }
}
