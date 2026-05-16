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
import java.util.List;
import java.util.Objects;

/**
 * 可复用的检索评测集。
 *
 * <p>样本仍使用强类型 {@link RetrievalEvaluationCase}，Web 或仓储不得保存原始动态 metadata Map 绕过过滤编译。
 */
public record RetrievalEvaluationDataset(
        String datasetId,
        String knowledgeBaseId,
        String name,
        String description,
        boolean enabled,
        List<RetrievalEvaluationCase> cases,
        Instant createTime,
        Instant updateTime
) {

    public RetrievalEvaluationDataset {
        datasetId = Objects.requireNonNullElse(datasetId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        name = Objects.requireNonNullElse(name, "");
        description = Objects.requireNonNullElse(description, "");
        cases = List.copyOf(Objects.requireNonNullElse(cases, List.of()));
        createTime = Objects.requireNonNullElseGet(createTime, Instant::now);
        updateTime = Objects.requireNonNullElseGet(updateTime, Instant::now);
    }

    public RetrievalEvaluationDatasetSummary summary() {
        return new RetrievalEvaluationDatasetSummary(
                datasetId, knowledgeBaseId, name, description, enabled, cases.size(), createTime, updateTime);
    }
}
