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
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationRunRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationRunSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationComparisonRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationRunRepositoryPort;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 JDBC 的检索评测集仓储。
 *
 * <p>评测样本以强类型 JSON 保存，读取后仍交给内核评测服务执行，避免 Web 或仓储层直接构造检索链路。
 */
public class JdbcRetrievalEvaluationDatasetRepositoryAdapter
        implements RetrievalEvaluationDatasetRepositoryPort, RetrievalEvaluationRunRepositoryPort,
        RetrievalEvaluationComparisonRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final JavaType caseListType;
    private final JavaType comparisonReportType;
    private final JavaType reportType;
    private final String jsonPlaceholder;

    public JdbcRetrievalEvaluationDatasetRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        DataSource safeDataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.jdbcTemplate = new JdbcTemplate(safeDataSource);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.caseListType = this.objectMapper.getTypeFactory()
                .constructCollectionType(List.class, RetrievalEvaluationCase.class);
        this.comparisonReportType = this.objectMapper.getTypeFactory()
                .constructType(RetrievalEvaluationComparisonReport.class);
        this.reportType = this.objectMapper.getTypeFactory().constructType(RetrievalEvaluationReport.class);
        this.jsonPlaceholder = isPostgres(safeDataSource) ? "?::jsonb" : "?";
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
        String datasetId = safePayload.datasetId().isBlank() ? SnowflakeIds.nextIdString() : safePayload.datasetId();
        String casesJson = casesJson(safePayload.cases());
        int enabled = Boolean.FALSE.equals(safePayload.enabled()) ? 0 : 1;
        int updated = jdbcTemplate.update("""
                UPDATE t_retrieval_evaluation_dataset
                SET dataset_name = ?,
                    description = ?,
                    cases_json = %s,
                    enabled = ?,
                    deleted = 0,
                    update_time = CURRENT_TIMESTAMP
                WHERE kb_id = ?
                  AND id = ?
                """.formatted(jsonPlaceholder), safePayload.name(), safePayload.description(), casesJson, enabled,
                safeKnowledgeBaseId, datasetId);
        if (updated <= 0) {
            jdbcTemplate.update("""
                    INSERT INTO t_retrieval_evaluation_dataset(
                        id, kb_id, dataset_name, description, cases_json, enabled,
                        create_time, update_time, deleted
                    ) VALUES (?, ?, ?, ?, %s, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """.formatted(jsonPlaceholder), datasetId, safeKnowledgeBaseId, safePayload.name(), safePayload.description(),
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

    @Override
    public RetrievalEvaluationRunRecord saveRun(String knowledgeBaseId, String datasetId,
                                                RetrievalEvaluationReport report) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        String safeDatasetId = Objects.requireNonNullElse(datasetId, "").trim();
        RetrievalEvaluationReport safeReport = report == null
                ? emptyReport()
                : report;
        String runId = SnowflakeIds.nextIdString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_retrieval_evaluation_run(
                        id, kb_id, dataset_id, strategy_name, top_k, case_count, evaluable_case_count,
                        recall_at_k, mrr, ndcg_at_k, empty_recall_rate, avg_latency_ms, p95_latency_ms,
                        report_json, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, %s, CURRENT_TIMESTAMP)
                    """.formatted(jsonPlaceholder),
                    runId,
                    safeKnowledgeBaseId,
                    safeDatasetId,
                    safeReport.strategyName(),
                    safeReport.topK(),
                    safeReport.caseCount(),
                    safeReport.evaluableCaseCount(),
                    safeReport.recallAtK(),
                    safeReport.mrr(),
                    safeReport.ndcgAtK(),
                    safeReport.emptyRecallRate(),
                    safeReport.averageLatencyMs(),
                    safeReport.p95LatencyMs(),
                    reportJson(safeReport));
        } catch (DataAccessException ex) {
            // 历史表尚未迁移时不阻断评测链路，调用方仍可拿到本次即时报告。
            return new RetrievalEvaluationRunRecord(runId, safeKnowledgeBaseId, safeDatasetId,
                    safeReport, Instant.now());
        }
        return findRun(safeKnowledgeBaseId, safeDatasetId, runId)
                .orElseGet(() -> new RetrievalEvaluationRunRecord(runId, safeKnowledgeBaseId, safeDatasetId,
                        safeReport, Instant.now()));
    }

    @Override
    public List<RetrievalEvaluationRunSummary> listRuns(String knowledgeBaseId, String datasetId, int limit) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        String safeDatasetId = Objects.requireNonNullElse(datasetId, "").trim();
        try {
            return jdbcTemplate.query("""
                    SELECT id, kb_id, dataset_id, strategy_name, top_k, case_count, evaluable_case_count,
                           recall_at_k, mrr, ndcg_at_k, empty_recall_rate, avg_latency_ms, p95_latency_ms,
                           create_time
                    FROM t_retrieval_evaluation_run
                    WHERE kb_id = ?
                      AND dataset_id = ?
                    ORDER BY create_time DESC, id DESC
                    LIMIT ?
                    """, this::toRunSummary, safeKnowledgeBaseId, safeDatasetId, Math.max(1, limit));
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    @Override
    public Optional<RetrievalEvaluationRunRecord> findRun(String knowledgeBaseId, String datasetId, String runId) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        String safeDatasetId = Objects.requireNonNullElse(datasetId, "").trim();
        String safeRunId = Objects.requireNonNullElse(runId, "").trim();
        if (safeKnowledgeBaseId.isBlank() || safeDatasetId.isBlank() || safeRunId.isBlank()) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, kb_id, dataset_id, report_json, create_time
                    FROM t_retrieval_evaluation_run
                    WHERE kb_id = ?
                      AND dataset_id = ?
                      AND id = ?
                    """, this::toRunRecord, safeKnowledgeBaseId, safeDatasetId, safeRunId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public RetrievalEvaluationComparisonRecord saveComparison(String knowledgeBaseId, String datasetId,
                                                              RetrievalEvaluationComparisonReport report) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        String safeDatasetId = Objects.requireNonNullElse(datasetId, "").trim();
        RetrievalEvaluationComparisonReport safeReport = report == null
                ? emptyComparisonReport()
                : report;
        String comparisonId = SnowflakeIds.nextIdString();
        int strategyCount = safeReport.reports().size();
        int caseCount = safeReport.reports().isEmpty() ? 0 : safeReport.reports().get(0).caseCount();
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_retrieval_evaluation_comparison(
                        id, kb_id, dataset_id, baseline_strategy_name, winner_strategy_name,
                        strategy_count, case_count, report_json, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, %s, CURRENT_TIMESTAMP)
                    """.formatted(jsonPlaceholder),
                    comparisonId,
                    safeKnowledgeBaseId,
                    safeDatasetId,
                    safeReport.baselineStrategyName(),
                    safeReport.winnerStrategyName(),
                    strategyCount,
                    caseCount,
                    comparisonReportJson(safeReport));
        } catch (DataAccessException ex) {
            return new RetrievalEvaluationComparisonRecord(comparisonId, safeKnowledgeBaseId, safeDatasetId,
                    safeReport, Instant.now());
        }
        return findComparison(safeKnowledgeBaseId, safeDatasetId, comparisonId)
                .orElseGet(() -> new RetrievalEvaluationComparisonRecord(comparisonId,
                        safeKnowledgeBaseId, safeDatasetId, safeReport, Instant.now()));
    }

    @Override
    public List<RetrievalEvaluationComparisonSummary> listComparisons(String knowledgeBaseId, String datasetId,
                                                                      int limit) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        String safeDatasetId = Objects.requireNonNullElse(datasetId, "").trim();
        try {
            return jdbcTemplate.query("""
                    SELECT id, kb_id, dataset_id, baseline_strategy_name, winner_strategy_name,
                           strategy_count, case_count, create_time
                    FROM t_retrieval_evaluation_comparison
                    WHERE kb_id = ?
                      AND dataset_id = ?
                    ORDER BY create_time DESC, id DESC
                    LIMIT ?
                    """, this::toComparisonSummary, safeKnowledgeBaseId, safeDatasetId, Math.max(1, limit));
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    @Override
    public Optional<RetrievalEvaluationComparisonRecord> findComparison(String knowledgeBaseId, String datasetId,
                                                                        String comparisonId) {
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        String safeDatasetId = Objects.requireNonNullElse(datasetId, "").trim();
        String safeComparisonId = Objects.requireNonNullElse(comparisonId, "").trim();
        if (safeKnowledgeBaseId.isBlank() || safeDatasetId.isBlank() || safeComparisonId.isBlank()) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, kb_id, dataset_id, report_json, create_time
                    FROM t_retrieval_evaluation_comparison
                    WHERE kb_id = ?
                      AND dataset_id = ?
                      AND id = ?
                    """, this::toComparisonRecord, safeKnowledgeBaseId, safeDatasetId, safeComparisonId)
                    .stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
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

    private RetrievalEvaluationRunSummary toRunSummary(ResultSet rs, int rowNum) throws SQLException {
        return new RetrievalEvaluationRunSummary(
                rs.getString("id"),
                rs.getString("kb_id"),
                rs.getString("dataset_id"),
                rs.getString("strategy_name"),
                rs.getInt("top_k"),
                rs.getInt("case_count"),
                rs.getInt("evaluable_case_count"),
                rs.getDouble("recall_at_k"),
                rs.getDouble("mrr"),
                rs.getDouble("ndcg_at_k"),
                rs.getDouble("empty_recall_rate"),
                rs.getDouble("avg_latency_ms"),
                rs.getDouble("p95_latency_ms"),
                instant(rs.getTimestamp("create_time")));
    }

    private RetrievalEvaluationRunRecord toRunRecord(ResultSet rs, int rowNum) throws SQLException {
        return new RetrievalEvaluationRunRecord(
                rs.getString("id"),
                rs.getString("kb_id"),
                rs.getString("dataset_id"),
                report(rs.getString("report_json")),
                instant(rs.getTimestamp("create_time")));
    }

    private RetrievalEvaluationComparisonSummary toComparisonSummary(ResultSet rs, int rowNum) throws SQLException {
        return new RetrievalEvaluationComparisonSummary(
                rs.getString("id"),
                rs.getString("kb_id"),
                rs.getString("dataset_id"),
                rs.getString("baseline_strategy_name"),
                rs.getString("winner_strategy_name"),
                rs.getInt("strategy_count"),
                rs.getInt("case_count"),
                instant(rs.getTimestamp("create_time")));
    }

    private RetrievalEvaluationComparisonRecord toComparisonRecord(ResultSet rs, int rowNum) throws SQLException {
        return new RetrievalEvaluationComparisonRecord(
                rs.getString("id"),
                rs.getString("kb_id"),
                rs.getString("dataset_id"),
                comparisonReport(rs.getString("report_json")),
                instant(rs.getTimestamp("create_time")));
    }

    private List<RetrievalEvaluationCase> cases(String casesJson) {
        String json = normalizeJsonPayload(casesJson);
        if (json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, caseListType);
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

    private RetrievalEvaluationReport report(String reportJson) {
        String json = normalizeJsonPayload(reportJson);
        if (json.isBlank()) {
            return emptyReport();
        }
        try {
            return objectMapper.readValue(json, reportType);
        } catch (JsonProcessingException ex) {
            return emptyReport();
        }
    }

    private RetrievalEvaluationComparisonReport comparisonReport(String reportJson) {
        String json = normalizeJsonPayload(reportJson);
        if (json.isBlank()) {
            return emptyComparisonReport();
        }
        try {
            return objectMapper.readValue(json, comparisonReportType);
        } catch (JsonProcessingException ex) {
            return emptyComparisonReport();
        }
    }

    private String reportJson(RetrievalEvaluationReport report) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(report, emptyReport()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize retrieval evaluation report failed", ex);
        }
    }

    private String comparisonReportJson(RetrievalEvaluationComparisonReport report) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(report, emptyComparisonReport()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize retrieval evaluation comparison report failed", ex);
        }
    }

    private RetrievalEvaluationReport emptyReport() {
        return new RetrievalEvaluationReport("", 0, 0, 0, 0D, 0D, 0D, 0D, 0D, 0D, List.of());
    }

    private RetrievalEvaluationComparisonReport emptyComparisonReport() {
        return new RetrievalEvaluationComparisonReport("", "", List.of(), List.of());
    }

    private String normalizeJsonPayload(String json) {
        String value = Objects.requireNonNullElse(json, "").trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            try {
                return Objects.requireNonNullElse(objectMapper.readValue(value, String.class), "").trim();
            } catch (JsonProcessingException ex) {
                return value;
            }
        }
        return value;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private boolean isPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData == null ? "" : metaData.getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("postgresql");
        } catch (SQLException ex) {
            return false;
        }
    }
}
