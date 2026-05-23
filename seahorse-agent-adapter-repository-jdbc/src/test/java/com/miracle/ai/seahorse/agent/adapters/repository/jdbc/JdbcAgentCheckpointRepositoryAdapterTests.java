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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentCheckpointRepositoryAdapterTests {

    private static final Instant CREATED_AT = Instant.parse("2026-05-23T00:00:00Z");

    @Test
    void shouldSaveListAndFindLatestCheckpointForRun() {
        DriverManagerDataSource dataSource = dataSource("agent-checkpoint");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createCheckpointSchema(jdbcTemplate);
        JdbcAgentCheckpointRepositoryAdapter adapter = new JdbcAgentCheckpointRepositoryAdapter(dataSource);

        AgentCheckpoint beforeTool = checkpoint(
                "checkpoint-1",
                "run-1",
                1L,
                AgentCheckpointType.BEFORE_TOOL,
                "{\"phase\":\"before\"}",
                "{\"messages\":2}",
                null);
        AgentCheckpoint waitingApproval = checkpoint(
                "checkpoint-2",
                "run-1",
                2L,
                AgentCheckpointType.WAITING_APPROVAL,
                "{\"phase\":\"waiting\"}",
                "{\"messages\":3}",
                "{\"toolId\":\"memory-forget\",\"toolCallId\":\"call-1\",\"arguments\":{\"memoryId\":\"mem-1\"},"
                        + "\"resourceRefs\":{},\"idempotencyKey\":\"run-1:call-1\","
                        + "\"agentId\":\"agent-1\",\"versionId\":\"version-1\",\"runId\":\"run-1\","
                        + "\"tenantId\":\"tenant-1\",\"userId\":\"user-1\"}");
        AgentCheckpoint otherRun = checkpoint(
                "checkpoint-3",
                "run-2",
                1L,
                AgentCheckpointType.MODEL_TURN,
                "{\"phase\":\"other\"}",
                null,
                null);

        adapter.save(beforeTool);
        adapter.save(waitingApproval);
        adapter.save(otherRun);

        Optional<AgentCheckpoint> latest = adapter.findLatestByRunId("run-1");
        List<AgentCheckpoint> checkpoints = adapter.listByRunId("run-1");

        assertThat(latest).contains(waitingApproval);
        assertThat(checkpoints).containsExactly(beforeTool, waitingApproval);
        assertThat(latest.orElseThrow().pendingToolCallJson()).contains("\"idempotencyKey\":\"run-1:call-1\"");
    }

    @Test
    void shouldPersistAllCheckpointColumns() {
        DriverManagerDataSource dataSource = dataSource("agent-checkpoint-columns");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createCheckpointSchema(jdbcTemplate);
        JdbcAgentCheckpointRepositoryAdapter adapter = new JdbcAgentCheckpointRepositoryAdapter(dataSource);

        adapter.save(checkpoint(
                "checkpoint-1",
                "run-1",
                7L,
                AgentCheckpointType.AFTER_TOOL,
                "{\"status\":\"ok\"}",
                "{\"messages\":4}",
                "{\"toolId\":\"search\"}"));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT checkpoint_id, run_id, step_id, sequence_no, checkpoint_type, state_json,
                       message_history_json, context_pack_id, pending_tool_call_json, created_at
                FROM sa_agent_checkpoint
                WHERE checkpoint_id = ?
                """, "checkpoint-1");

        assertThat(row.get("RUN_ID")).isEqualTo("run-1");
        assertThat(row.get("STEP_ID")).isEqualTo("step-1");
        assertThat(row.get("SEQUENCE_NO")).isEqualTo(7L);
        assertThat(row.get("CHECKPOINT_TYPE")).isEqualTo(AgentCheckpointType.AFTER_TOOL.name());
        assertThat(row.get("STATE_JSON")).isEqualTo("{\"status\":\"ok\"}");
        assertThat(row.get("MESSAGE_HISTORY_JSON")).isEqualTo("{\"messages\":4}");
        assertThat(row.get("CONTEXT_PACK_ID")).isEqualTo("context-pack-1");
        assertThat(row.get("PENDING_TOOL_CALL_JSON")).isEqualTo("{\"toolId\":\"search\"}");
        assertThat(row.get("CREATED_AT")).isNotNull();
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createCheckpointSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_checkpoint (
                    checkpoint_id VARCHAR(64) PRIMARY KEY,
                    run_id VARCHAR(64) NOT NULL,
                    step_id VARCHAR(64),
                    sequence_no BIGINT NOT NULL,
                    checkpoint_type VARCHAR(32) NOT NULL,
                    state_json CLOB NOT NULL,
                    message_history_json CLOB,
                    context_pack_id VARCHAR(64),
                    pending_tool_call_json CLOB,
                    created_at TIMESTAMP NOT NULL,
                    UNIQUE(run_id, sequence_no)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_checkpoint_run
                ON sa_agent_checkpoint(run_id, sequence_no)
                """);
    }

    private AgentCheckpoint checkpoint(String checkpointId,
                                       String runId,
                                       long sequenceNo,
                                       AgentCheckpointType checkpointType,
                                       String stateJson,
                                       String messageHistoryJson,
                                       String pendingToolCallJson) {
        return new AgentCheckpoint(
                checkpointId,
                runId,
                "step-1",
                sequenceNo,
                checkpointType,
                stateJson,
                messageHistoryJson,
                "context-pack-1",
                pendingToolCallJson,
                CREATED_AT.plusSeconds(sequenceNo));
    }
}
