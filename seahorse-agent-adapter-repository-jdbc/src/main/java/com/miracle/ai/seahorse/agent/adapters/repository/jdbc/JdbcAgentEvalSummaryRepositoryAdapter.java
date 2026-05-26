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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummaryPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentEvalSummaryRepositoryAdapter implements AgentEvalSummaryRepositoryPort {

    private static final TypeReference<List<String>> EVIDENCE_LIST_TYPE = new TypeReference<>() {
    };
    private static final String SUMMARY_COLUMNS = """
            summary_id, tenant_id, agent_id, version_id, eval_type, status, score,
            pass_threshold, warn_threshold, case_count, dataset_ref, eval_run_ref,
            evidence_json, created_by, created_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_eval_summary
            (summary_id, tenant_id, agent_id, version_id, eval_type, status, score,
             pass_threshold, warn_threshold, case_count, dataset_ref, eval_run_ref,
             evidence_json, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_LATEST = """
            SELECT %s
            FROM sa_agent_eval_summary
            WHERE tenant_id = ?
              AND agent_id = ?
              AND version_id = ?
              AND eval_type = ?
            ORDER BY created_at DESC, summary_id DESC
            LIMIT 1
            """.formatted(SUMMARY_COLUMNS);
    private static final String SQL_COUNT_HISTORY = """
            SELECT COUNT(1)
            FROM sa_agent_eval_summary
            WHERE tenant_id = ?
              AND agent_id = ?
              AND version_id = ?
            """;
    private static final String SQL_FIND_HISTORY = """
            SELECT %s
            FROM sa_agent_eval_summary
            WHERE tenant_id = ?
              AND agent_id = ?
              AND version_id = ?
            """.formatted(SUMMARY_COLUMNS);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAgentEvalSummaryRepositoryAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcAgentEvalSummaryRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public AgentEvalSummary append(AgentEvalSummary summary) {
        AgentEvalSummary safeSummary = Objects.requireNonNull(summary, "summary must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeSummary.summaryId(),
                safeSummary.tenantId(),
                safeSummary.agentId(),
                safeSummary.versionId(),
                safeSummary.evalType().name(),
                safeSummary.status().name(),
                safeSummary.score(),
                safeSummary.passThreshold(),
                safeSummary.warnThreshold(),
                safeSummary.caseCount(),
                safeSummary.datasetRef(),
                safeSummary.evalRunRef(),
                writeEvidence(safeSummary.evidenceRefs()),
                safeSummary.createdBy(),
                toTimestamp(safeSummary.createdAt()));
        return safeSummary;
    }

    @Override
    public Optional<AgentEvalSummary> findLatest(String tenantId,
                                                 String agentId,
                                                 String versionId,
                                                 AgentEvalType evalType) {
        if (!hasText(tenantId) || !hasText(agentId) || !hasText(versionId) || evalType == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                        SQL_FIND_LATEST,
                        this::mapSummary,
                        tenantId.trim(),
                        agentId.trim(),
                        versionId.trim(),
                        evalType.name())
                .stream()
                .findFirst();
    }

    @Override
    public AgentEvalSummaryPage findHistory(AgentEvalSummaryQuery query) {
        AgentEvalSummaryQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        List<Object> countArgs = new ArrayList<>(List.of(
                safeQuery.tenantId(),
                safeQuery.agentId(),
                safeQuery.versionId()));
        String evalFilter = "";
        if (safeQuery.evalType() != null) {
            evalFilter = " AND eval_type = ?";
            countArgs.add(safeQuery.evalType().name());
        }
        Long total = jdbcTemplate.queryForObject(
                SQL_COUNT_HISTORY + evalFilter,
                Long.class,
                countArgs.toArray());
        long safeTotal = total == null ? 0L : total;
        List<Object> historyArgs = new ArrayList<>(countArgs);
        historyArgs.add(safeQuery.size());
        historyArgs.add((safeQuery.current() - 1L) * safeQuery.size());
        List<AgentEvalSummary> records = jdbcTemplate.query(
                SQL_FIND_HISTORY + evalFilter + " ORDER BY created_at DESC, summary_id DESC LIMIT ? OFFSET ?",
                this::mapSummary,
                historyArgs.toArray());
        return new AgentEvalSummaryPage(
                records,
                safeTotal,
                safeQuery.size(),
                safeQuery.current(),
                pages(safeTotal, safeQuery.size()));
    }

    private AgentEvalSummary mapSummary(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentEvalSummary(
                resultSet.getString("summary_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                AgentEvalType.valueOf(resultSet.getString("eval_type")),
                AgentEvalStatus.valueOf(resultSet.getString("status")),
                resultSet.getDouble("score"),
                resultSet.getDouble("pass_threshold"),
                resultSet.getDouble("warn_threshold"),
                resultSet.getInt("case_count"),
                resultSet.getString("dataset_ref"),
                resultSet.getString("eval_run_ref"),
                readEvidence(resultSet.getString("evidence_json")),
                resultSet.getString("created_by"),
                toInstant(resultSet.getTimestamp("created_at")));
    }

    private String writeEvidence(List<String> evidenceRefs) {
        try {
            return objectMapper.writeValueAsString(evidenceRefs == null ? List.of() : evidenceRefs);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot write eval evidence refs", ex);
        }
    }

    private List<String> readEvidence(String json) {
        try {
            if (!hasText(json)) {
                return List.of();
            }
            return objectMapper.readValue(json, EVIDENCE_LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read eval evidence refs", ex);
        }
    }

    private long pages(long total, long size) {
        if (total <= 0L || size <= 0L) {
            return 0L;
        }
        return (total + size - 1L) / size;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
