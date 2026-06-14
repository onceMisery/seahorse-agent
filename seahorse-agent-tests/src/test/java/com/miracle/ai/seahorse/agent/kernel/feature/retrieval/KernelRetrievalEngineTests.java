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

package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelMultiChannelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine.KernelRetrievalEnginePorts;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.QueryOptimizationResult;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentKind;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeatureProperties;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内核检索引擎契约测试。
 * <p>
 * 新内核必须保留旧多通道检索的主干能力：通道失败降级为空结果、后处理器失败后继续执行、
 * Feature 执行链按启动期注册顺序运行，避免把检索能力拆散成无主流程的插件集合。
 */
class KernelRetrievalEngineTests {

    private static final int TOP_K = 3;
    private static final String QUESTION = "如何办理入职？";
    private static final String FAILING_CHANNEL = "failing-channel";
    private static final String WORKING_CHANNEL = "working-channel";
    private static final String FIRST_PROCESSOR = "first-processor";
    private static final String SECOND_PROCESSOR = "second-processor";
    private static final String MCP_TOOL_ID = "weather";

    private final Executor directExecutor = Runnable::run;

    @Test
    void shouldKeepRetrievalRunningWhenFeatureChannelFails() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        List<SearchChannelResult> observedResults = new ArrayList<>();
        registerChannel(registry, failingChannel(), 1);
        registerChannel(registry, workingChannel(), 2);
        registerPostProcessor(registry, recordingPostProcessor(observedResults), 1);
        KernelRetrievalEngine engine = new KernelRetrievalEngine(registry, directExecutor, activationContext());

        List<RetrievedChunk> chunks = engine.retrieveKnowledgeChannels(singleQuestionIntent(), TOP_K);

