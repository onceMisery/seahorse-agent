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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunLease;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentRunLeaseRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void shouldAcquireReleaseAndFindLease() {
        DriverManagerDataSource dataSource = dataSource("agent-run-lease-basic");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createRunLeaseSchema(jdbcTemplate);
        JdbcAgentRunLeaseRepositoryAdapter adapter = new JdbcAgentRunLeaseRepositoryAdapter(dataSource);

        boolean acquired = adapter.acquire("run-1", "worker-a", NOW.plusSeconds(30), NOW);
        Optional<AgentRunLease> lease = adapter.findByRunId("run-1");
        boolean released = adapter.release("run-1", "worker-a");

        assertThat(acquired).isTrue();
        assertThat(lease).contains(new AgentRunLease("run-1", "worker-a", NOW.plusSeconds(30), NOW));
        assertThat(released).isTrue();
        assertThat(adapter.findByRunId("run-1")).isEmpty();
    }

    @Test
    void shouldPreventSecondWorkerUntilLeaseExpires() {
        DriverManagerDataSource dataSource = dataSource("agent-run-lease-takeover");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createRunLeaseSchema(jdbcTemplate);
        JdbcAgentRunLeaseRepositoryAdapter adapter = new JdbcAgentRunLeaseRepositoryAdapter(dataSource);

        boolean first = adapter.acquire("run-1", "worker-a", NOW.plusSeconds(30), NOW);
        boolean blocked = adapter.acquire("run-1", "worker-b", NOW.plusSeconds(60), NOW.plusSeconds(10));
        boolean takeover = adapter.acquire("run-1", "worker-b", NOW.plusSeconds(90), NOW.plusSeconds(31));
        AgentRunLease lease = adapter.findByRunId("run-1").orElseThrow();

        assertThat(first).isTrue();
        assertThat(blocked).isFalse();
        assertThat(takeover).isTrue();
        assertThat(lease.workerId()).isEqualTo("worker-b");
        assertThat(lease.leaseUntil()).isEqualTo(NOW.plusSeconds(90));
        assertThat(lease.heartbeatAt()).isEqualTo(NOW.plusSeconds(31));
    }

    @Test
    void shouldHeartbeatOnlyForCurrentWorkerBeforeExpiry() {
        DriverManagerDataSource dataSource = dataSource("agent-run-lease-heartbeat");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createRunLeaseSchema(jdbcTemplate);
        JdbcAgentRunLeaseRepositoryAdapter adapter = new JdbcAgentRunLeaseRepositoryAdapter(dataSource);

        adapter.acquire("run-1", "worker-a", NOW.plusSeconds(30), NOW);

        boolean wrongWorker = adapter.heartbeat("run-1", "worker-b", NOW.plusSeconds(60), NOW.plusSeconds(10));
        boolean ownerHeartbeat = adapter.heartbeat("run-1", "worker-a", NOW.plusSeconds(90), NOW.plusSeconds(20));
        boolean expiredHeartbeat = adapter.heartbeat("run-1", "worker-a", NOW.plusSeconds(120), NOW.plusSeconds(91));
        AgentRunLease lease = adapter.findByRunId("run-1").orElseThrow();

        assertThat(wrongWorker).isFalse();
        assertThat(ownerHeartbeat).isTrue();
        assertThat(expiredHeartbeat).isFalse();
        assertThat(lease.workerId()).isEqualTo("worker-a");
        assertThat(lease.leaseUntil()).isEqualTo(NOW.plusSeconds(90));
        assertThat(lease.heartbeatAt()).isEqualTo(NOW.plusSeconds(20));
    }

    @Test
    void shouldFindRunnableRunsWithoutActiveLease() {
        DriverManagerDataSource dataSource = dataSource("agent-run-queue-active-lease");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcAgentRunRepositoryAdapterTests.createRunSchema(jdbcTemplate);
        createRunLeaseSchema(jdbcTemplate);
        JdbcAgentRunRepositoryAdapter runRepository = new JdbcAgentRunRepositoryAdapter(dataSource);
        JdbcAgentRunLeaseRepositoryAdapter adapter = new JdbcAgentRunLeaseRepositoryAdapter(dataSource);
        runRepository.createRun(run("run-active-lease", AgentRunStatus.RUNNING, "tenant-1", NOW.minusSeconds(20)));
        runRepository.createRun(run("run-free", AgentRunStatus.CREATED, "tenant-1", NOW.minusSeconds(10)));
        runRepository.createRun(run("run-other-tenant", AgentRunStatus.RUNNING, "tenant-2", NOW.minusSeconds(30)));
        runRepository.createRun(run("run-failed", AgentRunStatus.FAILED, "tenant-1", NOW.minusSeconds(40)));
        adapter.acquire("run-active-lease", "worker-a", NOW.plusSeconds(30), NOW.minusSeconds(1));

        List<AgentRun> runs = adapter.findRunnable("tenant-1", 10, NOW);

        assertThat(runs).extracting(AgentRun::runId).containsExactly("run-free");
    }

    @Test
    void shouldFindRunnableRunsWithExpiredLeaseInStartedOrder() {
        DriverManagerDataSource dataSource = dataSource("agent-run-queue-expired-lease");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcAgentRunRepositoryAdapterTests.createRunSchema(jdbcTemplate);
        createRunLeaseSchema(jdbcTemplate);
        JdbcAgentRunRepositoryAdapter runRepository = new JdbcAgentRunRepositoryAdapter(dataSource);
        JdbcAgentRunLeaseRepositoryAdapter adapter = new JdbcAgentRunLeaseRepositoryAdapter(dataSource);
        runRepository.createRun(run("run-expired-lease", AgentRunStatus.RUNNING, "tenant-1", NOW.minusSeconds(30)));
        runRepository.createRun(run("run-retrying", AgentRunStatus.RETRYING, "tenant-1", NOW.minusSeconds(20)));
        runRepository.createRun(run("run-created", AgentRunStatus.CREATED, "tenant-1", NOW.minusSeconds(10)));
        adapter.acquire("run-expired-lease", "worker-a", NOW.minusSeconds(1), NOW.minusSeconds(60));

        List<AgentRun> runs = adapter.findRunnable("tenant-1", 2, NOW);

        assertThat(runs).extracting(AgentRun::runId)
                .containsExactly("run-expired-lease", "run-retrying");
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private AgentRun run(String runId, AgentRunStatus status, String tenantId, Instant startedAt) {
        return new AgentRun(
                runId,
                "agent-1",
                "version-1",
                tenantId,
                "user-1",
                null,
                AgentRunTriggerType.API,
                "summary",
                status,
                null,
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                startedAt,
                status.isFinished() ? startedAt.plusSeconds(1) : null);
    }

    static void createRunLeaseSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_run_lease (
                    run_id VARCHAR(64) PRIMARY KEY,
                    worker_id VARCHAR(128) NOT NULL,
                    lease_until TIMESTAMP NOT NULL,
                    heartbeat_at TIMESTAMP NOT NULL
                )
                """);
    }
}
