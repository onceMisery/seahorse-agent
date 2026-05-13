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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据回填任务持久化快照。
 */
public record MetadataBackfillJobRecord(
        String jobId,
        String tenantId,
        String knowledgeBaseId,
        String pipelineId,
        MetadataBackfillJobStatus status,
        long currentPage,
        int batchSize,
        int processedDocuments,
        int succeededDocuments,
        int failedDocuments,
        int skippedDocuments,
        int reviewDocuments,
        int quarantineDocuments,
        Map<String, Object> checkpoint,
        List<String> failures,
        String operator,
        Instant createTime,
        Instant updateTime
) {

    public MetadataBackfillJobRecord {
        jobId = Objects.requireNonNullElse(jobId, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        pipelineId = Objects.requireNonNullElse(pipelineId, "");
        status = Objects.requireNonNullElse(status, MetadataBackfillJobStatus.PENDING);
        currentPage = Math.max(1L, currentPage);
        batchSize = Math.max(1, batchSize);
        checkpoint = Map.copyOf(Objects.requireNonNullElse(checkpoint, Map.of()));
        failures = List.copyOf(Objects.requireNonNullElse(failures, List.of()));
        operator = Objects.requireNonNullElse(operator, "");
        createTime = Objects.requireNonNullElse(createTime, Instant.now());
        updateTime = Objects.requireNonNullElse(updateTime, createTime);
    }
}
