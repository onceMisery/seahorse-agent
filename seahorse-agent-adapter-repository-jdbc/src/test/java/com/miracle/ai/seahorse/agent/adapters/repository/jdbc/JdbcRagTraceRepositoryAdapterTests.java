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

import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRagTraceRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcRagTraceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:rag-trace;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcRagTraceRepositoryAdapter(dataSource);
    }

    @Test
    void shouldRecordAndQueryTraceRun() {
        RagTraceRun run = sampleRun();
        adapter.startRun(run);
        adapter.finishRun(new RagTraceRunFinish("trace-1", "SUCCESS", null, Instant.now(), 123));

        RagTracePage<RagTraceRun> page = adapter.pageRuns(
                new RagTracePageRequest(1, 10, "trace-1", null, null, null));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(RagTraceRun::getStatus).containsExactly("SUCCESS");
        assertThat(adapter.findRun("trace-1")).isPresent();
    }

    @Test
    void shouldRecordAndQueryTraceNode() {
        RagTraceNode node = sampleNode();
        adapter.startNode(node);
        adapter.finishNode(new RagTraceNodeFinish("trace-1", "node-1", "ERROR",
                "failed", Instant.now(), 45));

        assertThat(adapter.listNodes("trace-1")).hasSize(1);
        RagTraceNode actual = adapter.listNodes("trace-1").get(0);
        assertThat(actual.getStatus()).isEqualTo("ERROR");
        assertThat(actual.getErrorMessage()).isEqualTo("failed");
    }

    @Test
    void shouldSoftDeleteExpiredRunsAndNodes() {
        RagTraceRun oldRun = sampleRun();
        oldRun.setTraceId("old-trace");
        oldRun.setStartTime(Instant.now().minusSeconds(3600));
        adapter.startRun(oldRun);
        RagTraceNode oldNode = sampleNode();
        oldNode.setTraceId("old-trace");
        adapter.startNode(oldNode);

        RagTraceRun freshRun = sampleRun();
        freshRun.setTraceId("fresh-trace");
        freshRun.setStartTime(Instant.now());
        adapter.startRun(freshRun);

        int deleted = adapter.deleteRunsBefore(Instant.now().minusSeconds(60), 10);

        assertThat(deleted).isEqualTo(1);
        assertThat(adapter.findRun("old-trace")).isEmpty();
        assertThat(adapter.listNodes("old-trace")).isEmpty();
        assertThat(adapter.findRun("fresh-trace")).isPresent();
    }

    private RagTraceRun sampleRun() {
        RagTraceRun run = new RagTraceRun();
        run.setTraceId("trace-1");
        run.setTraceName("rag-stream-chat");
        run.setEntryMethod("chat");
        run.setConversationId("conv-1");
        run.setTaskId("task-1");
        run.setUserId("user-1");
        run.setStatus("RUNNING");
        run.setStartTime(Instant.now());
        return run;
    }

    private RagTraceNode sampleNode() {
        RagTraceNode node = new RagTraceNode();
        node.setTraceId("trace-1");
        node.setNodeId("node-1");
        node.setDepth(0);
        node.setNodeType("RETRIEVE");
        node.setNodeName("retrieval-engine");
        node.setClassName("KernelRetrievalEngine");
        node.setMethodName("retrieve");
        node.setStatus("RUNNING");
        node.setStartTime(Instant.now());
        return node;
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_rag_trace_node");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_rag_trace_run");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_user");
        jdbcTemplate.execute("""
                CREATE TABLE t_user (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(64),
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_rag_trace_run (
                    id BIGINT PRIMARY KEY,
                    trace_id BIGINT NOT NULL,
                    trace_name VARCHAR(128),
                    entry_method VARCHAR(256),
                    conversation_id BIGINT,
                    task_id BIGINT,
                    user_id BIGINT,
                    status VARCHAR(16) NOT NULL,
                    error_message VARCHAR(1000),
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    duration_ms BIGINT,
                    extra_data TEXT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_rag_trace_node (
                    id BIGINT PRIMARY KEY,
                    trace_id BIGINT NOT NULL,
                    node_id BIGINT NOT NULL,
                    parent_node_id BIGINT,
                    depth INTEGER,
                    node_type VARCHAR(16),
                    node_name VARCHAR(128),
                    class_name VARCHAR(256),
                    method_name VARCHAR(128),
                    status VARCHAR(16) NOT NULL,
                    error_message VARCHAR(1000),
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    duration_ms BIGINT,
                    extra_data TEXT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.update("INSERT INTO t_user (id, username, deleted) VALUES (?, 'alice', 0)",
                JdbcMemorySupport.toLongId("user-1"));
    }
}
