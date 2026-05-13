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
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
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

    private MetadataSchema schema(boolean pushdownToVector) {
        return new MetadataSchema("tenant-a", "kb-a", 1, List.of(new MetadataFieldDescriptor(
                "department",
                "部门",
                MetadataValueType.STRING,
                Set.of(MetadataOperator.EQ),
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
}
