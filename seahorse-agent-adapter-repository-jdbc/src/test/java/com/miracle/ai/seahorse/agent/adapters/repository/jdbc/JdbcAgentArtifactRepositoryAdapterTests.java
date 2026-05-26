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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentArtifactRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveFindAndListArtifactsByRun() {
        DriverManagerDataSource dataSource = dataSource("agent-artifact");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createArtifactSchema(jdbcTemplate);
        JdbcAgentArtifactRepositoryAdapter adapter = new JdbcAgentArtifactRepositoryAdapter(dataSource);
        AgentArtifact report = artifact("artifact-1", "run-1", AgentArtifactType.REPORT, "text/markdown",
                AgentArtifactScanStatus.CLEAN);
        AgentArtifact blocked = artifact("artifact-2", "run-1", AgentArtifactType.FILE, "application/pdf",
                AgentArtifactScanStatus.BLOCKED);
        AgentArtifact otherRun = artifact("artifact-3", "run-2", AgentArtifactType.MARKDOWN, "text/plain",
                AgentArtifactScanStatus.CLEAN);

        adapter.save(report);
        adapter.save(blocked);
        adapter.save(otherRun);

        Optional<AgentArtifact> found = adapter.findById("artifact-1");
        List<AgentArtifact> runArtifacts = adapter.listByRunId("run-1");

        assertThat(found).contains(report);
        assertThat(runArtifacts).containsExactly(report, blocked);
    }

    private static AgentArtifact artifact(String artifactId,
                                          String runId,
                                          AgentArtifactType type,
                                          String mimeType,
                                          AgentArtifactScanStatus scanStatus) {
        return new AgentArtifact(
                artifactId,
                runId,
                "message-1",
                "tenant-a",
                "user-1",
                type,
                "Research report",
                mimeType,
                "s3://agent-artifacts/" + artifactId,
                "preview",
                "{\"runId\":\"" + runId + "\"}",
                scanStatus,
                NOW);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }

    static void createArtifactSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_artifact (
                    artifact_id VARCHAR(64) PRIMARY KEY,
                    run_id VARCHAR(64) NOT NULL,
                    message_id VARCHAR(64),
                    tenant_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    artifact_type VARCHAR(32) NOT NULL,
                    title VARCHAR(256) NOT NULL,
                    mime_type VARCHAR(128) NOT NULL,
                    storage_ref VARCHAR(1000) NOT NULL,
                    preview_text CLOB,
                    provenance_json CLOB,
                    scan_status VARCHAR(32) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_artifact_run
                ON sa_agent_artifact(run_id, created_at)
                """);
    }
}
