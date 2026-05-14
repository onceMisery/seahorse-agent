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

package com.miracle.ai.seahorse.agent.adapters.search.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldEq;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchKeywordSearchAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildSearchRequestFromCompiledFilterAndParseHits() throws Exception {
        CapturingInterceptor interceptor = new CapturingInterceptor("""
                {"hits":{"hits":[{"_id":"chunk-1","_score":3.5,
                "_source":{"chunk_id":"chunk-1","kb_id":"kb-1","doc_id":"doc-1","chunk_index":2,
                "content":"混合检索","tenant_id":"tenant-1","collection_name":"default",
                "metadata":{"category":"policy"},"enabled":true}}]}}
                """);
        ElasticsearchKeywordSearchAdapter adapter = new ElasticsearchKeywordSearchAdapter(
                new OkHttpClient.Builder().addInterceptor(interceptor).build(),
                objectMapper,
                new ElasticsearchKeywordProperties("http://localhost:9200", "chunks",
                        List.of("content^2", "title"), "ik_smart", "2<75%",
                        "", "", "", Duration.ofSeconds(3)));

        var results = adapter.search(new KeywordSearchRequest("metadata search", 7, null, null,
                compiledFilter()));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("chunk-1");
        assertThat(results.get(0).getKbId()).isEqualTo("kb-1");
        assertThat(results.get(0).getMetadata()).containsEntry("category", "policy");

        JsonNode body = objectMapper.readTree(interceptor.body());
        assertThat(body.path("size").asInt()).isEqualTo(7);
        assertThat(body.at("/query/bool/must/0/multi_match/query").asText()).isEqualTo("metadata search");
        assertThat(body.at("/query/bool/must/0/multi_match/analyzer").asText()).isEqualTo("ik_smart");
        assertThat(body.at("/query/bool/must/0/multi_match/minimum_should_match").asText()).isEqualTo("2<75%");
        assertThat(interceptor.request().url().encodedPath()).isEqualTo("/chunks/_search");
        assertThat(interceptor.body()).contains("\"terms\":{\"kb_id\":[\"kb-1\"]}");
        assertThat(interceptor.body()).contains("\"term\":{\"tenant_id\":\"tenant-1\"}");
        assertThat(interceptor.body()).contains("\"term\":{\"metadata.category.keyword\":\"policy\"}");
    }

    @Test
    void shouldUseMetadataObjectPathWhenSchemaUsesDefaultSearchField() {
        CapturingInterceptor interceptor = new CapturingInterceptor("{\"hits\":{\"hits\":[]}}");
        ElasticsearchKeywordSearchAdapter adapter = new ElasticsearchKeywordSearchAdapter(
                new OkHttpClient.Builder().addInterceptor(interceptor).build(),
                objectMapper,
                new ElasticsearchKeywordProperties("http://localhost:9200", "chunks",
                        List.of("content"), "", "", "", Duration.ofSeconds(3)));

        MetadataFieldDescriptor category = new MetadataFieldDescriptor(
                "category", "分类", MetadataValueType.STRING, Set.of(MetadataOperator.EQ),
                false, true, false, false, true, MetadataIndexPolicy.SEARCH_KEYWORD, 0.8D,
                Set.of(), Map.of(), BackendFieldMapping.defaults("category"));

        adapter.search(new KeywordSearchRequest("metadata search", 3, null, null,
                new CompiledMetadataFilter(RetrievalFilter.builder().build(),
                        new FieldEq(category, "policy"), List.of(), List.of())));

        assertThat(interceptor.body()).contains("\"term\":{\"metadata.category\":\"policy\"}");
    }

    private CompiledMetadataFilter compiledFilter() {
        MetadataFieldDescriptor category = new MetadataFieldDescriptor(
                "category", "分类", MetadataValueType.STRING, Set.of(MetadataOperator.EQ),
                false, true, false, false, true, MetadataIndexPolicy.SEARCH_KEYWORD, 0.8D,
                Set.of(), Map.of(), new BackendFieldMapping("category", "", "",
                "metadata.category.keyword", false, true, false, Map.of()));
        RetrievalFilter sourceFilter = RetrievalFilter.builder()
                .system(SystemRetrievalFilter.builder()
                        .tenantId("tenant-1")
                        .knowledgeBaseIds(List.of("kb-1"))
                        .documentIds(List.of("doc-1"))
                        .enabledOnly(true)
                        .build())
                .build();
        return new CompiledMetadataFilter(sourceFilter,
                new FilterAnd(List.of(new FieldEq(category, "policy"))), List.of(), List.of());
    }

    private static final class CapturingInterceptor implements Interceptor {

        private final String responseBody;
        private okhttp3.Request request;
        private String body;

        private CapturingInterceptor(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            request = chain.request();
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            body = buffer.readUtf8();
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(responseBody, ElasticsearchKeywordHttpClient.JSON))
                    .build();
        }

        okhttp3.Request request() {
            return request;
        }

        String body() {
            return body;
        }
    }
}
