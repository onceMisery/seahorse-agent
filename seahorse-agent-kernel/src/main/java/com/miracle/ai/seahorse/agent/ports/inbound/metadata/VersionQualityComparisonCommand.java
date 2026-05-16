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

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;

import java.util.List;
import java.util.Objects;

/**
 * 跨版本质量对比命令。
 *
 * <p>治理侧版本筛选与检索评测对比共用同一个知识库上下文，
 * 避免管理端分别调用两个接口后再自行拼装口径。
 */
public record VersionQualityComparisonCommand(
        String tenantId,
        String knowledgeBaseId,
        int quarantineTopN,
        Integer baselineSchemaVersion,
        String baselineExtractorVersion,
        String baselineLlmPromptVersion,
        Integer candidateSchemaVersion,
        String candidateExtractorVersion,
        String candidateLlmPromptVersion,
        RetrievalEvaluationComparisonCommand retrievalComparison
) {

    public VersionQualityComparisonCommand {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        quarantineTopN = quarantineTopN > 0 ? quarantineTopN : 5;
        baselineExtractorVersion = Objects.requireNonNullElse(baselineExtractorVersion, "");
        baselineLlmPromptVersion = Objects.requireNonNullElse(baselineLlmPromptVersion, "");
        candidateExtractorVersion = Objects.requireNonNullElse(candidateExtractorVersion, "");
        candidateLlmPromptVersion = Objects.requireNonNullElse(candidateLlmPromptVersion, "");
        retrievalComparison = retrievalComparison == null
                ? new RetrievalEvaluationComparisonCommand("", 5, List.of(), List.of())
                : retrievalComparison;
    }
}
