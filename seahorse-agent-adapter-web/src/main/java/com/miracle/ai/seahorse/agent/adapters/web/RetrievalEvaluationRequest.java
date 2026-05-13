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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.MetadataCondition;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand;

import java.util.List;
import java.util.Objects;

/**
 * 检索评测请求。
 *
 * <p>Web 层只把评测集样本转换为强类型过滤对象，动态 metadata 仍由后续 Filter Compiler 校验。
 */
public record RetrievalEvaluationRequest(
        String tenantId,
        String strategyName,
        Integer topK,
        RetrievalOptions options,
        List<CaseRequest> cases
) {

    public RetrievalEvaluationCommand toCommand(String kbId) {
        int resolvedTopK = topK == null || topK <= 0 ? 5 : topK;
        List<RetrievalEvaluationCase> evaluationCases = Objects.requireNonNullElse(cases, List.<CaseRequest>of())
                .stream()
                .map(caseRequest -> caseRequest.toCase(kbId, tenantId))
                .toList();
        return new RetrievalEvaluationCommand(strategyName, resolvedTopK, options, evaluationCases);
    }

    public record CaseRequest(
            String caseId,
            String question,
            String tenantId,
            List<String> expectedKbIds,
            List<String> expectedDocIds,
            List<String> expectedChunkIds,
            List<String> aclSubjectIds,
            List<MetadataCondition> metadataConditions,
            RetrievalFilter filter,
            RetrievalOptions options
    ) {

        private RetrievalEvaluationCase toCase(String kbId, String requestTenantId) {
            return new RetrievalEvaluationCase(
                    caseId,
                    question,
                    expectedKbIds,
                    expectedDocIds,
                    expectedChunkIds,
                    filter == null ? defaultFilter(kbId, requestTenantId) : filter,
                    options);
        }

        private RetrievalFilter defaultFilter(String kbId, String requestTenantId) {
            String effectiveTenant = hasText(tenantId) ? tenantId : Objects.requireNonNullElse(requestTenantId, "");
            return RetrievalFilter.builder()
                    .system(SystemRetrievalFilter.builder()
                            .tenantId(effectiveTenant)
                            .knowledgeBaseIds(hasText(kbId) ? List.of(kbId) : List.of())
                            .aclSubjectIds(copyTextList(aclSubjectIds))
                            .enabledOnly(true)
                            .build())
                    .metadataConditions(Objects.requireNonNullElse(metadataConditions, List.of()))
                    .build();
        }

        private List<String> copyTextList(List<String> values) {
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

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
