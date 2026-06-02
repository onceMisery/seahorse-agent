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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillOperationsOverview;
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
        adapter.create(job("job-1", "tenant-1", 1L, MetadataBackfillJobStatus.PENDING));
        adapter.create(job("job-2", "tenant-1", 1L, MetadataBackfillJobStatus.COMPLETED));
        adapter.create(job("job-3", "tenant-2", 2L, MetadataBackfillJobStatus.PENDING));

        MetadataBackfillJobPage pendingPage = adapter.page(new MetadataBackfillJobQuery(
                "tenant-1", "kb-1", MetadataBackfillJobStatus.PENDING, 1, 10));
        MetadataBackfillJobPage allPage = adapter.page(new MetadataBackfillJobQuery(
                "tenant-1", "kb-1", null, 1, 10));

        assertThat(pendingPage.total()).isEqualTo(1);
        assertThat(pendingPage.records()).extracting(MetadataBackfillJobRecord::jobId).containsExactly("job-1");
        assertThat(allPage.total()).isEqualTo(2);
        assertThat(allPage.records()).extracting(MetadataBackfillJobRecord::knowledgeBaseId)
                .containsOnly(1L);
    }

    @Test
    void shouldFilterBackfillJobsByGovernanceDetails() {
        adapter.create(job("job-1", "tenant-1", 1L, MetadataBackfillJobStatus.PAUSED,
                Map.of(
                        "currentPage", 2,
                        "documentIds", List.of("doc-1"),
                        "pauseReason", "SCHEMA_MISSING",
                        "reExtract", true),
                List.of("doc-1: metadata schema missing"),
                "auditor"));
        adapter.create(job("job-2", "tenant-1", 1L, MetadataBackfillJobStatus.COMPLETED,
                Map.of("currentPage", 1), List.of(), "admin"));

        MetadataBackfillJobPage page = adapter.page(new MetadataBackfillJobQuery(
                "tenant-1", "kb-1", null, "pipe-1", "auditor", "doc-1",
                "SCHEMA_MISSING", "schema", true, true, 1, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(MetadataBackfillJobRecord::jobId).containsExactly("job-1");
    }

    @Test
    void shouldBuildBackfillOperationsOverview() {
        adapter.create(new MetadataBackfillJobRecord(
                "job-1", "tenant-1", 1L, "pipe-1", MetadataBackfillJobStatus.PENDING,
                1, 50, 5, 4, 1, 0, 2, 1,
                Map.of(
                        "currentPage", 1,
                        "schemaCompensation", true,
                        "documentIds", List.of("doc-1", "doc-2"),
                        "pauseReason", "SCHEMA_MISSING"),
                List.of("doc-2: boom"),
                "ops",
                Instant.parse("2026-05-13T10:00:00Z"),
                Instant.parse("2026-05-13T10:10:00Z")));
        adapter.create(new MetadataBackfillJobRecord(
                "job-2", "tenant-1", 1L, "pipe-1", MetadataBackfillJobStatus.PAUSED,
                1, 50, 3, 2, 0, 1, 1, 0,
                Map.of(
                        "currentPage", 1,
                        "reExtract", true,
                        "pauseReason", "MANUAL"),
                List.of(),
                "ops",
                Instant.parse("2026-05-13T10:20:00Z"),
                Instant.parse("2026-05-13T10:30:00Z")));
        adapter.create(new MetadataBackfillJobRecord(
                "job-3", "tenant-1", 1L, "pipe-1", MetadataBackfillJobStatus.COMPLETED,
                1, 50, 8, 8, 0, 0, 0, 0,
                Map.of(
                        "currentPage", 1,
                        "schemaCompensation", true,
                        "documentIds", List.of("doc-9")),
                List.of(),
                "ops",
                Instant.parse("2026-05-13T10:40:00Z"),
                Instant.parse("2026-05-13T10:50:00Z")));
        adapter.create(job("job-4", "tenant-2", 2L, MetadataBackfillJobStatus.PENDING));

        jdbcTemplate.update("""
                INSERT INTO t_metadata_review_item(id, tenant_id, kb_id, review_status)
                VALUES (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?)
                """,
                "review-1", "tenant-1", "kb-1", "PENDING",
                "review-2", "tenant-1", "kb-1", "PENDING",
                "review-3", "tenant-1", "kb-1", "RE_EXTRACTING");
        jdbcTemplate.update("""
                INSERT INTO t_metadata_quarantine_item(id, tenant_id, kb_id, reason_code, resolved)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)
                """,
                "q-1", "tenant-1", "kb-1", "SCHEMA_MISSING", 0,
                "q-2", "tenant-1", "kb-1", "LOW_CONFIDENCE", 0,
                "q-3", "tenant-1", "kb-1", "SCHEMA_MISSING", 1);

        MetadataBackfillOperationsOverview overview = adapter.overview("tenant-1", "kb-1");

        assertThat(overview.totalJobs()).isEqualTo(3);
        assertThat(overview.processedDocuments()).isEqualTo(16);
        assertThat(overview.succeededDocuments()).isEqualTo(14);
        assertThat(overview.failedDocuments()).isEqualTo(1);
        assertThat(overview.skippedDocuments()).isEqualTo(1);
        assertThat(overview.reviewDocuments()).isEqualTo(3);
        assertThat(overview.quarantineDocuments()).isEqualTo(1);
        assertThat(overview.pendingReviewItems()).isEqualTo(2);
        assertThat(overview.reExtractingReviewItems()).isEqualTo(1);
        assertThat(overview.pendingQuarantineItems()).isEqualTo(2);
        assertThat(overview.resolvedQuarantineItems()).isEqualTo(1);
        assertThat(overview.pendingSchemaCompensationJobs()).isEqualTo(1);
        assertThat(overview.pendingSchemaCompensationDocuments()).isEqualTo(2);
        assertThat(overview.statusCounts())
                .extracting(item -> item.key() + ":" + item.count())
                .contains("PENDING:1", "PAUSED:1", "COMPLETED:1");
        assertThat(overview.failureReasons())
                .extracting(item -> item.key() + ":" + item.count())
                .containsExactly("SCHEMA_MISSING:2", "LOW_CONFIDENCE:1");
        assertThat(overview.pauseReasons())
                .extracting(item -> item.key() + ":" + item.count())
                .containsExactlyInAnyOrder("MANUAL:1", "SCHEMA_MISSING:1");
        assertThat(overview.latestReExtractJob()).extracting(MetadataBackfillJobRecord::jobId).isEqualTo("job-2");
        assertThat(overview.latestSchemaCompensationJob())
                .extracting(MetadataBackfillJobRecord::jobId)
                .isEqualTo("job-3");
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_review_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_quarantine_item");
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
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_review_item (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    review_status VARCHAR(32) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_quarantine_item (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    reason_code VARCHAR(64),
                    resolved INTEGER NOT NULL DEFAULT 0
                )
                """);
    }

    private MetadataBackfillJobRecord job(String jobId,
                                          String tenantId,
                                          Long knowledgeBaseId,
                                          MetadataBackfillJobStatus status) {
        return job(jobId, tenantId, knowledgeBaseId, status, Map.of("currentPage", 1), List.of(), "admin");
    }

    private MetadataBackfillJobRecord job(String jobId,
                                          String tenantId,
                                          Long knowledgeBaseId,
                                          MetadataBackfillJobStatus status,
                                          Map<String, Object> checkpoint,
                                          List<String> failures,
                                          String operator) {
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
                checkpoint,
                failures,
                operator,
                now,
                now);
    }
}
