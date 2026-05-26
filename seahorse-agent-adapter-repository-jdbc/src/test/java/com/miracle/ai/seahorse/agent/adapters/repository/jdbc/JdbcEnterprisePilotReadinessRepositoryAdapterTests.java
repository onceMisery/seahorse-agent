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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEnterprisePilotReadinessRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveAndFindLatestReadinessReportWithAllChecks() {
        DriverManagerDataSource dataSource = dataSource("enterprise-readiness-latest");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createReadinessSchema(jdbcTemplate);
        JdbcEnterprisePilotReadinessRepositoryAdapter adapter =
                new JdbcEnterprisePilotReadinessRepositoryAdapter(dataSource);
        EnterprisePilotReadinessReport warnReport = report(
                "report-1",
                EnterprisePilotReadinessStatus.WARN,
                NOW);
        EnterprisePilotReadinessReport failReport = report(
                "report-2",
                EnterprisePilotReadinessStatus.FAIL,
                NOW.plusSeconds(60));

        adapter.save(warnReport);
        adapter.save(failReport);

        EnterprisePilotReadinessReport latest = adapter.findLatest(
                        "tenant-a",
                        "agent-1",
                        "version-1")
                .orElseThrow();

        assertThat(latest.reportId()).isEqualTo("report-2");
        assertThat(latest.status()).isEqualTo(EnterprisePilotReadinessStatus.FAIL);
        assertThat(latest.checkResults()).hasSize(9);
        assertThat(latest.result(EnterprisePilotReadinessCheckCode.EVAL).orElseThrow().reasonCode())
                .isEqualTo(EnterprisePilotReadinessReasonCode.EVAL_FAILED);
        assertThat(latest.result(EnterprisePilotReadinessCheckCode.EVAL).orElseThrow().evidenceRef())
                .isEqualTo("evidence:EVAL:report-2");
        assertThat(adapter.findLatest("tenant-a", "agent-1", "missing")).isEmpty();
    }

    static void createReadinessSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_enterprise_pilot_readiness_report (
                    report_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64) NOT NULL,
                    version_id VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    check_results_json CLOB NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_enterprise_pilot_readiness_latest
                ON sa_enterprise_pilot_readiness_report(tenant_id, agent_id, version_id, created_at DESC, report_id DESC)
                """);
    }

    private static EnterprisePilotReadinessReport report(String reportId,
                                                         EnterprisePilotReadinessStatus evalStatus,
                                                         Instant createdAt) {
        List<EnterprisePilotReadinessCheckResult> results =
                EnterprisePilotReadinessCheckCode.all().stream()
                        .map(code -> result(reportId, code, evalStatus, createdAt))
                        .toList();
        return new EnterprisePilotReadinessReport(
                reportId,
                "tenant-a",
                "agent-1",
                "version-1",
                null,
                results,
                createdAt);
    }

    private static EnterprisePilotReadinessCheckResult result(String reportId,
                                                              EnterprisePilotReadinessCheckCode code,
                                                              EnterprisePilotReadinessStatus evalStatus,
                                                              Instant checkedAt) {
        EnterprisePilotReadinessStatus status = code == EnterprisePilotReadinessCheckCode.EVAL
                ? evalStatus
                : EnterprisePilotReadinessStatus.PASS;
        EnterprisePilotReadinessReasonCode reasonCode = status == EnterprisePilotReadinessStatus.PASS
                ? EnterprisePilotReadinessReasonCode.READY
                : EnterprisePilotReadinessReasonCode.EVAL_FAILED;
        return new EnterprisePilotReadinessCheckResult(
                code,
                status,
                reasonCode,
                "evidence:" + code.name() + ":" + reportId,
                code.name() + " check",
                checkedAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }
}
