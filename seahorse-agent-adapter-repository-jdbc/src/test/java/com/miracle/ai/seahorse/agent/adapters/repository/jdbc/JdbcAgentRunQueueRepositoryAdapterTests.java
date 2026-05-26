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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentRunQueueRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldFindRunnableRunsWithoutActiveLease() {
        DriverManagerDataSource dataSource = dataSource("agent-run-queue-active-lease");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcAgentRunRepositoryAdapterTests.createRunSchema(jdbcTemplate);
        JdbcAgentRunLeaseRepositoryAdapterTests.createRunLeaseSchema(jdbcTemplate);
        JdbcAgentRunRepositoryAdapter runRepository = new JdbcAgentRunRepositoryAdapter(dataSource);
        JdbcAgentRunLeaseRepositoryAdapter leaseRepository = new JdbcAgentRunLeaseRepositoryAdapter(dataSource);
        JdbcAgentRunQueueRepositoryAdapter adapter = new JdbcAgentRunQueueRepositoryAdapter(dataSource);
        runRepository.createRun(run("run-active-lease", AgentRunStatus.RUNNING, "tenant-1", NOW.minusSeconds(20)));
        runRepository.createRun(run("run-free", AgentRunStatus.CREATED, "tenant-1", NOW.minusSeconds(10)));
        runRepository.createRun(run("run-other-tenant", AgentRunStatus.RUNNING, "tenant-2", NOW.minusSeconds(30)));
        runRepository.createRun(run("run-failed", AgentRunStatus.FAILED, "tenant-1", NOW.minusSeconds(40)));
        leaseRepository.acquire("run-active-lease", "worker-a", NOW.plusSeconds(30), NOW.minusSeconds(1));

        List<AgentRun> runs = adapter.findRunnable("tenant-1", 10, NOW);

        assertThat(runs).extracting(AgentRun::runId).containsExactly("run-free");
    }

    @Test
    void shouldFindRunnableRunsWithExpiredLeaseInStartedOrder() {
        DriverManagerDataSource dataSource = dataSource("agent-run-queue-expired-lease");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcAgentRunRepositoryAdapterTests.createRunSchema(jdbcTemplate);
        JdbcAgentRunLeaseRepositoryAdapterTests.createRunLeaseSchema(jdbcTemplate);
        JdbcAgentRunRepositoryAdapter runRepository = new JdbcAgentRunRepositoryAdapter(dataSource);
        JdbcAgentRunLeaseRepositoryAdapter leaseRepository = new JdbcAgentRunLeaseRepositoryAdapter(dataSource);
        JdbcAgentRunQueueRepositoryAdapter adapter = new JdbcAgentRunQueueRepositoryAdapter(dataSource);
        runRepository.createRun(run("run-expired-lease", AgentRunStatus.RUNNING, "tenant-1", NOW.minusSeconds(30)));
        runRepository.createRun(run("run-retrying", AgentRunStatus.RETRYING, "tenant-1", NOW.minusSeconds(20)));
        runRepository.createRun(run("run-created", AgentRunStatus.CREATED, "tenant-1", NOW.minusSeconds(10)));
        leaseRepository.acquire("run-expired-lease", "worker-a", NOW.minusSeconds(1), NOW.minusSeconds(60));

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
}
