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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;

import java.util.List;
import java.util.Objects;

/**
 * 单条检索评测样本。
 *
 * @param caseId           样本标识
 * @param question         评测问题
 * @param expectedKbIds    期望命中的知识库 ID
 * @param expectedDocIds   期望命中的文档 ID
 * @param expectedChunkIds 期望命中的分片 ID
 * @param filter           检索过滤条件，动态元数据仍会经过 Filter Compiler
 * @param options          样本级检索策略参数，缺省时使用命令级 options
 */
public record RetrievalEvaluationCase(
        @JsonProperty("caseId") String caseId,
        @JsonProperty("question") String question,
        @JsonProperty("expectedKbIds") List<String> expectedKbIds,
        @JsonProperty("expectedDocIds") List<String> expectedDocIds,
        @JsonProperty("expectedChunkIds") List<String> expectedChunkIds,
        @JsonProperty("filter") RetrievalFilter filter,
        @JsonProperty("options") RetrievalOptions options,
        @JsonProperty("negativeChunkIds") List<String> negativeChunkIds,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("minRecall") Double minRecall,
        @JsonProperty("minPrecision") Double minPrecision
) {

    public RetrievalEvaluationCase(String caseId,
                                   String question,
                                   List<String> expectedKbIds,
                                   List<String> expectedDocIds,
                                   List<String> expectedChunkIds,
                                   RetrievalFilter filter,
                                   RetrievalOptions options) {
        this(caseId, question, expectedKbIds, expectedDocIds, expectedChunkIds, filter, options,
                List.of(), List.of(), null, null);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RetrievalEvaluationCase {
        caseId = Objects.requireNonNullElse(caseId, "");
        question = Objects.requireNonNullElse(question, "");
        expectedKbIds = copyTextList(expectedKbIds);
        expectedDocIds = copyTextList(expectedDocIds);
        expectedChunkIds = copyTextList(expectedChunkIds);
        negativeChunkIds = copyTextList(negativeChunkIds);
        tags = copyTextList(tags);
        minRecall = normalizeThreshold(minRecall);
        minPrecision = normalizeThreshold(minPrecision);
    }

    private static List<String> copyTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static Double normalizeThreshold(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return value;
    }
}
