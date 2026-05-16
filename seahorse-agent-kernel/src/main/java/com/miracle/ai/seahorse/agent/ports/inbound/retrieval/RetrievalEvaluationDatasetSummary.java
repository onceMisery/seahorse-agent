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

package com.miracle.ai.seahorse.agent.ports.inbound.retrieval;

import java.time.Instant;
import java.util.Objects;

/**
 * 检索评测集列表摘要。
 */
public record RetrievalEvaluationDatasetSummary(
        String datasetId,
        String knowledgeBaseId,
        String name,
        String description,
        boolean enabled,
        int caseCount,
        Instant createTime,
        Instant updateTime
) {

    public RetrievalEvaluationDatasetSummary {
        datasetId = Objects.requireNonNullElse(datasetId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        name = Objects.requireNonNullElse(name, "");
        description = Objects.requireNonNullElse(description, "");
        caseCount = Math.max(caseCount, 0);
        createTime = Objects.requireNonNullElseGet(createTime, Instant::now);
        updateTime = Objects.requireNonNullElseGet(updateTime, Instant::now);
    }
}
