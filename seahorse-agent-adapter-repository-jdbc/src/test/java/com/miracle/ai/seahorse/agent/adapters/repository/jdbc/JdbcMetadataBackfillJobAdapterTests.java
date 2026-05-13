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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataBackfillJobAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcTemplate jdbcTemplate;
    private JdbcMetadataGovernanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-backfill-job;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcMetadataGovernanceRepositoryAdapter(dataSource, objectMapper);
    }

    @Test
    void shouldPageBackfillJobsByKnowledgeBaseAndStatus() {
        adapter.create(job("job-1", "tenant-1", "kb-1", MetadataBackfillJobStatus.PENDING));
        adapter.create(job("job-2", "tenant-1", "kb-1", MetadataBackfillJobStatus.COMPLETED));
        adapter.create(job("job-3", "tenant-2", "kb-2", MetadataBackfillJobStatus.PENDING));

        MetadataBackfillJobPage pendingPage = adapter.page(new MetadataBackfillJobQuery(
                "tenant-1", "kb-1", MetadataBackfillJobStatus.PENDING, 1, 10));
        MetadataBackfillJobPage allPage = adapter.page(new MetadataBackfillJobQuery(
                "tenant-1", "kb-1", null, 1, 10));

        assertThat(pendingPage.total()).isEqualTo(1);
        assertThat(pendingPage.records()).extracting(MetadataBackfillJobRecord::jobId).containsExactly("job-1");
        assertThat(allPage.total()).isEqualTo(2);
        assertThat(allPage.records()).extracting(MetadataBackfillJobRecord::knowledgeBaseId)
                .containsOnly("kb-1");
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_extraction_job");
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_extraction_job (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    pipeline_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    current_page BIGINT NOT NULL DEFAULT 1,
                    checkpoint_json VARCHAR(4096),
                    batch_size INTEGER NOT NULL DEFAULT 50,
                    processed_count INTEGER NOT NULL DEFAULT 0,
                    success_count INTEGER NOT NULL DEFAULT 0,
                    failed_count INTEGER NOT NULL DEFAULT 0,
                    skipped_count INTEGER NOT NULL DEFAULT 0,
                    review_count INTEGER NOT NULL DEFAULT 0,
                    quarantine_count INTEGER NOT NULL DEFAULT 0,
                    failure_summary VARCHAR(4096),
                    operator VARCHAR(64),
                    create_time TIMESTAMP NOT NULL,
                    update_time TIMESTAMP NOT NULL
                )
                """);
    }

    private MetadataBackfillJobRecord job(String jobId,
                                          String tenantId,
                                          String knowledgeBaseId,
                                          MetadataBackfillJobStatus status) {
        Instant now = Instant.parse("2026-05-13T10:00:00Z");
        return new MetadataBackfillJobRecord(
                jobId,
                tenantId,
                knowledgeBaseId,
                "pipe-1",
                status,
                1,
                50,
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of("currentPage", 1),
                List.of(),
                "admin",
                now,
                now);
    }
}
