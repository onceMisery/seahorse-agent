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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataExtractionResultManagementAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcMetadataGovernanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-extraction-result-management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcMetadataGovernanceRepositoryAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldPageAndFindExtractionResults() {
        insertResult("result-1", "tenant-1", "kb-1", "doc-1", "job-1", "ACCEPTED");
        insertResult("result-2", "tenant-1", "kb-1", "doc-2", "job-1", "REVIEW_REQUIRED");

        MetadataExtractionResultPage page = adapter.pageExtractionResults(new MetadataExtractionResultQuery(
                "tenant-1", "kb-1", "doc-1", "job-1", "ACCEPTED", 1, 10));
        MetadataExtractionResultRecord detail = adapter.findExtractionResult("result-1").orElseThrow();

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(MetadataExtractionResultRecord::id).containsExactly("result-1");
        assertThat(detail.normalizedMetadata()).containsEntry("department", "HR");
        assertThat(detail.rawCandidates()).hasSize(1);
        assertThat(detail.fieldQuality()).hasSize(1);
        assertThat(detail.validationIssues()).hasSize(1);
        assertThat(detail.approvedMetadata()).containsEntry("department", "HR");
    }

    @Test
    void shouldFilterExtractionResultsBySchemaAndExtractorVersion() {
        insertResult("result-v1", "tenant-1", "kb-1", "doc-1", "job-1", "ACCEPTED", 1, "extractor-v1");
        insertResult("result-v2", "tenant-1", "kb-1", "doc-2", "job-1", "ACCEPTED", 2, "extractor-v2");
        insertResult("result-v3", "tenant-1", "kb-1", "doc-3", "job-1", "ACCEPTED", 2, "extractor-v3");

        MetadataExtractionResultPage page = adapter.pageExtractionResults(new MetadataExtractionResultQuery(
                "tenant-1", "kb-1", "", "job-1", "ACCEPTED", 2, "extractor-v2", 1, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(MetadataExtractionResultRecord::id).containsExactly("result-v2");
    }

    private void insertResult(String id,
                              String tenantId,
                              String kbId,
                              String docId,
                              String jobId,
                              String status) {
        insertResult(id, tenantId, kbId, docId, jobId, status, 2, "extractor-v2");
    }

    private void insertResult(String id,
                              String tenantId,
                              String kbId,
                              String docId,
                              String jobId,
                              String status,
                              int schemaVersion,
                              String extractorVersion) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_extraction_result(
                    id, tenant_id, kb_id, doc_id, job_id, schema_version, extractor_version, status,
                    normalized_metadata, raw_candidates, field_quality, validation_issues,
                    approved_metadata, approved_by, approved_time, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?,
                          '{"department":"HR"}',
                          '[{"fieldKey":"department","value":"hr"}]',
                          '[{"fieldKey":"department","confidence":0.93}]',
                          '[{"fieldKey":"department","reason":"LOW_CONFIDENCE"}]',
                          '{"department":"HR"}',
                          'auditor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, tenantId, kbId, docId, jobId, schemaVersion, extractorVersion, status);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_extraction_result");
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_extraction_result (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64) NOT NULL,
                    job_id VARCHAR(64),
                    schema_version INTEGER NOT NULL DEFAULT 1,
                    extractor_version VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    normalized_metadata VARCHAR(2048),
                    raw_candidates VARCHAR(2048),
                    field_quality VARCHAR(2048),
                    validation_issues VARCHAR(2048),
                    approved_metadata VARCHAR(2048),
                    approved_by VARCHAR(64),
                    approved_time TIMESTAMP,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
