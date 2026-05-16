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
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 JDBC 的检索评测集仓储。
 *
 * <p>评测样本以强类型 JSON 保存，读取后仍交给内核评测服务执行，避免 Web 或仓储层直接构造检索链路。
 */
public class JdbcRetrievalEvaluationDatasetRepositoryAdapter
        implements RetrievalEvaluationDatasetRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final JavaType caseListType;

    public JdbcRetrievalEvaluationDatasetRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.caseListType = this.objectMapper.getTypeFactory()
                .constructCollectionType(List.class, RetrievalEvaluationCase.class);
    }

    @Override
    public List<RetrievalEvaluationDatasetSummary> listDatasets(String knowledgeBaseId, boolean includeDisabled) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        try {
            return jdbcTemplate.query("""
                    SELECT id, kb_id, dataset_name, description, cases_json, enabled, create_time, update_time
                    FROM t_retrieval_evaluation_dataset
                    WHERE kb_id = ?
                      AND deleted = 0
                      AND (? = 1 OR enabled = 1)
                    ORDER BY update_time DESC, dataset_name ASC
                    """, this::toSummary, safeKnowledgeBaseId, includeDisabled ? 1 : 0);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    @Override
    public Optional<RetrievalEvaluationDataset> findDataset(String knowledgeBaseId, String datasetId) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        String safeDatasetId = Objects.requireNonNullElse(datasetId, "").trim();
        if (safeKnowledgeBaseId.isBlank() || safeDatasetId.isBlank()) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, kb_id, dataset_name, description, cases_json, enabled, create_time, update_time
                    FROM t_retrieval_evaluation_dataset
                    WHERE kb_id = ?
                      AND id = ?
                      AND deleted = 0
                    """, this::toDataset, safeKnowledgeBaseId, safeDatasetId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public RetrievalEvaluationDataset upsertDataset(String knowledgeBaseId, RetrievalEvaluationDatasetPayload payload) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        RetrievalEvaluationDatasetPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String datasetId = safePayload.datasetId().isBlank() ? UUID.randomUUID().toString() : safePayload.datasetId();
        String casesJson = casesJson(safePayload.cases());
        int enabled = Boolean.FALSE.equals(safePayload.enabled()) ? 0 : 1;
        int updated = jdbcTemplate.update("""
                UPDATE t_retrieval_evaluation_dataset
                SET dataset_name = ?,
                    description = ?,
                    cases_json = ?,
                    enabled = ?,
                    deleted = 0,
                    update_time = CURRENT_TIMESTAMP
                WHERE kb_id = ?
                  AND id = ?
                """, safePayload.name(), safePayload.description(), casesJson, enabled,
                safeKnowledgeBaseId, datasetId);
        if (updated <= 0) {
            jdbcTemplate.update("""
                    INSERT INTO t_retrieval_evaluation_dataset(
                        id, kb_id, dataset_name, description, cases_json, enabled,
                        create_time, update_time, deleted
                    ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """, datasetId, safeKnowledgeBaseId, safePayload.name(), safePayload.description(),
                    casesJson, enabled);
        }
        return findDataset(safeKnowledgeBaseId, datasetId)
                .orElseThrow(() -> new IllegalStateException("retrieval evaluation dataset saved but not found"));
    }

    @Override
    public boolean deleteDataset(String knowledgeBaseId, String datasetId) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        String safeDatasetId = Objects.requireNonNullElse(datasetId, "").trim();
        if (safeKnowledgeBaseId.isBlank() || safeDatasetId.isBlank()) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE t_retrieval_evaluation_dataset
                SET enabled = 0,
                    deleted = 1,
                    update_time = CURRENT_TIMESTAMP
                WHERE kb_id = ?
                  AND id = ?
                  AND deleted = 0
                """, safeKnowledgeBaseId, safeDatasetId) > 0;
    }

    private RetrievalEvaluationDatasetSummary toSummary(ResultSet rs, int rowNum) throws SQLException {
        return new RetrievalEvaluationDatasetSummary(
                rs.getString("id"),
                rs.getString("kb_id"),
                rs.getString("dataset_name"),
                rs.getString("description"),
                rs.getInt("enabled") == 1,
                cases(rs.getString("cases_json")).size(),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private RetrievalEvaluationDataset toDataset(ResultSet rs, int rowNum) throws SQLException {
        return new RetrievalEvaluationDataset(
                rs.getString("id"),
                rs.getString("kb_id"),
                rs.getString("dataset_name"),
                rs.getString("description"),
                rs.getInt("enabled") == 1,
                cases(rs.getString("cases_json")),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private List<RetrievalEvaluationCase> cases(String casesJson) {
        if (casesJson == null || casesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(casesJson, caseListType);
        } catch (JsonProcessingException ex) {
            // 单个评测集样本 JSON 损坏时降级为空，避免列表页整体不可用。
            return List.of();
        }
    }

    private String casesJson(List<RetrievalEvaluationCase> cases) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(cases, List.of()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize retrieval evaluation cases failed", ex);
        }
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }
}
