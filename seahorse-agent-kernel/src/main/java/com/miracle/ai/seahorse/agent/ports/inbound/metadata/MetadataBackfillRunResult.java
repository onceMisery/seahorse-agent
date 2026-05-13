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

package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单批次元数据回填执行摘要。
 */
public record MetadataBackfillRunResult(
        String jobId,
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
        List<String> failures
) {

    public MetadataBackfillRunResult {
        jobId = Objects.requireNonNullElse(jobId, "");
        status = Objects.requireNonNullElse(status, MetadataBackfillJobStatus.PENDING);
        currentPage = Math.max(1L, currentPage);
        batchSize = Math.max(1, batchSize);
        checkpoint = Map.copyOf(Objects.requireNonNullElse(checkpoint, Map.of()));
        failures = List.copyOf(Objects.requireNonNullElse(failures, List.of()));
    }

    public boolean success() {
        return failedDocuments == 0;
    }
}
