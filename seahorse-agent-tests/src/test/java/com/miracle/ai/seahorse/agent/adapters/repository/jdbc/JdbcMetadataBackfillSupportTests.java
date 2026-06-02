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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcMetadataBackfillSupportTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcMetadataBackfillSupport support = new JdbcMetadataBackfillSupport(
            jdbcTemplate, new JdbcMetadataJsonSupport(new ObjectMapper()));

    @Test
    void shouldPersistCheckpointAndFailuresWhenCreateAndSave() {
        Instant createTime = Instant.parse("2026-05-17T08:00:00Z");
        MetadataBackfillJobRecord created = new MetadataBackfillJobRecord(
                "job-1",
                "tenant-1",
                1L,
                "pipeline-1",
                MetadataBackfillJobStatus.PENDING,
                1,
                20,
                3,
                2,
                1,
                0,
                1,
                0,
                Map.of("cursor", "doc-1"),
                List.of("timeout"),
                "tester",
                createTime,
                createTime);

        support.create(created);
        support.save(new MetadataBackfillJobRecord(
                "job-1",
                "tenant-1",
                1L,
                "pipeline-1",
                MetadataBackfillJobStatus.RUNNING,
                2,
                50,
                20,
                18,
                2,
                0,
                1,
                1,
                Map.of("cursor", "doc-20"),
                List.of("timeout", "schema-mismatch"),
                "tester",
                createTime,
                createTime.plusSeconds(60)));

        verify(jdbcTemplate).update(anyString(), eq("job-1"), eq("tenant-1"), eq(1L), eq("pipeline-1"),
                eq("PENDING"), eq(1L), eq("{\"cursor\":\"doc-1\"}"), eq(20), eq(3), eq(2), eq(1), eq(0), eq(1),
                eq(0), eq("[\"timeout\"]"), eq("tester"), any(), any());
        verify(jdbcTemplate).update(anyString(), eq("RUNNING"), eq(2L), eq("{\"cursor\":\"doc-20\"}"), eq(50),
                eq(20), eq(18), eq(2), eq(0), eq(1), eq(1),
                eq("[\"timeout\",\"schema-mismatch\"]"), eq("tester"), any(), eq("job-1"));
    }

    @Test
    void shouldFindAndPageBackfillJobs() {
        MetadataBackfillJobRecord runningJob = new MetadataBackfillJobRecord(
                "job-2",
                "tenant-1",
                1L,
                "pipeline-1",
                MetadataBackfillJobStatus.RUNNING,
                2,
                50,
                20,
                18,
                2,
                0,
                1,
                1,
                Map.of("cursor", "doc-20"),
                List.of("timeout"),
                "tester",
                Instant.parse("2026-05-17T08:00:00Z"),
                Instant.parse("2026-05-17T08:01:00Z"));
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("job-2")))
                .thenReturn(List.of(runningJob));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(runningJob));

        Optional<MetadataBackfillJobRecord> found = support.findById("job-2");
        MetadataBackfillJobPage page = support.page(new MetadataBackfillJobQuery(
                "tenant-1", "1", MetadataBackfillJobStatus.RUNNING, 1, 10));

        assertThat(found).contains(runningJob);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).containsExactly(runningJob);
        assertThat(page.pages()).isEqualTo(1);
    }
}
