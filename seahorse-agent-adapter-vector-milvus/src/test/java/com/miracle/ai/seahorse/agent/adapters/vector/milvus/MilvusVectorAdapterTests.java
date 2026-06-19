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

package com.miracle.ai.seahorse.agent.adapters.vector.milvus;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldContains;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldNe;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.MetadataFilterExpr;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusVectorAdapterTests {

    @Test
    void shouldPushDownAclSubjectsIntoJsonFilter() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.search(any(SearchReq.class))).thenReturn(SearchResp.builder().searchResults(List.of()).build());
        MilvusVectorAdapter adapter = new MilvusVectorAdapter(client,
                new MilvusVectorProperties("default_collection", 2, "COSINE"));

        adapter.search(searchRequest());

        ArgumentCaptor<SearchReq> requestCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFilter())
                .contains("metadata[\"tenant_id\"] == \"tenant-1\"")
                .contains("json_contains_any(metadata[\"acl_subjects\"], [\"dept-a\", \"user-1\"])")
                .contains("(metadata[\"enabled\"] == true || metadata[\"enabled\"] == \"true\")");
    }

    @Test
    void shouldPushDownNotEqualsMetadataExpression() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.search(any(SearchReq.class))).thenReturn(SearchResp.builder().searchResults(List.of()).build());
        MilvusVectorAdapter adapter = new MilvusVectorAdapter(client,
                new MilvusVectorProperties("default_collection", 2, "COSINE"));

        adapter.search(searchRequest(new FieldNe(field("department"), "HR")));

        ArgumentCaptor<SearchReq> requestCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFilter())
                .contains("(metadata[\"department\"] != \"HR\" || metadata[\"department\"] == null)");
    }

    @Test
    void shouldPushDownArrayContainsMetadataExpression() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.search(any(SearchReq.class))).thenReturn(SearchResp.builder().searchResults(List.of()).build());
        MilvusVectorAdapter adapter = new MilvusVectorAdapter(client,
                new MilvusVectorProperties("default_collection", 2, "COSINE"));

        adapter.search(searchRequest(new FieldContains(field("tags", MetadataValueType.STRING_ARRAY), "hr")));

        ArgumentCaptor<SearchReq> requestCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFilter())
                .contains("json_contains(metadata[\"tags\"], \"hr\")");
    }

    @Test
    void shouldLeaveStringContainsForGuard() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.search(any(SearchReq.class))).thenReturn(SearchResp.builder().searchResults(List.of()).build());
        MilvusVectorAdapter adapter = new MilvusVectorAdapter(client,
                new MilvusVectorProperties("default_collection", 2, "COSINE"));

        adapter.search(searchRequest(new FieldContains(field("title"), "policy")));

        ArgumentCaptor<SearchReq> requestCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFilter())
                .doesNotContain("metadata[\"title\"] == \"policy\"")
                .doesNotContain("json_contains(metadata[\"title\"]");
    }

    @Test
    void shouldApplyConfigurableSearchEf() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.search(any(SearchReq.class))).thenReturn(SearchResp.builder().searchResults(List.of()).build());
        MilvusVectorAdapter adapter = new MilvusVectorAdapter(client,
                new MilvusVectorProperties("default_collection", 2, "COSINE", 32, 16, 96, true, 64));

        adapter.search(searchRequest());

        ArgumentCaptor<SearchReq> requestCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getSearchParams()).containsEntry("ef", 64);
    }

    @Test
    void shouldApplyConfigurableSchemaAndIndexParams() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any(HasCollectionReq.class))).thenReturn(false);
        MilvusVectorAdapter adapter = new MilvusVectorAdapter(client,
                new MilvusVectorProperties("default_collection", 2, "COSINE", 32, 16, 96, true, 64));

        adapter.ensureCollection("collection-a");

        ArgumentCaptor<CreateCollectionReq> requestCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(requestCaptor.capture());
        CreateCollectionReq request = requestCaptor.getValue();
        assertThat(request.getCollectionSchema().getField("content").getMaxLength()).isEqualTo(32);
        assertThat(request.getIndexParams().get(0).getExtraParams())
                .containsEntry("M", "16")
                .containsEntry("efConstruction", "96")
                .containsEntry("mmap.enabled", "true");
    }

    @Test
    void shouldTruncateContentByConfiguredMaxLength() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        MilvusVectorAdapter adapter = new MilvusVectorAdapter(client,
                new MilvusVectorProperties("default_collection", 2, "COSINE", 5, 16, 96, false, 64));

        adapter.indexDocumentChunks("collection-a", "doc-1", List.of(VectorChunk.builder()
                .chunkId("chunk-1")
                .index(0)
                .content("123456789")
                .embedding(new float[] {0.1F, 0.2F})
                .build()));

        ArgumentCaptor<InsertReq> requestCaptor = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getData().get(0).get("content").getAsString()).isEqualTo("12345");
    }

    @Test
    void shouldSkipDocumentVectorDeleteWhenCollectionIsMissing() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any(HasCollectionReq.class))).thenReturn(false);
        MilvusVectorAdapter adapter = new MilvusVectorAdapter(client,
                new MilvusVectorProperties("default_collection", 2, "COSINE"));

        adapter.deleteDocumentVectors("missing_collection", "doc-1");

        verify(client, never()).delete(any(DeleteReq.class));
    }

    @Test
    void shouldDeleteDocumentVectorsWhenCollectionExists() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any(HasCollectionReq.class))).thenReturn(true);
        MilvusVectorAdapter adapter = new MilvusVectorAdapter(client,
                new MilvusVectorProperties("default_collection", 2, "COSINE"));

        adapter.deleteDocumentVectors("collection-a", "doc-1");

        ArgumentCaptor<DeleteReq> requestCaptor = ArgumentCaptor.forClass(DeleteReq.class);
        verify(client).delete(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCollectionName()).isEqualTo("collection-a");
        assertThat(requestCaptor.getValue().getFilter()).contains("metadata[\"doc_id\"] == \"doc-1\"");
    }

    private VectorSearchRequest searchRequest() {
        return searchRequest(new FilterAnd(List.of()));
    }

    private VectorSearchRequest searchRequest(MetadataFilterExpr expression) {
        SystemRetrievalFilter system = SystemRetrievalFilter.builder()
                .tenantId("tenant-1")
                .aclSubjectIds(List.of("dept-a", "user-1"))
                .enabledOnly(true)
                .build();
        RetrievalFilter filter = RetrievalFilter.builder().system(system).build();
        CompiledMetadataFilter compiledFilter = new CompiledMetadataFilter(
                filter,
                expression,
                List.of(),
                List.of());
        return new VectorSearchRequest(
                "collection-a",
                "query",
                List.of(0.1F, 0.2F),
                3,
                Map.of(),
                compiledFilter);
    }

    private MetadataFieldDescriptor field(String fieldKey) {
        return field(fieldKey, MetadataValueType.STRING);
    }

    private MetadataFieldDescriptor field(String fieldKey, MetadataValueType valueType) {
        return new MetadataFieldDescriptor(fieldKey, fieldKey, valueType,
                Set.of(MetadataOperator.EQ, MetadataOperator.NE, MetadataOperator.CONTAINS), false, true, false, false, true,
                MetadataIndexPolicy.MILVUS_JSON, 0.8D, Set.of(), Map.of(), BackendFieldMapping.defaults(fieldKey));
    }
}
