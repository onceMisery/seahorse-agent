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

package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 跨版本质量对比报表。
 *
 * <p>该报表并列展示治理侧质量变化和检索侧评测变化，
 * 便于在版本发布前统一评估元数据治理与检索效果。
 */
public record VersionQualityComparisonReport(
        String tenantId,
        String knowledgeBaseId,
        MetadataQualityComparisonReport metadataQuality,
        RetrievalEvaluationComparisonReport retrievalQuality,
        Instant generatedAt
) {

    public VersionQualityComparisonReport {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        metadataQuality = metadataQuality == null
                ? new MetadataQualityComparisonReport(
                tenantId,
                knowledgeBaseId,
                MetadataQualityReport.empty(tenantId, knowledgeBaseId),
                MetadataQualityReport.empty(tenantId, knowledgeBaseId),
                null,
                List.of())
                : metadataQuality;
        retrievalQuality = retrievalQuality == null
                ? new RetrievalEvaluationComparisonReport("", "", List.of(), List.of())
                : retrievalQuality;
        generatedAt = Objects.requireNonNullElseGet(generatedAt, Instant::now);
    }
}
