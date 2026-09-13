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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamEdge;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamEdgeCondition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMember;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamNodeRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamNodeStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAgentTeamRepositoryAdapterTests {

    private JdbcAgentTeamRepositoryAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        Connection connection = java.sql.DriverManager.getConnection(
                "jdbc:h2:mem:agent-team;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sa_agent_team (
                        team_id              VARCHAR(64)  PRIMARY KEY,
                        tenant_id            VARCHAR(64)  NOT NULL,
                        name                 VARCHAR(128) NOT NULL,
                        mode                 VARCHAR(32)  NOT NULL,
                        owner_team           VARCHAR(128) DEFAULT '',
                        supervisor_member_id VARCHAR(64),
                        status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
                        definition_json      TEXT         NOT NULL,
                        created_at           TIMESTAMP    NOT NULL,
                        updated_at           TIMESTAMP    NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sa_agent_team_run (
                        team_run_id    VARCHAR(64)  PRIMARY KEY,
                        team_id        VARCHAR(64)  NOT NULL,
                        tenant_id      VARCHAR(64)  NOT NULL,
                        user_id        VARCHAR(64),
                        mode           VARCHAR(32)  NOT NULL,
                        objective      TEXT,
                        status         VARCHAR(32)  NOT NULL,
                        parent_run_id  VARCHAR(64),
                        summary        TEXT,
                        error_code     VARCHAR(64),
                        error_message  TEXT,
                        node_runs_json TEXT,
                        started_at     TIMESTAMP    NOT NULL,
                        finished_at    TIMESTAMP
                    )
                    """);
        }
        adapter = new JdbcAgentTeamRepositoryAdapter(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        "jdbc:h2:mem:agent-team;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    @Test
    void shouldRoundTripTeamDefinitionWithMembersAndEdges() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        AgentTeamDefinition definition = new AgentTeamDefinition(
                "team-1", "tenant-1", "research-team", AgentTeamMode.WORKFLOW_DAG, "org", null,
                List.of(new AgentTeamMember("a", "agent-a", "researcher", "先调研"),
                        new AgentTeamMember("b", "agent-b", "writer", "")),
                List.of(new AgentTeamEdge("a", "b", AgentTeamEdgeCondition.ON_SUCCESS)),
                true, now, now);

        adapter.saveDefinition(definition);
        Optional<AgentTeamDefinition> loaded = adapter.findDefinitionById("team-1");

        assertTrue(loaded.isPresent());
        assertEquals("research-team", loaded.orElseThrow().name());
        assertEquals(2, loaded.orElseThrow().members().size());
        assertEquals("先调研", loaded.orElseThrow().members().get(0).instruction());
        assertEquals(AgentTeamEdgeCondition.ON_SUCCESS, loaded.orElseThrow().edges().get(0).condition());
        assertTrue(loaded.orElseThrow().isActive());
        // 按租户列举
        assertEquals(1, adapter.listDefinitions("tenant-1").size());
        assertEquals(0, adapter.listDefinitions("tenant-2").size());
    }

    @Test
    void shouldRoundTripTeamRunWithNodeRuns() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        AgentTeamRun run = AgentTeamRun.start("teamrun-1", definition(), "user-1", "目标", "run-1",
                List.of(AgentTeamNodeRun.pending("node-1", "a", "agent-a", "指令")), now);
        run = run.withNodeRun(AgentTeamNodeRun.pending("node-1", "a", "agent-a", "指令")
                .running(now)
                .withDispatch("handoff-1", "child-1")
                .succeed("输出摘要", now));
        run = run.succeed("团队汇总", now.plusSeconds(5));

        adapter.saveTeamRun(run);
        Optional<AgentTeamRun> loaded = adapter.findTeamRunById("teamrun-1");

        assertTrue(loaded.isPresent());
        assertEquals(AgentTeamRunStatus.SUCCEEDED, loaded.orElseThrow().status());
        assertEquals("团队汇总", loaded.orElseThrow().summary());
        assertEquals("run-1", loaded.orElseThrow().parentRunId());
        AgentTeamNodeRun node = loaded.orElseThrow().nodeRuns().get(0);
        assertEquals(AgentTeamNodeStatus.SUCCEEDED, node.status());
        assertEquals("handoff-1", node.handoffId());
        assertEquals("child-1", node.childRunId());
        assertEquals("输出摘要", node.outputSummary());
    }

    private AgentTeamDefinition definition() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        return new AgentTeamDefinition("team-1", "tenant-1", "team", AgentTeamMode.SUPERVISOR, "org", "a",
                List.of(new AgentTeamMember("a", "agent-a", "supervisor", "")), List.of(), true, now, now);
    }
}
