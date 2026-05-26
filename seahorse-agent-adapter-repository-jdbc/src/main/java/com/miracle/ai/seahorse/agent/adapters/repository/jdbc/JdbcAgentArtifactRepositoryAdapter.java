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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentArtifactRepositoryAdapter implements AgentArtifactRepositoryPort {

    private static final String COLUMNS = """
            artifact_id, run_id, message_id, tenant_id, user_id, artifact_type, title, mime_type, storage_ref,
            preview_text, provenance_json, scan_status, created_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_artifact
            (artifact_id, run_id, message_id, tenant_id, user_id, artifact_type, title, mime_type, storage_ref,
             preview_text, provenance_json, scan_status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE = """
            UPDATE sa_agent_artifact
            SET run_id = ?,
                message_id = ?,
                tenant_id = ?,
                user_id = ?,
                artifact_type = ?,
                title = ?,
                mime_type = ?,
                storage_ref = ?,
                preview_text = ?,
                provenance_json = ?,
                scan_status = ?,
                created_at = ?
            WHERE artifact_id = ?
            """;
    private static final String SQL_FIND = """
            SELECT %s
            FROM sa_agent_artifact
            WHERE artifact_id = ?
            """.formatted(COLUMNS);
    private static final String SQL_LIST_BY_RUN = """
            SELECT %s
            FROM sa_agent_artifact
            WHERE run_id = ?
            ORDER BY created_at ASC, artifact_id ASC
            """.formatted(COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentArtifactRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public AgentArtifact save(AgentArtifact artifact) {
        AgentArtifact safeArtifact = Objects.requireNonNull(artifact, "artifact must not be null");
        if (findById(safeArtifact.artifactId()).isPresent()) {
            update(safeArtifact);
            return safeArtifact;
        }
        insert(safeArtifact);
        return safeArtifact;
    }

    @Override
    public Optional<AgentArtifact> findById(String artifactId) {
        if (!hasText(artifactId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND, this::mapArtifact, artifactId.trim()).stream().findFirst();
    }

    @Override
    public List<AgentArtifact> listByRunId(String runId) {
        if (!hasText(runId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_BY_RUN, this::mapArtifact, runId.trim());
    }

    private void insert(AgentArtifact artifact) {
        jdbcTemplate.update(SQL_INSERT,
                artifact.artifactId(),
                artifact.runId(),
                artifact.messageId(),
                artifact.tenantId(),
                artifact.userId(),
                artifact.artifactType().name(),
                artifact.title(),
                artifact.mimeType(),
                artifact.storageRef(),
                artifact.previewText(),
                artifact.provenanceJson(),
                artifact.scanStatus().name(),
                toTimestamp(artifact.createdAt()));
    }

    private void update(AgentArtifact artifact) {
        jdbcTemplate.update(SQL_UPDATE,
                artifact.runId(),
                artifact.messageId(),
                artifact.tenantId(),
                artifact.userId(),
                artifact.artifactType().name(),
                artifact.title(),
                artifact.mimeType(),
                artifact.storageRef(),
                artifact.previewText(),
                artifact.provenanceJson(),
                artifact.scanStatus().name(),
                toTimestamp(artifact.createdAt()),
                artifact.artifactId());
    }

    private AgentArtifact mapArtifact(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentArtifact(
                resultSet.getString("artifact_id"),
                resultSet.getString("run_id"),
                resultSet.getString("message_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("user_id"),
                AgentArtifactType.valueOf(resultSet.getString("artifact_type")),
                resultSet.getString("title"),
                resultSet.getString("mime_type"),
                resultSet.getString("storage_ref"),
                resultSet.getString("preview_text"),
                resultSet.getString("provenance_json"),
                AgentArtifactScanStatus.valueOf(resultSet.getString("scan_status")),
                toInstant(resultSet.getTimestamp("created_at")));
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
