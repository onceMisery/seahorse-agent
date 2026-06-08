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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillCountItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillOperationsOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class JdbcMetadataBackfillJobRepositoryAdapter implements MetadataBackfillJobRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataBackfillSupport backfillSupport;

    public JdbcMetadataBackfillJobRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.backfillSupport = new JdbcMetadataBackfillSupport(
                jdbcTemplate, new JdbcMetadataJsonSupport(objectMapper));
    }

    @Override
    public String create(MetadataBackfillJobRecord job) {
        return backfillSupport.create(job);
    }

    @Override
    public Optional<MetadataBackfillJobRecord> findById(String jobId) {
        return backfillSupport.findById(jobId);
    }

    @Override
    public MetadataBackfillJobPage page(MetadataBackfillJobQuery query) {
        return backfillSupport.page(query);
    }

    @Override
    public MetadataBackfillOperationsOverview overview(String tenantId, String knowledgeBaseId) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        if (blank(safeTenantId)) {
            return MetadataBackfillOperationsOverview.empty(safeTenantId, safeKbId);
        }
        List<MetadataBackfillJobRecord> jobs = listBackfillJobs(safeTenantId, safeKbId);
        long processedDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::processedDocuments).sum();
        long succeededDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::succeededDocuments).sum();
        long failedDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::failedDocuments).sum();
        long skippedDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::skippedDocuments).sum();
        long reviewDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::reviewDocuments).sum();
        long quarantineDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::quarantineDocuments).sum();
        long pendingSchemaCompensationJobs = jobs.stream()
                .filter(this::isPendingSchemaCompensationJob)
                .count();
        long pendingSchemaCompensationDocuments = jobs.stream()
                .filter(this::isPendingSchemaCompensationJob)
                .mapToLong(job -> checkpointDocumentCount(job.checkpoint()))
                .sum();

        Map<String, Long> reviewStatusCounts = reviewStatusCounts(safeTenantId, safeKbId);
        Map<Boolean, Long> quarantineResolvedCounts = quarantineResolvedCounts(safeTenantId, safeKbId);
        return new MetadataBackfillOperationsOverview(
                safeTenantId,
                safeKbId,
                jobs.size(),
                processedDocuments,
                succeededDocuments,
                failedDocuments,
                skippedDocuments,
                reviewDocuments,
                quarantineDocuments,
                reviewStatusCounts.getOrDefault(MetadataReviewStatus.PENDING.name(), 0L),
                reviewStatusCounts.getOrDefault(MetadataReviewStatus.RE_EXTRACTING.name(), 0L),
                quarantineResolvedCounts.getOrDefault(Boolean.FALSE, 0L),
                quarantineResolvedCounts.getOrDefault(Boolean.TRUE, 0L),
                pendingSchemaCompensationJobs,
                pendingSchemaCompensationDocuments,
                statusCounts(jobs),
                failureReasonCounts(safeTenantId, safeKbId),
                pauseReasonCounts(jobs),
                latestMatchingJob(jobs, "reExtract"),
                latestMatchingJob(jobs, "schemaCompensation"),
                Instant.now());
    }

    @Override
    public void save(MetadataBackfillJobRecord job) {
        backfillSupport.save(job);
    }

    private List<MetadataBackfillJobRecord> listBackfillJobs(String tenantId, String knowledgeBaseId) {
        return backfillSupport.list(tenantId, knowledgeBaseId);
    }

    private Map<String, Long> reviewStatusCounts(String tenantId, String knowledgeBaseId) {
        try {
            List<MetadataBackfillCountItem> counts = jdbcTemplate.query("""
                    SELECT review_status, COUNT(1) AS item_count
                    FROM t_metadata_review_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                    GROUP BY review_status
                    """, (rs, rowNum) -> new MetadataBackfillCountItem(
                            text(rs.getString("review_status"), ""),
                            rs.getLong("item_count")),
                    tenantId, knowledgeBaseId, knowledgeBaseId);
            Map<String, Long> indexed = new LinkedHashMap<>();
            for (MetadataBackfillCountItem count : counts) {
                indexed.put(count.key(), count.count());
            }
            return indexed;
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    private Map<Boolean, Long> quarantineResolvedCounts(String tenantId, String knowledgeBaseId) {
        try {
            List<MetadataBackfillCountItem> counts = jdbcTemplate.query("""
                    SELECT resolved, COUNT(1) AS item_count
                    FROM t_metadata_quarantine_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                    GROUP BY resolved
                    """, (rs, rowNum) -> new MetadataBackfillCountItem(
                            rs.getInt("resolved") == 1 ? "true" : "false",
                            rs.getLong("item_count")),
                    tenantId, knowledgeBaseId, knowledgeBaseId);
            Map<Boolean, Long> indexed = new LinkedHashMap<>();
            for (MetadataBackfillCountItem count : counts) {
                indexed.put(Boolean.parseBoolean(count.key()), count.count());
            }
            return indexed;
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    private List<MetadataBackfillCountItem> failureReasonCounts(String tenantId, String knowledgeBaseId) {
        try {
            return jdbcTemplate.query("""
                    SELECT COALESCE(NULLIF(reason_code, ''), 'UNKNOWN') AS reason_code,
                           COUNT(1) AS item_count
                    FROM t_metadata_quarantine_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                    GROUP BY COALESCE(NULLIF(reason_code, ''), 'UNKNOWN')
                    ORDER BY item_count DESC, reason_code ASC
                    """, (rs, rowNum) -> new MetadataBackfillCountItem(
                            rs.getString("reason_code"),
                            rs.getLong("item_count")),
                    tenantId, knowledgeBaseId, knowledgeBaseId);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private List<MetadataBackfillCountItem> statusCounts(List<MetadataBackfillJobRecord> jobs) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MetadataBackfillJobStatus status : MetadataBackfillJobStatus.values()) {
            counts.put(status.name(), 0L);
        }
        for (MetadataBackfillJobRecord job : jobs) {
            counts.computeIfPresent(job.status().name(), (key, value) -> value + 1L);
        }
        return counts.entrySet().stream()
                .map(entry -> new MetadataBackfillCountItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<MetadataBackfillCountItem> pauseReasonCounts(List<MetadataBackfillJobRecord> jobs) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MetadataBackfillJobRecord job : jobs) {
            String pauseReason = text(job.checkpoint().get("pauseReason"), "");
            if (!blank(pauseReason)) {
                counts.merge(pauseReason, 1L, Long::sum);
            }
        }
        return sortCountItems(counts);
    }

    private List<MetadataBackfillCountItem> sortCountItems(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int byCount = Long.compare(right.getValue(), left.getValue());
                    return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
                })
                .map(entry -> new MetadataBackfillCountItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private MetadataBackfillJobRecord latestMatchingJob(List<MetadataBackfillJobRecord> jobs, String checkpointKey) {
        return jobs.stream()
                .filter(job -> bool(job.checkpoint().get(checkpointKey)))
                .findFirst()
                .orElse(null);
    }

    private boolean isPendingSchemaCompensationJob(MetadataBackfillJobRecord job) {
        return bool(job.checkpoint().get("schemaCompensation"))
                && job.status() != MetadataBackfillJobStatus.COMPLETED
                && job.status() != MetadataBackfillJobStatus.CANCELLED;
    }

    private long checkpointDocumentCount(Map<String, Object> checkpoint) {
        if (checkpoint == null || !checkpoint.containsKey("documentIds")) {
            return 0L;
        }
        Object value = checkpoint.get("documentIds");
        LinkedHashSet<String> documentIds = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addCheckpointDocumentId(documentIds, item);
            }
            return documentIds.size();
        }
        String text = text(value, "");
        if (blank(text)) {
            return 0L;
        }
        for (String item : text.split(",")) {
            addCheckpointDocumentId(documentIds, item);
        }
        return documentIds.size();
    }

    private void addCheckpointDocumentId(Set<String> documentIds, Object value) {
        String text = text(value, "");
        if (!blank(text)) {
            documentIds.add(text.trim());
        }
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(Objects.toString(value, "false"));
    }

    private String text(Object value, String defaultValue) {
        String text = Objects.toString(value, "");
        return text.isBlank() ? defaultValue : text;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
