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

import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelMultiChannelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.MetadataCondition;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldNe;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeatureProperties;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataRetrievalFilterTests {

    @Test
    void shouldRejectUnregisteredMetadataFilterField() {
        DefaultMetadataFilterCompiler compiler = new DefaultMetadataFilterCompiler();
        RetrievalFilter filter = RetrievalFilter.builder()
                .metadataConditions(List.of(new MetadataCondition("unknown", MetadataOperator.EQ, "HR")))
                .build();

        assertThatThrownBy(() -> compiler.compile(filter, MetadataSchema.empty("tenant-a", "kb-a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void shouldRejectTooManyMetadataFilterConditions() {
        DefaultMetadataFilterCompiler compiler = new DefaultMetadataFilterCompiler();
        List<MetadataCondition> conditions = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            conditions.add(new MetadataCondition("department", MetadataOperator.EQ, "HR"));
        }
        RetrievalFilter filter = RetrievalFilter.builder()
                .metadataConditions(conditions)
                .build();

        assertThatThrownBy(() -> compiler.compile(filter, schema(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("condition count exceeds limit");
    }

    @Test
    void shouldGuardSystemAndMetadataConditionsAfterSearch() {
        DefaultMetadataFilterCompiler compiler = new DefaultMetadataFilterCompiler();
        RetrievalFilter filter = RetrievalFilter.builder()
                .system(SystemRetrievalFilter.builder()
                        .tenantId("tenant-a")
                        .knowledgeBaseIds(List.of("kb-a"))
                        .enabledOnly(true)
                        .build())
                .metadataConditions(List.of(new MetadataCondition("department", MetadataOperator.EQ, "HR")))
                .build();
        CompiledMetadataFilter compiledFilter = compiler.compile(filter, schema(false));
        MetadataGuardPostProcessorFeature guard = new MetadataGuardPostProcessorFeature();
        SearchContext context = SearchContext.builder()
                .filter(filter)
                .compiledFilter(compiledFilter)
                .build();

        List<RetrievedChunk> chunks = guard.process(List.of(
                chunk("1", "tenant-a", "kb-a", "HR", true),
                chunk("2", "tenant-a", "kb-a", "FIN", true),
                chunk("3", "tenant-b", "kb-a", "HR", true),
                chunk("4", "tenant-a", "kb-a", "HR", false)), List.of(), context);

        assertThat(chunks).extracting(RetrievedChunk::getId).containsExactly("1");
    }

    @Test
    void shouldCompileNotEqualsAndGuardWithSameSemantics() {
        DefaultMetadataFilterCompiler compiler = new DefaultMetadataFilterCompiler();
        RetrievalFilter filter = RetrievalFilter.builder()
                .metadataConditions(List.of(new MetadataCondition("department", MetadataOperator.NE, "HR")))
                .build();
        CompiledMetadataFilter compiledFilter = compiler.compile(filter,
                schema(true, Set.of(MetadataOperator.EQ, MetadataOperator.NE)));
        MetadataGuardPostProcessorFeature guard = new MetadataGuardPostProcessorFeature();
        SearchContext context = SearchContext.builder()
                .filter(filter)
                .compiledFilter(compiledFilter)
                .build();

        assertThat(compiledFilter.expression()).isInstanceOfSatisfying(FilterAnd.class,
                expression -> assertThat(expression.children()).singleElement().isInstanceOf(FieldNe.class));
        List<RetrievedChunk> chunks = guard.process(List.of(
                chunk("1", "tenant-a", "kb-a", "HR", true),
                chunk("2", "tenant-a", "kb-a", "FIN", true),
                chunk("3", "tenant-a", "kb-a", "", true)), List.of(), context);

        assertThat(chunks).extracting(RetrievedChunk::getId).containsExactly("2", "3");
    }

    @Test
    void shouldFallbackBlankBackendCanonicalNameToFieldKey() {
        MetadataFieldDescriptor field = new MetadataFieldDescriptor(
                "department", "部门", MetadataValueType.STRING, Set.of(MetadataOperator.EQ),
                false, true, false, false, true, MetadataIndexPolicy.JSON_GIN, 0.8D,
                Set.of("source"), Map.of(), new BackendFieldMapping("", "", "", "",
                true, true, false, Map.of()));
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 1, List.of(field));
        RetrievalFilter filter = RetrievalFilter.builder()
                .metadataConditions(List.of(new MetadataCondition("department", MetadataOperator.EQ, "HR")))
                .build();
        CompiledMetadataFilter compiledFilter = new DefaultMetadataFilterCompiler().compile(filter, schema);
        SearchContext context = SearchContext.builder()
                .filter(filter)
                .compiledFilter(compiledFilter)
                .build();

        assertThat(field.backendMapping().canonicalName()).isEqualTo("department");
        assertThat(field.backendMapping().searchFieldName()).isEqualTo("department");
        List<RetrievedChunk> chunks = new MetadataGuardPostProcessorFeature().process(List.of(
                chunk("1", "tenant-a", "kb-a", "HR", true),
                chunk("2", "tenant-a", "kb-a", "FIN", true)), List.of(), context);

        assertThat(chunks).extracting(RetrievedChunk::getId).containsExactly("1");
    }

    @Test
    void shouldGenerateQueryEmbeddingForVectorGlobalSearch() {
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort();
        VectorGlobalSearchFeature feature = new VectorGlobalSearchFeature(
                new StaticKnowledgeBaseQueryPort(List.of(new KnowledgeBaseRef("kb-a", "知识库", "collection-a"))),
                vectorSearchPort,
                (modelId, text) -> List.of(0.1F, 0.2F, 0.3F));

        feature.search(SearchContext.builder()
                .rewrittenQuestion("如何办理入职")
                .options(RetrievalOptions.builder().embeddingModel("embedding-model").build())
                .topK(2)
                .build());

        assertThat(vectorSearchPort.requests).hasSize(1);
        assertThat(vectorSearchPort.requests.get(0).vector()).containsExactly(0.1F, 0.2F, 0.3F);
    }

    @Test
    void shouldCompileFilterBeforeExecutingSearchChannels() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        RecordingSearchChannelFeature channel = new RecordingSearchChannelFeature();
        MetadataGuardPostProcessorFeature guard = new MetadataGuardPostProcessorFeature();
        RecordingObservationPort observationPort = new RecordingObservationPort();
        registry.register(new ExtensionDescriptor(channel.name(), SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, 1, true), channel);
        registry.register(new ExtensionDescriptor(guard.name(), SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, guard.order(), true), guard);
        KernelMultiChannelRetrievalEngine engine = new KernelMultiChannelRetrievalEngine(
                registry,
                Runnable::run,
                new FeatureActivationContext("tenant-a", "user-a", Map.of(), AgentFeatureProperties.empty()),
                (tenantId, knowledgeBaseId) -> schema(false),
                new DefaultMetadataFilterCompiler(),
                KernelRagTraceRecorder.noop(),
                observationPort);
        RetrievalFilter filter = RetrievalFilter.builder()
                .system(SystemRetrievalFilter.builder().tenantId("tenant-a").knowledgeBaseIds(List.of("kb-a")).build())
                .metadataConditions(List.of(new MetadataCondition("department", MetadataOperator.EQ, "HR")))
                .build();

        List<RetrievedChunk> chunks = engine.retrieveKnowledgeChannels(
                List.of(new SubQuestionIntent("入职流程", List.of())), 5, filter, RetrievalOptions.defaults(5));

        assertThat(channel.observedCompiledFilter).isNotNull();
        assertThat(channel.observedCompiledFilter.guardOnlyConditions()).hasSize(1);
        assertThat(chunks).extracting(RetrievedChunk::getId).containsExactly("1");
        assertThat(observationPort.events)
                .extracting(ObservationEvent::name)
                .contains("retrieval.metadata.filter.compiled", "retrieval.channel.completed",
                        "retrieval.metadata.guard");
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("retrieval.metadata.filter.compiled"))
                .singleElement()
                .satisfies(event -> {
            assertThat(event.name()).isEqualTo("retrieval.metadata.filter.compiled");
            assertThat(event.attributes())
                    .containsEntry("tenantId", "tenant-a")
                    .containsEntry("knowledgeBaseId", "kb-a")
                    .containsEntry("fieldKeys", "department")
                    .containsEntry("guardOnlyFieldKeys", "department");
        });
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("retrieval.channel.completed"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("channelName", "recording-search")
                        .containsEntry("channelType", "VECTOR_GLOBAL")
                        .containsEntry("hitCount", "2")
                        .containsEntry("success", "true"));
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("retrieval.metadata.guard"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("tenantId", "tenant-a")
                        .containsEntry("knowledgeBaseId", "kb-a")
                        .containsEntry("inputCount", "2")
                        .containsEntry("outputCount", "1")
                        .containsEntry("filteredCount", "1")
                        .containsEntry("success", "true"));
    }

    @Test
    void shouldRecordChannelFailureObservationWhenChannelFallsBack() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        FailingSearchChannelFeature channel = new FailingSearchChannelFeature();
        RecordingObservationPort observationPort = new RecordingObservationPort();
        registry.register(new ExtensionDescriptor(channel.name(), SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, 1, true), channel);
        KernelMultiChannelRetrievalEngine engine = new KernelMultiChannelRetrievalEngine(
                registry,
                Runnable::run,
                new FeatureActivationContext("tenant-a", "user-a", Map.of(), AgentFeatureProperties.empty()),
                MetadataSchemaRegistryPort.empty(),
                new DefaultMetadataFilterCompiler(),
                KernelRagTraceRecorder.noop(),
                observationPort);

        List<RetrievedChunk> chunks = engine.retrieveKnowledgeChannels(
                List.of(new SubQuestionIntent("关键词检索失败", List.of())), 5);

        assertThat(chunks).isEmpty();
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("retrieval.channel.completed"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("tenantId", "tenant-a")
                        .containsEntry("channelName", "failing-keyword")
                        .containsEntry("channelType", "KEYWORD_BM25")
                        .containsEntry("hitCount", "0")
                        .containsEntry("success", "false")
                        .containsEntry("exception", "IllegalStateException"));
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("retrieval.empty"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("tenantId", "tenant-a")
                        .containsEntry("stage", "channel")
                        .containsEntry("reason", "channels_returned_empty")
                        .containsEntry("channelCount", "1")
                        .containsEntry("candidateCount", "0"));
    }

    @Test
    void shouldRecordEmptyRetrievalWhenMetadataGuardFiltersAllCandidates() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        RecordingSearchChannelFeature channel = new RecordingSearchChannelFeature();
        MetadataGuardPostProcessorFeature guard = new MetadataGuardPostProcessorFeature();
        RecordingObservationPort observationPort = new RecordingObservationPort();
        registry.register(new ExtensionDescriptor(channel.name(), SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, 1, true), channel);
        registry.register(new ExtensionDescriptor(guard.name(), SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, guard.order(), true), guard);
        KernelMultiChannelRetrievalEngine engine = new KernelMultiChannelRetrievalEngine(
                registry,
                Runnable::run,
                new FeatureActivationContext("tenant-a", "user-a", Map.of(), AgentFeatureProperties.empty()),
                (tenantId, knowledgeBaseId) -> schema(false),
                new DefaultMetadataFilterCompiler(),
                KernelRagTraceRecorder.noop(),
                observationPort);
        RetrievalFilter filter = RetrievalFilter.builder()
                .system(SystemRetrievalFilter.builder().tenantId("tenant-a").knowledgeBaseIds(List.of("kb-a")).build())
                .metadataConditions(List.of(new MetadataCondition("department", MetadataOperator.EQ, "LEGAL")))
                .build();

        List<RetrievedChunk> chunks = engine.retrieveKnowledgeChannels(
                List.of(new SubQuestionIntent("法务制度", List.of())), 5, filter, RetrievalOptions.defaults(5));

        assertThat(chunks).isEmpty();
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("retrieval.empty"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("tenantId", "tenant-a")
                        .containsEntry("knowledgeBaseId", "kb-a")
                        .containsEntry("stage", "post_processor")
                        .containsEntry("reason", "post_processor_filtered_all")
                        .containsEntry("channelCount", "1")
                        .containsEntry("candidateCount", "2")
                        .containsEntry("filterApplied", "true"));
    }

    private MetadataSchema schema(boolean pushdownToVector) {
        return schema(pushdownToVector, Set.of(MetadataOperator.EQ));
    }

    private MetadataSchema schema(boolean pushdownToVector, Set<MetadataOperator> allowedOperators) {
        return new MetadataSchema("tenant-a", "kb-a", 1, List.of(new MetadataFieldDescriptor(
                "department",
                "部门",
                MetadataValueType.STRING,
                allowedOperators,
                false,
                true,
                false,
                false,
                true,
                MetadataIndexPolicy.JSON_GIN,
                0.8D,
                Set.of("source"),
                Map.of(),
                new BackendFieldMapping("department", "metadata[\"department\"]", "metadata->>'department'",
                        "department", pushdownToVector, true, !pushdownToVector, Map.of()))));
    }

    private RetrievedChunk chunk(String id, String tenantId, String kbId, String department, boolean enabled) {
        return RetrievedChunk.builder()
                .id(id)
                .tenantId(tenantId)
                .kbId(kbId)
                .metadata(Map.of(
                        "tenant_id", tenantId,
                        "kb_id", kbId,
                        "department", department,
                        "enabled", enabled))
                .build();
    }

    private record StaticKnowledgeBaseQueryPort(List<KnowledgeBaseRef> refs) implements KnowledgeBaseQueryPort {

        @Override
        public List<KnowledgeBaseRef> listSearchableKnowledgeBases() {
            return refs;
        }

        @Override
        public List<KnowledgeDocumentSummary> searchDocuments(String keyword, int limit) {
            return List.of();
        }

        @Override
        public List<KnowledgeChunkSummary> listChunksByDocId(String docId) {
            return List.of();
        }
    }

    private static class RecordingVectorSearchPort implements com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort {

        private final List<VectorSearchRequest> requests = new ArrayList<>();

        @Override
        public List<RetrievedChunk> search(VectorSearchRequest request) {
            requests.add(request);
            return List.of();
        }
    }

    private static class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    events.add(event);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }

    private static class RecordingSearchChannelFeature implements SearchChannelFeature {

        private CompiledMetadataFilter observedCompiledFilter;

        @Override
        public String name() {
            return "recording-search";
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
            observedCompiledFilter = context.getCompiledFilter();
            return SearchChannelResult.builder()
                    .channelType(channelType())
                    .channelName(name())
                    .chunks(List.of(
                            RetrievedChunk.builder()
                                    .id("1")
                                    .tenantId("tenant-a")
                                    .kbId("kb-a")
                                    .metadata(Map.of("tenant_id", "tenant-a", "kb_id", "kb-a",
                                            "department", "HR"))
                                    .build(),
                            RetrievedChunk.builder()
                                    .id("2")
                                    .tenantId("tenant-a")
                                    .kbId("kb-a")
                                    .metadata(Map.of("tenant_id", "tenant-a", "kb_id", "kb-a",
                                            "department", "FIN"))
                                    .build()))
                    .build();
        }
    }

    private static class FailingSearchChannelFeature implements SearchChannelFeature {

        @Override
        public String name() {
            return "failing-keyword";
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
            throw new IllegalStateException("keyword backend unavailable");
        }
    }
}
