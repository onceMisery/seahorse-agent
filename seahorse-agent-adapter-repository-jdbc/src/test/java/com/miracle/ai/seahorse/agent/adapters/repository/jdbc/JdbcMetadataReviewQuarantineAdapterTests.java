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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRetry;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataReviewQuarantineAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcTemplate jdbcTemplate;
    private JdbcMetadataGovernanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-review-quarantine;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcMetadataGovernanceRepositoryAdapter(dataSource, objectMapper);
    }

    @Test
    void shouldPageAndApplyReviewDecision() {
        insertExtractionResult();
        insertReviewItem("review-1", "PENDING");

        MetadataReviewPage page = adapter.pageReviewItems(
                new MetadataReviewQuery("tenant-1", "kb-1", MetadataReviewStatus.PENDING, 1, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(MetadataReviewRecord::id).containsExactly("review-1");
        assertThat(page.records().get(0).reviewContext()).containsKey("issues");

        MetadataReviewRecord corrected = adapter.applyReviewDecision(new MetadataReviewDecision(
                "review-1",
                MetadataReviewStatus.CORRECTED,
                "auditor",
                "修正部门",
                Map.of("department", "legal")));

        assertThat(corrected.reviewStatus()).isEqualTo(MetadataReviewStatus.CORRECTED);
        assertThat(corrected.correctedMetadata()).containsEntry("department", "legal");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT review_status FROM t_metadata_review_item WHERE id = 'review-1'", String.class))
                .isEqualTo("CORRECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approved_metadata FROM t_metadata_extraction_result WHERE id = 'result-1'", String.class))
                .contains("legal");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approved_by FROM t_metadata_extraction_result WHERE id = 'result-1'", String.class))
                .isEqualTo("auditor");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_metadata_review_audit WHERE review_item_id = 'review-1'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT from_status FROM t_metadata_review_audit WHERE review_item_id = 'review-1'", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_status FROM t_metadata_review_audit WHERE review_item_id = 'review-1'", String.class))
                .isEqualTo("CORRECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT decision_metadata FROM t_metadata_review_audit WHERE review_item_id = 'review-1'",
                String.class)).contains("legal");
    }

    @Test
    void shouldPersistReviewContextWhenEnqueueReviewItem() {
        adapter.enqueue(new MetadataReviewItem(
                "tenant-1",
                "kb-1",
                "doc-context",
                "result-context",
                "METADATA_REVIEW_REQUIRED",
                "字段置信度低",
                Map.of("department", "hr"),
                Map.of("issues", List.of("LOW_CONFIDENCE"), "evidence", "财务部预算说明")));

        String reviewContextJson = jdbcTemplate.queryForObject(
                "SELECT review_context FROM t_metadata_review_item WHERE doc_id = 'doc-context'",
                String.class);

        assertThat(reviewContextJson).contains("LOW_CONFIDENCE");
        assertThat(reviewContextJson).contains("财务部预算说明");
    }

    @Test
    void shouldSyncExtractionTerminalStatusWhenReviewRejectedOrQuarantined() {
        insertExtractionResult("result-rejected");
        insertReviewItem("review-rejected", "PENDING", "result-rejected");
        insertExtractionResult("result-quarantined");
        insertReviewItem("review-quarantined", "PENDING", "result-quarantined");

        MetadataReviewRecord rejected = adapter.applyReviewDecision(new MetadataReviewDecision(
                "review-rejected",
                MetadataReviewStatus.REJECTED,
                "auditor",
                "拒绝低置信字段",
                Map.of()));
        MetadataReviewRecord quarantined = adapter.applyReviewDecision(new MetadataReviewDecision(
                "review-quarantined",
                MetadataReviewStatus.QUARANTINED,
                "auditor",
                "转入隔离区",
                Map.of()));

        assertThat(rejected.reviewStatus()).isEqualTo(MetadataReviewStatus.REJECTED);
        assertThat(quarantined.reviewStatus()).isEqualTo(MetadataReviewStatus.QUARANTINED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_metadata_extraction_result WHERE id = 'result-rejected'", String.class))
                .isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_metadata_extraction_result WHERE id = 'result-quarantined'", String.class))
                .isEqualTo("QUARANTINED");
    }

    @Test
    void shouldPageResolveAndScheduleQuarantineRetry() {
        insertQuarantineItem("q-1", 0, 1);

        MetadataQuarantinePage page = adapter.pageQuarantineItems(
                new MetadataQuarantineQuery("tenant-1", "kb-1", Boolean.FALSE, 1, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(MetadataQuarantineRecord::id).containsExactly("q-1");

        MetadataQuarantineRecord resolved = adapter.resolveQuarantineItem(
                new MetadataQuarantineResolution("q-1", "auditor"));

        assertThat(resolved.resolved()).isTrue();
        assertThat(resolved.resolvedBy()).isEqualTo("auditor");

        MetadataQuarantineRecord retried = adapter.scheduleQuarantineRetry(new MetadataQuarantineRetry(
                "q-1",
                "auditor",
                Instant.parse("2026-05-13T10:00:00Z")));

        assertThat(retried.resolved()).isFalse();
        assertThat(retried.retryCount()).isEqualTo(2);
        assertThat(retried.nextRetryTime()).isEqualTo(Instant.parse("2026-05-13T10:00:00Z"));
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_quarantine_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_review_audit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_review_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_extraction_result");
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_extraction_result (
                    id VARCHAR(64) PRIMARY KEY,
                    status VARCHAR(32) NOT NULL,
                    approved_metadata VARCHAR(4096),
                    approved_by VARCHAR(64),
                    approved_time TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_review_item (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64) NOT NULL,
                    result_id VARCHAR(64),
                    review_status VARCHAR(32) NOT NULL,
                    priority INTEGER NOT NULL DEFAULT 0,
                    reason_code VARCHAR(64),
                    reason_message VARCHAR(512),
                    suggested_metadata VARCHAR(4096),
                    review_context VARCHAR(4096),
                    corrected_metadata VARCHAR(4096),
                    reviewer_id VARCHAR(64),
                    review_comment VARCHAR(1024),
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_review_audit (
                    id VARCHAR(64) PRIMARY KEY,
                    review_item_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64) NOT NULL,
                    result_id VARCHAR(64),
                    from_status VARCHAR(32),
                    to_status VARCHAR(32) NOT NULL,
                    reviewer_id VARCHAR(64),
                    review_comment VARCHAR(1024),
                    decision_metadata VARCHAR(4096),
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_quarantine_item (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64),
                    job_id VARCHAR(64),
                    stage VARCHAR(32) NOT NULL,
                    reason_code VARCHAR(64),
                    reason_message VARCHAR(512),
                    source_snapshot VARCHAR(4096),
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    next_retry_time TIMESTAMP,
                    resolved SMALLINT NOT NULL DEFAULT 0,
                    resolved_by VARCHAR(64),
                    resolved_time TIMESTAMP,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void insertExtractionResult() {
        insertExtractionResult("result-1");
    }

    private void insertExtractionResult(String resultId) {
        jdbcTemplate.update("INSERT INTO t_metadata_extraction_result(id, status) VALUES (?, 'REVIEW_REQUIRED')",
                resultId);
    }

    private void insertReviewItem(String id, String status) {
        insertReviewItem(id, status, "result-1");
    }

    private void insertReviewItem(String id, String status, String resultId) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_review_item(
                    id, tenant_id, kb_id, doc_id, result_id, review_status, priority,
                    reason_code, reason_message, suggested_metadata, review_context
                ) VALUES (?, 'tenant-1', 'kb-1', 'doc-1', ?, ?, 10,
                    'LOW_CONFIDENCE', '字段置信度低', ?, ?)
                """, id, resultId, status, "{\"department\":\"hr\"}",
                "{\"issues\":[\"LOW_CONFIDENCE\"]}");
    }

    private void insertQuarantineItem(String id, int resolved, int retryCount) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_quarantine_item(
                    id, tenant_id, kb_id, doc_id, job_id, stage, reason_code, reason_message,
                    source_snapshot, retry_count, resolved
                ) VALUES (?, 'tenant-1', 'kb-1', 'doc-1', 'job-1', 'VALIDATE',
                    'SCHEMA_MISSING', '缺少 Schema', ?, ?, ?)
                """, id, "{\"source\":\"test\"}", retryCount, resolved);
    }
}
