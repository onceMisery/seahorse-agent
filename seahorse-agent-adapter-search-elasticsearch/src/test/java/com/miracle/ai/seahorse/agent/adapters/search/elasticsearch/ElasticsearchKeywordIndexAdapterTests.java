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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchKeywordIndexAdapterTests {

    @Test
    void shouldWriteBulkIndexAndDeleteByQueryRequests() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        ElasticsearchKeywordIndexAdapter adapter = new ElasticsearchKeywordIndexAdapter(
                new OkHttpClient.Builder().addInterceptor(interceptor).build(),
                new ObjectMapper(),
                new ElasticsearchKeywordProperties("http://localhost:9200", "chunks",
                        List.of("content"), "", "", "", Duration.ofSeconds(3)));

        adapter.indexDocumentChunks("kb-1", "doc-1", List.of(VectorChunk.builder()
                .chunkId("chunk-1")
                .index(1)
                .content("企业级元数据治理")
                .metadata(Map.of(
                        "tenant_id", "tenant-1",
                        "collection_name", "default",
                        "acl_subjects", List.of("dept-a"),
                        "file_type", "pdf",
                        "source_type", "upload",
                        "enabled", false))
                .build()));
        adapter.deleteDocumentChunks("kb-1", "doc-1");

        assertThat(interceptor.requests()).hasSize(2);
        assertThat(interceptor.requests().get(0).url().encodedPath()).isEqualTo("/chunks/_bulk");
        assertThat(interceptor.bodies().get(0))
                .contains("\"_id\":\"chunk-1\"")
                .contains("\"content\":\"企业级元数据治理\"")
                .contains("\"tenant_id\":\"tenant-1\"")
                .contains("\"acl_subject_ids\":[\"dept-a\"]")
                .contains("\"file_type\":\"pdf\"")
                .contains("\"source_type\":\"upload\"")
                .contains("\"enabled\":false")
                .endsWith("\n");
        assertThat(interceptor.requests().get(1).url().encodedPath()).isEqualTo("/chunks/_delete_by_query");
        assertThat(interceptor.bodies().get(1))
                .contains("\"term\":{\"kb_id\":\"kb-1\"}")
                .contains("\"term\":{\"doc_id\":\"doc-1\"}");
    }

    private static final class CapturingInterceptor implements Interceptor {

        private final List<okhttp3.Request> requests = new ArrayList<>();
        private final List<String> bodies = new ArrayList<>();

        @Override
        public Response intercept(Chain chain) throws IOException {
            okhttp3.Request request = chain.request();
            requests.add(request);
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            bodies.add(buffer.readUtf8());
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create("{\"acknowledged\":true}", ElasticsearchKeywordHttpClient.JSON))
                    .build();
        }

        List<okhttp3.Request> requests() {
            return requests;
        }

        List<String> bodies() {
            return bodies;
        }
    }
}
