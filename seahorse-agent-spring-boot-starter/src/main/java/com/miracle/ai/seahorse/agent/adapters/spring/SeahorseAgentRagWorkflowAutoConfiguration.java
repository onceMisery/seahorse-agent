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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.adapters.ai.JinaRerankModelAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.CachedRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.NoOpQueryRewriteAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.workflow.KernelWorkflowVisualizationService;
import com.miracle.ai.seahorse.agent.kernel.application.workflow.WorkflowEventPublisher;
import com.miracle.ai.seahorse.agent.ports.inbound.workflow.WorkflowVisualizationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.workflow.WorkflowVisualizationRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Advanced RAG (Module 09) and Workflow Visualization (Module 08).
 *
 * <p>Registers the following beans:
 * <ul>
 *   <li>{@link JinaRerankModelAdapter} — when {@code seahorse-agent.jina.api-key} is set</li>
 *   <li>{@link NoOpQueryRewriteAdapter} — when no other {@link QueryRewritePort} bean exists</li>
 *   <li>{@link CachedRetrievalEngine} — always, with default 10-minute TTL</li>
 *   <li>{@link KernelWorkflowVisualizationService} — when repository port is available</li>
 *   <li>{@link WorkflowEventPublisher} — always</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SeahorseAgentRagWorkflowAutoConfiguration {

    // ── Module 09: Advanced RAG ──────────────────────────────────────

    /**
     * Jina AI rerank model adapter, activated only when the API key is provided.
     */
    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.jina", name = "api-key")
    @ConditionalOnMissingBean(name = "advancedRagRerankModelPort")
    public RerankModelPort jinaRerankModelAdapter(
            @Value("${seahorse-agent.jina.api-key}") String apiKey,
            @Value("${seahorse-agent.jina.rerank-model:jina-reranker-v2-base-multilingual}") String model) {
        return new JinaRerankModelAdapter(apiKey, model);
    }

    /**
     * No-op query rewrite adapter, used when no LLM-based rewriting is configured.
     */
    @Bean
    @ConditionalOnMissingBean(name = "advancedRagQueryRewritePort")
    public QueryRewritePort noOpQueryRewriteAdapter() {
        return new NoOpQueryRewriteAdapter();
    }

    /**
     * Cached retrieval engine with default 10-minute TTL.
     */
    @Bean
    @ConditionalOnMissingBean(CachedRetrievalEngine.class)
    public CachedRetrievalEngine<?> cachedRetrievalEngine() {
        return new CachedRetrievalEngine<>((query, options) -> java.util.List.of());
    }

    // ── Module 08: Workflow Visualization ────────────────────────────

    /**
     * Workflow visualization service — builds DAG from persisted execution steps.
     */
    @Bean
    @ConditionalOnBean(WorkflowVisualizationRepositoryPort.class)
    @ConditionalOnMissingBean(WorkflowVisualizationInboundPort.class)
    public KernelWorkflowVisualizationService workflowVisualizationService(
            WorkflowVisualizationRepositoryPort repository) {
        return new KernelWorkflowVisualizationService(repository);
    }

    /**
     * Workflow event publisher for real-time step updates via SSE.
     */
    @Bean
    @ConditionalOnMissingBean(WorkflowEventPublisher.class)
    public WorkflowEventPublisher workflowEventPublisher() {
        return new WorkflowEventPublisher();
    }
}
