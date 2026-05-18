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

import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchResultPostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 执行检索结果后处理链。
 * <p>
 * 该类只负责后处理器发现、排序、执行和失败跳过，保持主检索引擎的编排职责轻量。
 */
final class KernelRetrievalPostProcessorChain {

    private static final Logger LOG = LoggerFactory.getLogger(KernelRetrievalPostProcessorChain.class);
    private static final String LOG_MSG_PROCESSOR_FAILED = "检索后处理器 {} 执行失败，跳过该处理器";

    private final ExtensionRegistry extensionRegistry;
    private final FeatureActivationContext activationContext;
    private final KernelRagTraceRecorder traceRecorder;
    private final KernelRetrievalObservationSupport observationSupport;

    KernelRetrievalPostProcessorChain(ExtensionRegistry extensionRegistry,
                                      FeatureActivationContext activationContext,
                                      KernelRagTraceRecorder traceRecorder,
                                      KernelRetrievalObservationSupport observationSupport) {
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "extensionRegistry must not be null");
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        this.observationSupport = Objects.requireNonNull(observationSupport, "observationSupport must not be null");
    }

    List<RetrievedChunk> execute(List<SearchChannelResult> results, SearchContext context) {
        List<SearchResultPostProcessorFeature> processors = extensionRegistry
                .getActivatedExtensions(SearchResultPostProcessorFeature.class, activationContext)
                .stream()
                .filter(processor -> processor.enabled(context))
                .sorted(Comparator.comparingInt(SearchResultPostProcessorFeature::order))
                .toList();

        List<RetrievedChunk> chunks = mergeChunks(results);
        for (SearchResultPostProcessorFeature processor : processors) {
            chunks = executeSingleProcessor(processor, chunks, results, context);
        }
        return chunks;
    }

    int candidateCount(List<SearchChannelResult> results) {
        return Objects.requireNonNullElse(results, List.<SearchChannelResult>of()).stream()
                .mapToInt(result -> safeChunks(result).size())
                .sum();
    }

    private List<RetrievedChunk> executeSingleProcessor(SearchResultPostProcessorFeature processor,
                                                        List<RetrievedChunk> chunks,
                                                        List<SearchChannelResult> results,
                                                        SearchContext context) {
        TraceNodeScope nodeScope = traceRecorder.startNode(traceRunScope(context), processorTraceCommand(processor));
        long startedAt = System.currentTimeMillis();
        int inputCount = chunkCount(chunks);
        try {
            List<RetrievedChunk> processed = processor.process(chunks, results, context);
            observationSupport.recordMetadataGuardCompleted(processor, context, inputCount, chunkCount(processed),
                    System.currentTimeMillis() - startedAt, null);
            traceRecorder.finishNode(nodeScope);
            return processed;
        } catch (Exception ex) {
            observationSupport.recordMetadataGuardCompleted(processor, context, inputCount, inputCount,
                    System.currentTimeMillis() - startedAt, ex);
            traceRecorder.finishNode(nodeScope, ex);
            LOG.error(LOG_MSG_PROCESSOR_FAILED, processor.name(), ex);
            return chunks;
        }
    }

    private List<RetrievedChunk> mergeChunks(List<SearchChannelResult> results) {
        return results.stream()
                .flatMap(result -> safeChunks(result).stream())
                .collect(Collectors.toList());
    }

    private List<RetrievedChunk> safeChunks(SearchChannelResult result) {
        if (result == null) {
            return List.of();
        }
        return Objects.requireNonNullElse(result.getChunks(), List.of());
    }

    private int chunkCount(List<RetrievedChunk> chunks) {
        return chunks == null ? 0 : chunks.size();
    }

    private TraceRunScope traceRunScope(SearchContext context) {
        TraceRunScope traceRunScope = context == null ? null : context.getTraceRunScope();
        return traceRunScope == null ? TraceRunScope.disabled() : traceRunScope;
    }

    private TraceNodeStartCommand processorTraceCommand(SearchResultPostProcessorFeature processor) {
        return new TraceNodeStartCommand(
                "post-processor:" + Objects.requireNonNullElse(processor.name(), "unknown"),
                "RETRIEVAL_POST_PROCESSOR",
                processor.getClass().getName(),
                "process",
                null,
                1);
    }
}
