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

import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.DefaultMetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.MetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;

import java.util.List;
import java.util.Objects;

/**
 * 构建检索上下文，并负责 metadata filter 的 schema 编译。
 * <p>
 * 该类集中处理 filter/schema/options/trace 的组合逻辑，让主检索引擎只负责串联阶段。
 */
final class KernelSearchContextFactory {

    private final FeatureActivationContext activationContext;
    private final MetadataSchemaRegistryPort schemaRegistryPort;
    private final MetadataFilterCompiler metadataFilterCompiler;
    private final KernelRetrievalObservationSupport observationSupport;

    KernelSearchContextFactory(FeatureActivationContext activationContext,
                               MetadataSchemaRegistryPort schemaRegistryPort,
                               MetadataFilterCompiler metadataFilterCompiler,
                               KernelRetrievalObservationSupport observationSupport) {
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.schemaRegistryPort = Objects.requireNonNullElseGet(schemaRegistryPort, MetadataSchemaRegistryPort::empty);
        this.metadataFilterCompiler = Objects.requireNonNullElseGet(metadataFilterCompiler,
                DefaultMetadataFilterCompiler::new);
        this.observationSupport = Objects.requireNonNull(observationSupport, "observationSupport must not be null");
    }

    SearchContext build(List<SubQuestionIntent> subIntents,
                        int topK,
                        RetrievalFilter filter,
                        RetrievalOptions options,
                        TraceRunScope traceRunScope) {
        List<SubQuestionIntent> safeSubIntents = Objects.requireNonNullElse(subIntents, List.of());
        String question = safeSubIntents.isEmpty() ? "" : safeSubIntents.get(0).subQuestion();
        return SearchContext.builder()
                .originalQuestion(question)
                .rewrittenQuestion(question)
                .intents(safeSubIntents)
                .topK(topK)
                .filter(filter)
                .options(options)
                .compiledFilter(compileFilter(filter))
                .traceRunScope(traceRunScope)
                .build();
    }

    private CompiledMetadataFilter compileFilter(RetrievalFilter filter) {
        if (filter == null) {
            return null;
        }
        String knowledgeBaseId = filter.system().knowledgeBaseIds().isEmpty()
                ? ""
                : filter.system().knowledgeBaseIds().get(0);
        String tenantId = !filter.system().tenantId().isBlank() ? filter.system().tenantId() : activationContext.tenantId();
        MetadataSchema schema = schemaRegistryPort.loadSchema(tenantId, knowledgeBaseId);
        try {
            // 只有通过 schema 编译后的过滤条件才能下发到检索通道与后处理器。
            CompiledMetadataFilter compiledFilter = metadataFilterCompiler.compile(filter, schema);
            observationSupport.recordMetadataFilterUsage(tenantId, knowledgeBaseId, schema, compiledFilter);
            return compiledFilter;
        } catch (RuntimeException ex) {
            observationSupport.recordMetadataFilterRejected(tenantId, knowledgeBaseId, schema, filter, ex);
            throw ex;
        }
    }
}
