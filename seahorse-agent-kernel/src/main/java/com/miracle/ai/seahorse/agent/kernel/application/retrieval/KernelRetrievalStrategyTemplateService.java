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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplateInboundPort;

import java.util.List;
import java.util.Map;

/**
 * 内核默认检索策略模板服务。
 *
 * <p>当前阶段只提供内置模板，不持久化知识库级覆盖配置；后续可用仓储适配器替换该端口实现。</p>
 */
public class KernelRetrievalStrategyTemplateService implements RetrievalStrategyTemplateInboundPort {

    @Override
    public List<RetrievalStrategyTemplate> listTemplates(String kbId) {
        // kbId 预留给后续知识库级模板覆盖，本轮保持无 DDL 的默认模板闭环。
        return List.of(vectorOnly(), hybridRrf(), hybridRerank());
    }

    private RetrievalStrategyTemplate vectorOnly() {
        return new RetrievalStrategyTemplate(
                "vector_only",
                "向量召回",
                "只使用向量和意图召回，适合轻量知识库或关键词索引尚未启用的场景。",
                RetrievalOptions.builder()
                        .finalTopK(5)
                        .enableVector(true)
                        .enableIntentDirected(true)
                        .enableKeyword(false)
                        .enableRrf(false)
                        .enableRerank(false)
                        .build());
    }

    private RetrievalStrategyTemplate hybridRrf() {
        return new RetrievalStrategyTemplate(
                "hybrid_rrf",
                "混合召回 RRF",
                "同时启用向量、意图和关键词通道，并通过 RRF 做通道融合。",
                RetrievalOptions.builder()
                        .finalTopK(5)
                        .enableVector(true)
                        .enableIntentDirected(true)
                        .enableKeyword(true)
                        .enableRrf(true)
                        .enableRerank(false)
                        .channelSettings(Map.of(
                                "rrfK", 60,
                                "channelWeights", Map.of(
                                        "VectorGlobalSearch", 1.0D,
                                        "IntentDirectedSearch", 1.2D,
                                        "KeywordSearch", 1.0D)))
                        .build());
    }

    private RetrievalStrategyTemplate hybridRerank() {
        return new RetrievalStrategyTemplate(
                "hybrid_rerank",
                "混合召回精排",
                "在混合召回和 RRF 后启用精排；管理端应用时需要补充具体 rerankModel。",
                RetrievalOptions.builder()
                        .finalTopK(5)
                        .fusionTopK(15)
                        .rerankTopK(5)
                        .enableVector(true)
                        .enableIntentDirected(true)
                        .enableKeyword(true)
                        .enableRrf(true)
                        .enableRerank(true)
                        .channelSettings(Map.of("rrfK", 60))
                        .build());
    }
}