        Assertions.assertEquals(1, chunks.size());
        Assertions.assertEquals("chunk-1", chunks.get(0).getId());
        Assertions.assertTrue(observedResults.stream()
                .anyMatch(result -> FAILING_CHANNEL.equals(result.getChannelName()) && result.getChunks().isEmpty()));
        Assertions.assertTrue(observedResults.stream()
                .anyMatch(result -> WORKING_CHANNEL.equals(result.getChannelName()) && !result.getChunks().isEmpty()));
    }

    @Test
    void shouldSkipFailingPostProcessorAndContinueNextProcessor() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        List<String> processorOrder = new ArrayList<>();
        registerChannel(registry, workingChannel(), 1);
        registerPostProcessor(registry, failingPostProcessor(processorOrder), 1);
        registerPostProcessor(registry, appendingPostProcessor(processorOrder), 2);
        KernelRetrievalEngine engine = new KernelRetrievalEngine(registry, directExecutor, activationContext());

        List<RetrievedChunk> chunks = engine.retrieveKnowledgeChannels(singleQuestionIntent(), TOP_K);

        Assertions.assertEquals(List.of(FIRST_PROCESSOR, SECOND_PROCESSOR), processorOrder);
        Assertions.assertEquals(2, chunks.size());
        Assertions.assertTrue(chunks.stream().anyMatch(chunk -> "post-processor-extra".equals(chunk.getId())));
    }

    @Test
    void shouldMergeKnowledgeBaseAndMcpContextInKernelRetrieval() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registerChannel(registry, workingChannel(), 1);
        KernelMultiChannelRetrievalEngine multiChannelEngine =
                new KernelMultiChannelRetrievalEngine(registry, directExecutor, activationContext());
        KernelMcpOrchestrator mcpOrchestrator = new KernelMcpOrchestrator(
                new SingleMcpToolRegistry(request -> McpToolExecutionResult.success(
                        request.toolId(), request.arguments().get("city") + "：晴天")),
                (toolId, question) -> Map.of("city", "杭州"),
                directExecutor);
        KernelRetrievalEngine engine = new KernelRetrievalEngine(new KernelRetrievalEnginePorts(
                multiChannelEngine, mcpOrchestrator, new ContractRetrievalFormatter(), directExecutor));

        RetrievalContext context = engine.retrieve(List.of(new SubQuestionIntent(
                QUESTION, List.of(kbIntentScore(), mcpIntentScore()))), TOP_K);

        Assertions.assertTrue(context.hasKb());
        Assertions.assertTrue(context.hasMcp());
        Assertions.assertTrue(context.getKbContext().contains("chunk-1"));
        Assertions.assertTrue(context.getMcpContext().contains("杭州：晴天"));
    }

    @Test
    void shouldTimeoutSlowChannelAndKeepFastChannelResult() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
            registerChannel(registry, slowKeywordChannel(), 1);
            registerChannel(registry, workingChannel(), 2);
            KernelRetrievalEngine engine = new KernelRetrievalEngine(registry, executor, activationContext());
            RetrievalOptions options = RetrievalOptions.builder()
                    .keywordTimeout(Duration.ofMillis(100))
                    .vectorTimeout(Duration.ofSeconds(2))
                    .build();

            long startedAt = System.currentTimeMillis();
            List<RetrievedChunk> chunks = engine.retrieveKnowledgeChannels(singleQuestionIntent(), TOP_K, null, options);
            long elapsedMs = System.currentTimeMillis() - startedAt;

            Assertions.assertTrue(elapsedMs < 800, "慢通道应被通道级超时截断");
            Assertions.assertEquals(1, chunks.size());
            Assertions.assertEquals("chunk-1", chunks.get(0).getId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAttachExpandedTermsToKeywordRetrievalContext() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        RecordingKeywordContextChannel keywordChannel = new RecordingKeywordContextChannel();
        registerChannel(registry, keywordChannel, 1);
        KernelRetrievalEngine engine = new KernelRetrievalEngine(registry, directExecutor, activationContext());
        QueryOptimizationResult optimizationResult = new QueryOptimizationResult(
                QUESTION,
                QUESTION,
                Map.of(),
                List.of("Pulsar", "Kafka"),
                List.of("term_expansion"));

        engine.retrieve(singleQuestionIntent(), TOP_K, null, optimizationResult);

        Assertions.assertTrue(keywordChannel.keywordEnabled);
        Assertions.assertEquals(QUESTION, keywordChannel.mainQuestion);
        Assertions.assertEquals(List.of("Pulsar", "Kafka"), keywordChannel.expandedTerms);
    }

    @Test
    void shouldApplyDefaultEmbeddingModelWhenRetrievalOptionsAreAbsent() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        RecordingEmbeddingModelChannel channel = new RecordingEmbeddingModelChannel();
        registerChannel(registry, channel, 1);
        KernelMultiChannelRetrievalEngine multiChannelEngine =
                new KernelMultiChannelRetrievalEngine(registry, directExecutor, activationContext(),
                        "nomic-embed-text");
        KernelRetrievalEngine engine = new KernelRetrievalEngine(multiChannelEngine);

        engine.retrieveKnowledgeChannels(singleQuestionIntent(), TOP_K);

        Assertions.assertEquals("nomic-embed-text", channel.embeddingModel);
    }

    @Test
    void shouldRecordChannelResultMetadataInTraceExtraData() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        RecordingEmbeddingModelChannel channel = new RecordingEmbeddingModelChannel();
        registerChannel(registry, channel, 1);
        RecordingTraceRepository traceRepository = new RecordingTraceRepository();
        KernelRagTraceRecorder traceRecorder = new KernelRagTraceRecorder(traceRepository);
        KernelMultiChannelRetrievalEngine multiChannelEngine =
                new KernelMultiChannelRetrievalEngine(registry,
                        directExecutor,
                        activationContext(),
                        MetadataSchemaRegistryPort.empty(),
                        new DefaultMetadataFilterCompiler(),
                        traceRecorder,
                        ObservationPort.noop(),
                        MetadataSchemaUsageReportRepositoryPort.empty(),
                        "nomic-embed-text");
        TraceRunScope runScope = traceRecorder.startRun(new TraceRunStartCommand(
                "stream-chat", "KernelChatInboundService#streamChat", "conv-1", "task-1", "user-1"));

        multiChannelEngine.retrieveKnowledgeChannels(singleQuestionIntent(), TOP_K, null, null, runScope);

        Assertions.assertEquals(1, traceRepository.finishedNodes.size());
        String extraData = traceRepository.finishedNodes.get(0).extraData();
        Assertions.assertTrue(extraData.contains("\"metadata\""));
        Assertions.assertTrue(extraData.contains("\"embeddingModel\":\"nomic-embed-text\""));
        Assertions.assertTrue(extraData.contains("\"collectionCount\":1"));
    }

    @Test
    void shouldApplyDefaultEmbeddingModelToExpandedRetrievalOptions() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        RecordingEmbeddingModelChannel channel = new RecordingEmbeddingModelChannel();
        registerChannel(registry, channel, 1);
        KernelMultiChannelRetrievalEngine multiChannelEngine =
                new KernelMultiChannelRetrievalEngine(registry, directExecutor, activationContext(),
                        "nomic-embed-text");
        KernelRetrievalEngine engine = new KernelRetrievalEngine(multiChannelEngine);
        QueryOptimizationResult optimizationResult = new QueryOptimizationResult(
                QUESTION,
                QUESTION,
                Map.of(),
                List.of("Pulsar"),
                List.of("term_expansion"));

        engine.retrieve(singleQuestionIntent(), TOP_K, null, optimizationResult);

        Assertions.assertEquals("nomic-embed-text", channel.embeddingModel);
        Assertions.assertEquals(List.of("Pulsar"), channel.expandedTerms);
    }

    private void registerChannel(DefaultExtensionRegistry registry, SearchChannelFeature feature, int order) {
        registry.register(new ExtensionDescriptor(feature.name(), SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, order, order == 1), feature);
    }

    private void registerPostProcessor(DefaultExtensionRegistry registry,
                                       SearchResultPostProcessorFeature feature,
                                       int order) {
        registry.register(new ExtensionDescriptor(feature.name(), SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, order, order == 1), feature);
    }

    private FeatureActivationContext activationContext() {
        return new FeatureActivationContext("tenant-a", "user-a", Map.of(), AgentFeatureProperties.empty());
    }

    private List<SubQuestionIntent> singleQuestionIntent() {
        return List.of(new SubQuestionIntent(QUESTION, List.of()));
    }

    private IntentScore kbIntentScore() {
        return IntentScore.builder()
                .node(IntentNode.builder()
                        .id("kb-node")
                        .kind(IntentKind.KB)
                        .topK(2)
                        .build())
                .score(0.9D)
                .build();
    }

    private IntentScore mcpIntentScore() {
        return IntentScore.builder()
                .node(IntentNode.builder()
                        .id("mcp-node")
                        .kind(IntentKind.MCP)
                        .mcpToolId(MCP_TOOL_ID)
                        .build())
                .score(0.91D)
                .build();
    }

    private SearchChannelFeature failingChannel() {
        return new ContractSearchChannelFeature(FAILING_CHANNEL, SearchChannelType.KEYWORD_BM25, true);
    }

    private SearchChannelFeature workingChannel() {
        return new ContractSearchChannelFeature(WORKING_CHANNEL, SearchChannelType.VECTOR_GLOBAL, false);
    }

    private SearchChannelFeature slowKeywordChannel() {
        return new SearchChannelFeature() {
            @Override
            public String name() {
                return "slow-keyword-channel";
            }

            @Override
            public SearchChannelType channelType() {
                return SearchChannelType.KEYWORD_BM25;
            }

            @Override
            public boolean enabled(SearchContext context) {
                return true;
            }

            @Override
            public SearchChannelResult search(SearchContext context) {
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return SearchChannelResult.builder()
                        .channelType(channelType())
                        .channelName(name())
                        .chunks(List.of(RetrievedChunk.builder()
                                .id("slow-chunk")
                                .text("慢关键词结果")
                                .score(0.1F)
                                .build()))
                        .latencyMs(1_000L)
                        .build();
            }
        };
    }

    private SearchResultPostProcessorFeature recordingPostProcessor(List<SearchChannelResult> observedResults) {
        return new ContractPostProcessorFeature("recorder", false, chunks -> chunks) {
            @Override
            public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                                List<SearchChannelResult> results,
                                                SearchContext context) {
                observedResults.addAll(results);
                return chunks;
            }
        };
    }

    private SearchResultPostProcessorFeature failingPostProcessor(List<String> processorOrder) {
        return new ContractPostProcessorFeature(FIRST_PROCESSOR, true, chunks -> {
            processorOrder.add(FIRST_PROCESSOR);
            return chunks;
        });
    }

    private SearchResultPostProcessorFeature appendingPostProcessor(List<String> processorOrder) {
        return new ContractPostProcessorFeature(SECOND_PROCESSOR, false, chunks -> {
            processorOrder.add(SECOND_PROCESSOR);
            List<RetrievedChunk> appended = new ArrayList<>(chunks);
            appended.add(RetrievedChunk.builder()
                    .id("post-processor-extra")
                    .text("后处理器追加的验证结果")
                    .score(0.5F)
                    .build());
            return appended;
        });
    }

    private record SingleMcpToolRegistry(McpToolExecutorPort executor) implements McpToolRegistryPort {

        @Override
        public Optional<McpToolExecutorPort> findExecutor(String toolId) {
            if (MCP_TOOL_ID.equals(toolId)) {
                return Optional.of(executor);
            }
            return Optional.empty();
        }

        @Override
        public Optional<McpToolDescriptor> findTool(String toolId) {
            if (MCP_TOOL_ID.equals(toolId)) {
                return Optional.of(new McpToolDescriptor(MCP_TOOL_ID, "天气查询", Map.of()));
            }
            return Optional.empty();
        }
    }

    private static class ContractRetrievalFormatter implements RetrievalContextFormatPort {

        @Override
        public String formatKbContext(List<IntentScore> kbIntents,
                                      Map<String, List<RetrievedChunk>> intentChunks,
                                      int topK) {
            return intentChunks.values().stream()
                    .flatMap(List::stream)
                    .map(RetrievedChunk::getId)
                    .findFirst()
                    .orElse("");
        }

        @Override
        public String formatMcpContext(List<McpToolExecutionResult> results, List<IntentScore> mcpIntents) {
            return results.stream()
                    .filter(McpToolExecutionResult::success)
                    .map(McpToolExecutionResult::content)
                    .findFirst()
                    .orElse("");
        }
    }

    /**
     * 测试专用检索通道 Feature。
     * <p>
     * 通过 throwsOnSearch 精确模拟单通道故障，验证内核检索编排不会被单点失败中断。
     */
    private record ContractSearchChannelFeature(String name,
                                                SearchChannelType channelType,
                                                boolean throwsOnSearch) implements SearchChannelFeature {

        @Override
        public boolean enabled(SearchContext context) {
            return true;
        }

        @Override
        public SearchChannelResult search(SearchContext context) {
            if (throwsOnSearch) {
                throw new IllegalStateException("模拟通道故障");
            }
            return SearchChannelResult.builder()
                    .channelType(channelType)
                    .channelName(name)
                    .chunks(List.of(RetrievedChunk.builder()
                            .id("chunk-1")
                            .text("入职流程说明")
                            .score(0.9F)
                            .build()))
                    .latencyMs(1L)
                    .build();
        }
    }

    /**
     * 测试专用后处理器 Feature。
     * <p>
     * 用于验证后处理器链的异常跳过和继续执行语义。
     */
    private static class ContractPostProcessorFeature implements SearchResultPostProcessorFeature {

        private final String name;
        private final boolean throwsOnProcess;
        private final ChunkProcessor chunkProcessor;

        private ContractPostProcessorFeature(String name, boolean throwsOnProcess, ChunkProcessor chunkProcessor) {
            this.name = name;
            this.throwsOnProcess = throwsOnProcess;
            this.chunkProcessor = chunkProcessor;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean enabled(SearchContext context) {
            return true;
        }

        @Override
        public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                            List<SearchChannelResult> results,
                                            SearchContext context) {
            List<RetrievedChunk> processed = chunkProcessor.process(chunks);
            if (throwsOnProcess) {
                throw new IllegalStateException("模拟后处理器故障");
            }
            return processed;
        }
    }

    private static class RecordingKeywordContextChannel implements SearchChannelFeature {

        private boolean keywordEnabled;
        private String mainQuestion;
        private List<String> expandedTerms = List.of();

        @Override
        public String name() {
            return "recording-keyword-context";
        }

        @Override
        public SearchChannelType channelType() {
            return SearchChannelType.KEYWORD_BM25;
        }

        @Override
        public boolean enabled(SearchContext context) {
            return context != null && context.effectiveOptions().enableKeyword();
        }

        @Override
        public SearchChannelResult search(SearchContext context) {
            keywordEnabled = context.effectiveOptions().enableKeyword();
            mainQuestion = context.getMainQuestion();
            Object metadataValue = context.getMetadata().get(SearchContext.METADATA_QUERY_EXPANDED_TERMS);
            if (metadataValue instanceof List<?> values) {
                expandedTerms = values.stream().map(Object::toString).toList();
            }
            return SearchChannelResult.builder()
                    .channelType(channelType())
                    .channelName(name())
                    .chunks(List.of(RetrievedChunk.builder()
                            .id("keyword-context-chunk")
                            .text("关键词扩展结果")
                            .score(0.8F)
                            .build()))
                    .latencyMs(1L)
                    .build();
        }
    }

    private static class RecordingEmbeddingModelChannel implements SearchChannelFeature {

        private String embeddingModel;
        private List<String> expandedTerms = List.of();

        @Override
        public String name() {
            return "recording-embedding-model";
        }

        @Override
        public SearchChannelType channelType() {
            return SearchChannelType.VECTOR_GLOBAL;
        }

        @Override
        public boolean enabled(SearchContext context) {
            return true;
        }

        @Override
        public SearchChannelResult search(SearchContext context) {
            embeddingModel = context.effectiveOptions().embeddingModel();
            Object metadataValue = context.getMetadata().get(SearchContext.METADATA_QUERY_EXPANDED_TERMS);
            if (metadataValue instanceof List<?> values) {
                expandedTerms = values.stream().map(Object::toString).toList();
            }
            return SearchChannelResult.builder()
                    .channelType(channelType())
                    .channelName(name())
                    .chunks(List.of(RetrievedChunk.builder()
                            .id("embedding-model-context-chunk")
                            .text("embedding model context")
                            .score(0.8F)
                            .build()))
                    .metadata(Map.of("embeddingModel", embeddingModel, "collectionCount", 1))
                    .latencyMs(1L)
                    .build();
        }
    }

    private static final class RecordingTraceRepository implements RagTraceRepositoryPort {

        private final List<RagTraceNodeFinish> finishedNodes = new ArrayList<>();

        @Override
        public RagTracePage<RagTraceRun> pageRuns(RagTracePageRequest request) {
            return new RagTracePage<>(1, 10, 0, List.of());
        }

        @Override
        public Optional<RagTraceRun> findRun(String traceId) {
            return Optional.empty();
        }

        @Override
        public List<RagTraceNode> listNodes(String traceId) {
            return List.of();
        }

        @Override
        public void startRun(RagTraceRun run) {
        }

        @Override
        public void finishRun(RagTraceRunFinish finish) {
        }

        @Override
        public void startNode(RagTraceNode node) {
        }

        @Override
        public void finishNode(RagTraceNodeFinish finish) {
            finishedNodes.add(finish);
        }
    }

    @FunctionalInterface
    private interface ChunkProcessor {

        List<RetrievedChunk> process(List<RetrievedChunk> chunks);
    }
}
