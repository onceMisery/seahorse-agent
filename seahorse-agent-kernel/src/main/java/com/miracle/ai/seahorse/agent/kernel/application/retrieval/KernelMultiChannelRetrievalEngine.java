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
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.DefaultMetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.MetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchChannelFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchResultPostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * L1 多通道检索编排器。
 * <p>
 * 该类只负责稳定编排：构建检索上下文、并行执行已激活通道、合并结果并按顺序执行后处理链。
 * 具体检索策略保留在 L2 Feature，外部向量库和知识库能力通过 L3 outbound port 提供。
 */
public class KernelMultiChannelRetrievalEngine {

    private static final Logger LOG = LoggerFactory.getLogger(KernelMultiChannelRetrievalEngine.class);
    private static final String LOG_MSG_CHANNEL_FAILED = "检索通道 {} 执行失败，按空结果降级";
    private static final String LOG_MSG_PROCESSOR_FAILED = "检索后处理器 {} 执行失败，跳过该处理器";

    private final ExtensionRegistry extensionRegistry;
    private final Executor retrievalExecutor;
    private final FeatureActivationContext activationContext;
    private final MetadataSchemaRegistryPort schemaRegistryPort;
    private final MetadataFilterCompiler metadataFilterCompiler;

    public KernelMultiChannelRetrievalEngine(ExtensionRegistry extensionRegistry,
                                             Executor retrievalExecutor,
                                             FeatureActivationContext activationContext) {
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "扩展注册表不能为空");
        this.retrievalExecutor = Objects.requireNonNull(retrievalExecutor, "检索线程池不能为空");
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.schemaRegistryPort = MetadataSchemaRegistryPort.empty();
        this.metadataFilterCompiler = new DefaultMetadataFilterCompiler();
    }

    public KernelMultiChannelRetrievalEngine(ExtensionRegistry extensionRegistry,
                                             Executor retrievalExecutor,
                                             FeatureActivationContext activationContext,
                                             MetadataSchemaRegistryPort schemaRegistryPort,
                                             MetadataFilterCompiler metadataFilterCompiler) {
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "extensionRegistry must not be null");
        this.retrievalExecutor = Objects.requireNonNull(retrievalExecutor, "retrievalExecutor must not be null");
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.schemaRegistryPort = Objects.requireNonNullElseGet(schemaRegistryPort, MetadataSchemaRegistryPort::empty);
        this.metadataFilterCompiler = Objects.requireNonNullElseGet(metadataFilterCompiler,
                DefaultMetadataFilterCompiler::new);
    }

    /**
     * 执行知识库多通道检索。
     *
     * @param subIntents 子问题意图列表
     * @param topK       期望返回数量
     * @return 检索结果 Chunk 列表
     */
    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents, int topK) {
        return retrieveKnowledgeChannels(subIntents, topK, null, null);
    }

    /**
     * 执行带治理过滤条件的知识库多通道检索。
     * <p>
     * 动态 metadata 过滤会先按 Schema 编译成后端无关 AST，再传递给检索通道和兜底后处理器。
     *
     * @param subIntents 子问题意图列表
     * @param topK       期望返回数量
     * @param filter     已解析的检索过滤条件
     * @param options    检索策略参数
     * @return 检索结果 Chunk 列表
     */
    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents,
                                                          int topK,
                                                          RetrievalFilter filter,
                                                          RetrievalOptions options) {
        SearchContext context = buildSearchContext(subIntents, topK, filter, options);
        List<SearchChannelResult> channelResults = executeSearchChannels(context);
        if (channelResults.isEmpty()) {
            return List.of();
        }
        return executePostProcessors(channelResults, context);
    }

    private List<SearchChannelResult> executeSearchChannels(SearchContext context) {
        List<SearchChannelFeature> enabledChannels = extensionRegistry
                .getActivatedExtensions(SearchChannelFeature.class, activationContext)
                .stream()
                .filter(channel -> channel.enabled(context))
                .toList();

        if (enabledChannels.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<SearchChannelResult>> futures = enabledChannels.stream()
                .map(channel -> CompletableFuture.supplyAsync(() -> executeSingleChannel(channel, context),
                        retrievalExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private SearchChannelResult executeSingleChannel(SearchChannelFeature channel, SearchContext context) {
        try {
            return channel.search(context);
        } catch (Exception ex) {
            LOG.error(LOG_MSG_CHANNEL_FAILED, channel.name(), ex);
            return emptyResult(channel);
        }
    }

    private SearchChannelResult emptyResult(SearchChannelFeature channel) {
        return SearchChannelResult.builder()
                .channelType(channel.channelType())
                .channelName(channel.name())
                .chunks(List.of())
                .build();
    }

    private List<RetrievedChunk> executePostProcessors(List<SearchChannelResult> results, SearchContext context) {
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

    private List<RetrievedChunk> executeSingleProcessor(SearchResultPostProcessorFeature processor,
                                                       List<RetrievedChunk> chunks,
                                                       List<SearchChannelResult> results,
                                                       SearchContext context) {
        try {
            return processor.process(chunks, results, context);
        } catch (Exception ex) {
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

    private SearchContext buildSearchContext(List<SubQuestionIntent> subIntents,
                                             int topK,
                                             RetrievalFilter filter,
                                             RetrievalOptions options) {
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
        // 只有通过 Schema 编译后的表达式才能进入向量库或关键词后端，避免原始 Map 直通外部查询。
        return metadataFilterCompiler.compile(filter, schema);
    }
}
