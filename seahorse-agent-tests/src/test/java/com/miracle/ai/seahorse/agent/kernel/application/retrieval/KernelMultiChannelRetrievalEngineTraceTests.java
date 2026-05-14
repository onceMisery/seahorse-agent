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
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.DefaultMetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchChannelFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchResultPostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMultiChannelRetrievalEngineTraceTests {

    @Test
    void shouldRecordSearchChannelAndPostProcessorNodes() {
        RecordingTraceRepository repository = new RecordingTraceRepository();
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registry.register(new ExtensionDescriptor("test-channel", SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, 1, true), new TestChannel(false));
        registry.register(new ExtensionDescriptor("test-processor", SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, 10, true), new TestProcessor());
        KernelMultiChannelRetrievalEngine engine = engine(registry, repository);

        List<RetrievedChunk> chunks = engine.retrieveKnowledgeChannels(
                List.of(new SubQuestionIntent("问题", List.of())),
                3,
                TraceRunScope.active("trace-1", Instant.now()));

        assertThat(chunks).extracting(RetrievedChunk::getId).containsExactly("chunk-1");
        assertThat(repository.startedNodes)
                .extracting(RagTraceNode::getNodeName)
                .containsExactly("search-channel:test-channel", "post-processor:test-processor");
        assertThat(repository.startedNodes)
                .extracting(RagTraceNode::getNodeType)
                .containsExactly("RETRIEVAL_CHANNEL", "RETRIEVAL_POST_PROCESSOR");
        assertThat(repository.finishedNodes)
                .extracting(RagTraceNodeFinish::status)
                .containsExactly(KernelRagTraceRecorder.STATUS_SUCCESS, KernelRagTraceRecorder.STATUS_SUCCESS);
    }

    @Test
    void shouldMarkChannelTraceFailedAndKeepEmptyResultFallback() {
        RecordingTraceRepository repository = new RecordingTraceRepository();
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registry.register(new ExtensionDescriptor("test-channel", SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, 1, true), new TestChannel(true));
        KernelMultiChannelRetrievalEngine engine = engine(registry, repository);

        List<RetrievedChunk> chunks = engine.retrieveKnowledgeChannels(
                List.of(new SubQuestionIntent("问题", List.of())),
                3,
                TraceRunScope.active("trace-1", Instant.now()));

        assertThat(chunks).isEmpty();
        assertThat(repository.finishedNodes).hasSize(1);
        assertThat(repository.finishedNodes.get(0).status()).isEqualTo(KernelRagTraceRecorder.STATUS_FAILED);
        assertThat(repository.finishedNodes.get(0).errorMessage()).contains("channel failed");
    }

    private KernelMultiChannelRetrievalEngine engine(DefaultExtensionRegistry registry,
                                                     RecordingTraceRepository repository) {
        return new KernelMultiChannelRetrievalEngine(
                registry,
                Runnable::run,
                FeatureActivationContext.empty(),
                MetadataSchemaRegistryPort.empty(),
                new DefaultMetadataFilterCompiler(),
                new KernelRagTraceRecorder(repository));
    }

    private static class TestChannel implements SearchChannelFeature {

        private final boolean shouldFail;

        private TestChannel(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        @Override
        public String name() {
            return "test-channel";
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
            if (shouldFail) {
                throw new IllegalStateException("channel failed");
            }
            return SearchChannelResult.builder()
                    .channelType(channelType())
                    .channelName(name())
                    .chunks(List.of(RetrievedChunk.builder().id("chunk-1").text("命中内容").build()))
                    .build();
        }
    }

    private static class TestProcessor implements SearchResultPostProcessorFeature {

        @Override
        public String name() {
            return "test-processor";
        }

        @Override
        public int order() {
            return 10;
        }

        @Override
        public boolean enabled(SearchContext context) {
            return true;
        }

        @Override
        public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                            List<SearchChannelResult> results,
                                            SearchContext context) {
            return chunks;
        }
    }

    private static final class RecordingTraceRepository implements RagTraceRepositoryPort {

        private final List<RagTraceNode> startedNodes = new ArrayList<>();
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
            startedNodes.add(node);
        }

        @Override
        public void finishNode(RagTraceNodeFinish finish) {
            finishedNodes.add(finish);
        }
    }
}
