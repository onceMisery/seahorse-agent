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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcProductionGateRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveAndReturnLatestProductionGateReport() {
        DriverManagerDataSource dataSource = dataSource("production-gate");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createGateSchema(jdbcTemplate);
        JdbcProductionGateRepositoryAdapter adapter = new JdbcProductionGateRepositoryAdapter(dataSource);
        ProductionGateReport first = report("gate-1", ProductionGateStatus.WARN, NOW);
        ProductionGateReport second = report("gate-2", ProductionGateStatus.FAIL, NOW.plusSeconds(60));

        adapter.save(first);
        adapter.save(second);

        ProductionGateReport latest = adapter.latest("agent-1").orElseThrow();
        assertThat(latest.reportId()).isEqualTo("gate-2");
        assertThat(latest.status()).isEqualTo(ProductionGateStatus.FAIL);
        assertThat(latest.item(ProductionGateCheckCode.EVAL_PASSING).orElseThrow().status())
                .isEqualTo(ProductionGateStatus.WARN);
        assertThat(adapter.latest("missing")).isEmpty();
    }

    static void createGateSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_production_gate_report (
                    report_id VARCHAR(64) PRIMARY KEY,
                    agent_id VARCHAR(64) NOT NULL,
                    version_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    result_json CLOB NOT NULL,
                    checked_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_production_gate_report_agent
                ON sa_production_gate_report(agent_id, checked_at)
                """);
    }

    private static ProductionGateReport report(String reportId,
                                               ProductionGateStatus status,
                                               Instant checkedAt) {
        return new ProductionGateReport(
                reportId,
                "agent-1",
                "version-1",
                status,
                List.of(
                        ProductionGateCheckItem.pass(
                                ProductionGateCheckCode.AUDIT_LEDGER_ENABLED,
                                "Audit ledger repository is configured."),
                        ProductionGateCheckItem.warn(
                                ProductionGateCheckCode.EVAL_PASSING,
                                "Evaluation platform is not connected yet.")),
                checkedAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }
}
