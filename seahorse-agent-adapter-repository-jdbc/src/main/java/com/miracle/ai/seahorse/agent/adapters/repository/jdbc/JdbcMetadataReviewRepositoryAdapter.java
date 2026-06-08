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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewAuditRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcMetadataReviewRepositoryAdapter implements MetadataReviewQueuePort,
        MetadataReviewManagementRepositoryPort {

    private final JdbcMetadataReviewSupport reviewSupport;
    private final JdbcMetadataExtractionResultRepositoryAdapter extractionResultRepositoryAdapter;

    public JdbcMetadataReviewRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        JdbcMetadataJsonSupport jsonSupport = new JdbcMetadataJsonSupport(objectMapper);
        this.reviewSupport = new JdbcMetadataReviewSupport(jdbcTemplate, jsonSupport);
        this.extractionResultRepositoryAdapter = new JdbcMetadataExtractionResultRepositoryAdapter(
                dataSource, objectMapper);
    }

    @Override
    public void enqueue(MetadataReviewItem item) {
        reviewSupport.enqueue(item);
    }

    @Override
    public MetadataReviewPage pageReviewItems(MetadataReviewQuery query) {
        return reviewSupport.pageReviewItems(query);
    }

    @Override
    public Optional<MetadataReviewRecord> findReviewItem(String itemId) {
        return reviewSupport.findReviewItem(itemId);
    }

    @Override
    public List<MetadataReviewAuditRecord> listReviewAudits(String itemId) {
        return reviewSupport.listReviewAudits(itemId);
    }

    @Override
    public MetadataReviewRecord applyReviewDecision(MetadataReviewDecision decision) {
        JdbcMetadataReviewSupport.ReviewDecisionResult result = reviewSupport.applyReviewDecision(decision);
        if (MetadataReviewStatus.APPROVED.equals(result.reviewStatus())
                || MetadataReviewStatus.CORRECTED.equals(result.reviewStatus())) {
            extractionResultRepositoryAdapter.updateApproval(
                    result.resultId(), result.approvedMetadata(), result.reviewerId());
        } else if (MetadataReviewStatus.REJECTED.equals(result.reviewStatus())) {
            extractionResultRepositoryAdapter.updateStatus(result.resultId(), "REJECTED");
        } else if (MetadataReviewStatus.QUARANTINED.equals(result.reviewStatus())) {
            extractionResultRepositoryAdapter.updateStatus(result.resultId(), "QUARANTINED");
        } else if (MetadataReviewStatus.RE_EXTRACTING.equals(result.reviewStatus())) {
            extractionResultRepositoryAdapter.updateStatus(result.resultId(), "RE_EXTRACTING");
        }
        return reviewSupport.findReviewItem(result.itemId())
                .orElseThrow(() -> new IllegalArgumentException("元数据复核项不存在: " + result.itemId()));
    }
}
