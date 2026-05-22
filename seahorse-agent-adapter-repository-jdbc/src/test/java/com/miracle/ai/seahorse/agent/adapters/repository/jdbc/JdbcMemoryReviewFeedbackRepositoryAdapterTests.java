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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryReviewFeedbackRepositoryAdapterTests {

    private JdbcMemoryReviewFeedbackRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-review-feedback-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new JdbcChatSchemaUpgrade(dataSource).upgrade();
        adapter = new JdbcMemoryReviewFeedbackRepositoryAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldListFeedbackSamplesForDatasetExportWithFilters() {
        adapter.save(sample("sample-1", "review-1", "tenant-1", "user-1",
                MemoryReviewStatus.APPLIED, "PROJECT_FACT", "project.fact",
                Instant.parse("2026-05-22T08:00:00Z")));
        adapter.save(sample("sample-2", "review-2", "tenant-1", "user-2",
                MemoryReviewStatus.APPLIED, "PROJECT_FACT", "project.fact",
                Instant.parse("2026-05-22T09:00:00Z")));
        adapter.save(sample("sample-3", "review-3", "tenant-1", "user-1",
                MemoryReviewStatus.REJECTED, "PROJECT_FACT", "project.fact",
                Instant.parse("2026-05-22T10:00:00Z")));

        List<MemoryReviewFeedbackSample> samples = adapter.listSamples(new MemoryReviewFeedbackQuery(
                "tenant-1",
                "user-1",
                MemoryReviewStatus.APPLIED,
                "PROJECT_FACT",
                "project.fact",
                10));

        assertThat(samples).extracting(MemoryReviewFeedbackSample::sampleId)
                .containsExactly("sample-1");
        assertThat(samples.get(0).chosenMetadata()).containsEntry("reviewReason", "human");
    }

    private MemoryReviewFeedbackSample sample(String sampleId,
                                              String candidateId,
                                              String tenantId,
                                              String userId,
                                              MemoryReviewStatus status,
                                              String targetKind,
                                              String targetKey,
                                              Instant createdAt) {
        return new MemoryReviewFeedbackSample(
                sampleId,
                candidateId,
                "op-" + candidateId,
                tenantId,
                userId,
                "REVIEW",
                status,
                "auditor",
                "comment",
                "SHORT_TERM",
                targetKind,
                targetKey,
                "rejected",
                status == MemoryReviewStatus.APPLIED ? "chosen" : "",
                Map.of("source", "llm"),
                Map.of("reviewReason", "human"),
                List.of("msg-1"),
                "memory-1",
                "SHORT_TERM",
                createdAt);
    }
}
