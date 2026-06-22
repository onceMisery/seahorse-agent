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

import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
public class JdbcRunExperimentRepositoryAdapter implements RunExperimentRepositoryPort {

    private static final String EXPERIMENT_COLUMNS = """
            id, tenant_id, user_id, conversation_id, base_leaf_message_id, name, status,
            create_time, update_time, deleted
            """;

    private static final String TRIAL_COLUMNS = """
            id, tenant_id, experiment_id, run_profile_id, run_id, output_message_id,
            score_json, metric_json, status, error_message, create_time, update_time, deleted
            """;

    private static final String SQL_INSERT_EXPERIMENT = """
            INSERT INTO sa_run_experiment
            (id, tenant_id, user_id, conversation_id, base_leaf_message_id, name, status,
             create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private static final String SQL_INSERT_TRIAL = """
            INSERT INTO sa_run_experiment_trial
            (id, tenant_id, experiment_id, run_profile_id, run_id, output_message_id,
             score_json, metric_json, status, error_message, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private static final String SQL_FIND_EXPERIMENT = """
            SELECT %s
            FROM sa_run_experiment
            WHERE id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0
            """.formatted(EXPERIMENT_COLUMNS);

    private static final String SQL_LIST_TRIALS = """
            SELECT %s
            FROM sa_run_experiment_trial
            WHERE experiment_id = ? AND tenant_id = ? AND deleted = 0
            ORDER BY create_time ASC, id ASC
            """.formatted(TRIAL_COLUMNS);

    private static final String SQL_UPDATE_EXPERIMENT_STATUS = """
            UPDATE sa_run_experiment
            SET status = ?, update_time = ?
            WHERE id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_UPDATE_TRIAL_STATUS_BY_EXPERIMENT = """
            UPDATE sa_run_experiment_trial
            SET status = ?, update_time = ?
            WHERE experiment_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_UPDATE_TRIAL_SCORE = """
            UPDATE sa_run_experiment_trial
            SET score_json = ?, update_time = ?
            WHERE id = ? AND experiment_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_UPDATE_TRIAL_EXECUTION = """
            UPDATE sa_run_experiment_trial
            SET status = ?, run_id = ?, output_message_id = ?, metric_json = ?, error_message = ?, update_time = ?
            WHERE id = ? AND experiment_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcRunExperimentRepositoryAdapter(DataSource dataSource) {
        this(new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null")));
    }

    @Override
    public RunExperimentDetails create(RunExperimentRecord experiment, List<RunExperimentTrialRecord> trials) {
        RunExperimentRecord safeExperiment = Objects.requireNonNull(experiment, "experiment must not be null");
        Long experimentId = safeExperiment.getId() == null ? JdbcMemorySupport.nextId() : safeExperiment.getId();
        String tenantId = hasText(safeExperiment.getTenantId()) ? safeExperiment.getTenantId().trim() : tenantId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT_EXPERIMENT,
                experimentId,
                tenantId,
                Long.parseLong(requireText(safeExperiment.getUserId(), "userId")),
                Objects.requireNonNull(safeExperiment.getConversationId(), "conversationId must not be null"),
                safeExperiment.getBaseLeafMessageId(),
                requireText(safeExperiment.getName(), "name"),
                statusOrPending(safeExperiment.getStatus()),
                now,
                now);
        safeExperiment.setId(experimentId);
        safeExperiment.setTenantId(tenantId);
        safeExperiment.setStatus(statusOrPending(safeExperiment.getStatus()));
        safeExperiment.setCreateTime(now.toInstant());
        safeExperiment.setUpdateTime(now.toInstant());
        safeExperiment.setDeleted(0);

        List<RunExperimentTrialRecord> savedTrials = Objects.requireNonNullElse(trials, List.<RunExperimentTrialRecord>of())
                .stream()
                .filter(Objects::nonNull)
                .map(trial -> insertTrial(tenantId, experimentId, trial, now))
                .toList();
        return RunExperimentDetails.builder()
                .experiment(safeExperiment)
                .trials(savedTrials)
                .build();
    }

    @Override
    public Optional<RunExperimentDetails> findById(String userId, Long id) {
        if (id == null || !hasText(userId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_EXPERIMENT, this::mapExperiment,
                        id, Long.parseLong(userId.trim()), tenantId())
                .stream()
                .findFirst()
                .map(experiment -> RunExperimentDetails.builder()
                        .experiment(experiment)
                        .trials(jdbcTemplate.query(SQL_LIST_TRIALS, this::mapTrial, experiment.getId(), tenantId()))
                        .build());
    }

    @Override
    public Optional<RunExperimentDetails> updateExperimentStatus(String userId, Long id, String status) {
        if (id == null || !hasText(userId) || !hasText(status)) {
            return Optional.empty();
        }
        String tenantId = tenantId();
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbcTemplate.update(SQL_UPDATE_EXPERIMENT_STATUS,
                status.trim(),
                now,
                id,
                Long.parseLong(userId.trim()),
                tenantId);
        if (updated == 0) {
            return Optional.empty();
        }
        jdbcTemplate.update(SQL_UPDATE_TRIAL_STATUS_BY_EXPERIMENT, status.trim(), now, id, tenantId);
        return findById(userId, id);
    }

    @Override
    public Optional<RunExperimentDetails> updateExperimentOnlyStatus(String userId, Long id, String status) {
        if (id == null || !hasText(userId) || !hasText(status)) {
            return Optional.empty();
        }
        int updated = jdbcTemplate.update(SQL_UPDATE_EXPERIMENT_STATUS,
                status.trim(),
                Timestamp.from(Instant.now()),
                id,
                Long.parseLong(userId.trim()),
                tenantId());
        return updated == 0 ? Optional.empty() : findById(userId, id);
    }

    @Override
    public Optional<RunExperimentDetails> updateTrialScore(String userId, Long experimentId, Long trialId,
                                                           String scoreJson) {
        if (experimentId == null || trialId == null || !hasText(userId) || !hasText(scoreJson)) {
            return Optional.empty();
        }
        Optional<RunExperimentDetails> existing = findById(userId, experimentId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        int updated = jdbcTemplate.update(SQL_UPDATE_TRIAL_SCORE,
                scoreJson.trim(),
                Timestamp.from(Instant.now()),
                trialId,
                experimentId,
                tenantId());
        return updated == 0 ? Optional.empty() : findById(userId, experimentId);
    }

    @Override
    public Optional<RunExperimentDetails> updateTrialExecution(
            String userId,
            Long experimentId,
            Long trialId,
            String status,
            String runId,
            Long outputMessageId,
            String metricJson,
            String errorMessage) {
        if (experimentId == null || trialId == null || !hasText(userId) || !hasText(status)) {
            return Optional.empty();
        }
        Optional<RunExperimentDetails> existing = findById(userId, experimentId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        int updated = jdbcTemplate.update(SQL_UPDATE_TRIAL_EXECUTION,
                status.trim(),
                blankToNull(runId),
                outputMessageId,
                blankToNull(metricJson),
                blankToNull(errorMessage),
                Timestamp.from(Instant.now()),
                trialId,
                experimentId,
                tenantId());
        return updated == 0 ? Optional.empty() : findById(userId, experimentId);
    }

    private RunExperimentTrialRecord insertTrial(
            String tenantId,
            Long experimentId,
            RunExperimentTrialRecord trial,
            Timestamp now) {
        Long trialId = trial.getId() == null ? JdbcMemorySupport.nextId() : trial.getId();
        jdbcTemplate.update(SQL_INSERT_TRIAL,
                trialId,
                tenantId,
                experimentId,
                Objects.requireNonNull(trial.getRunProfileId(), "runProfileId must not be null"),
                trial.getRunId(),
                trial.getOutputMessageId(),
                blankToNull(trial.getScoreJson()),
                blankToNull(trial.getMetricJson()),
                statusOrPending(trial.getStatus()),
                blankToNull(trial.getErrorMessage()),
                now,
                now);
        trial.setId(trialId);
        trial.setTenantId(tenantId);
        trial.setExperimentId(experimentId);
        trial.setStatus(statusOrPending(trial.getStatus()));
        trial.setCreateTime(now.toInstant());
        trial.setUpdateTime(now.toInstant());
        trial.setDeleted(0);
        return trial;
    }

    private RunExperimentRecord mapExperiment(ResultSet resultSet, int rowNumber) throws SQLException {
        return RunExperimentRecord.builder()
                .id(resultSet.getLong("id"))
                .tenantId(resultSet.getString("tenant_id"))
                .userId(resultSet.getString("user_id"))
                .conversationId(resultSet.getLong("conversation_id"))
                .baseLeafMessageId(nullableLong(resultSet, "base_leaf_message_id"))
                .name(resultSet.getString("name"))
                .status(resultSet.getString("status"))
                .createTime(toInstant(resultSet.getTimestamp("create_time")))
                .updateTime(toInstant(resultSet.getTimestamp("update_time")))
                .deleted(resultSet.getInt("deleted"))
                .build();
    }

    private RunExperimentTrialRecord mapTrial(ResultSet resultSet, int rowNumber) throws SQLException {
        return RunExperimentTrialRecord.builder()
                .id(resultSet.getLong("id"))
                .tenantId(resultSet.getString("tenant_id"))
                .experimentId(resultSet.getLong("experiment_id"))
                .runProfileId(resultSet.getLong("run_profile_id"))
                .runId(resultSet.getString("run_id"))
                .outputMessageId(nullableLong(resultSet, "output_message_id"))
                .scoreJson(resultSet.getString("score_json"))
                .metricJson(resultSet.getString("metric_json"))
                .status(resultSet.getString("status"))
                .errorMessage(resultSet.getString("error_message"))
                .createTime(toInstant(resultSet.getTimestamp("create_time")))
                .updateTime(toInstant(resultSet.getTimestamp("update_time")))
                .deleted(resultSet.getInt("deleted"))
                .build();
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String tenantId() {
        return JdbcTenantSupport.resolveTenantId();
    }

    private String statusOrPending(String value) {
        return hasText(value) ? value.trim() : "PENDING";
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
