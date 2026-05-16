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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;

import java.util.List;

/**
 * 跨版本质量对比请求。
 *
 * <p>治理版本筛选负责读取已落库的元数据质量报表，
 * 检索评测部分复用现有评测样本与策略定义，避免请求层重复造轮子。
 */
public record VersionQualityComparisonRequest(
        String tenantId,
        Integer quarantineTopN,
        Integer baselineSchemaVersion,
        String baselineExtractorVersion,
        String baselineLlmPromptVersion,
        Integer candidateSchemaVersion,
        String candidateExtractorVersion,
        String candidateLlmPromptVersion,
        RetrievalEvaluationComparisonRequest retrievalComparison
) {

    public VersionQualityComparisonCommand toCommand(String kbId) {
        return new VersionQualityComparisonCommand(
                tenantId,
                kbId,
                quarantineTopN == null ? 5 : quarantineTopN,
                baselineSchemaVersion,
                baselineExtractorVersion,
                baselineLlmPromptVersion,
                candidateSchemaVersion,
                candidateExtractorVersion,
                candidateLlmPromptVersion,
                toRetrievalCommand(kbId));
    }

    private RetrievalEvaluationComparisonCommand toRetrievalCommand(String kbId) {
        RetrievalEvaluationComparisonRequest request = retrievalComparison;
        if (request == null) {
            return new RetrievalEvaluationComparisonCommand("", 5, List.of(), List.of());
        }
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            request = new RetrievalEvaluationComparisonRequest(
                    tenantId,
                    request.baselineStrategyName(),
                    request.topK(),
                    request.strategies(),
                    request.cases());
        }
        return request.toCommand(kbId);
    }
}
