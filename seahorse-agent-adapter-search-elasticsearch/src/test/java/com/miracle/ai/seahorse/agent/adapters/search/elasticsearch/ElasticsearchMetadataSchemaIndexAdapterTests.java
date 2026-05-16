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
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncStatusRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ElasticsearchMetadataSchemaIndexAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPutStrictMappingForIndexedSchemaField() throws Exception {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        ElasticsearchMetadataSchemaIndexAdapter adapter = new ElasticsearchMetadataSchemaIndexAdapter(
                new OkHttpClient.Builder().addInterceptor(interceptor).build(),
                objectMapper,
                new ElasticsearchKeywordProperties("http://localhost:9200", "chunks",
                        List.of("content"), "", "", "", Duration.ofSeconds(3)));

        adapter.syncField(field("department", MetadataValueType.STRING, MetadataIndexPolicy.SEARCH_KEYWORD,
                "metadata.department.keyword"));

        assertThat(interceptor.requests()).hasSize(1);
        assertThat(interceptor.requests().get(0).method()).isEqualTo("PUT");
        assertThat(interceptor.requests().get(0).url().encodedPath()).isEqualTo("/chunks/_mapping");

        JsonNode body = objectMapper.readTree(interceptor.bodies().get(0));
        assertThat(body.path("dynamic").asText()).isEqualTo("strict");
        assertThat(body.at("/properties/metadata/dynamic").asText()).isEqualTo("strict");
        assertThat(body.at("/properties/metadata/properties/department/type").asText()).isEqualTo("text");
        assertThat(body.at("/properties/metadata/properties/department/fields/keyword/type").asText())
                .isEqualTo("keyword");
    }

    @Test
    void shouldSkipFieldWithoutSearchIndexPolicy() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        ElasticsearchMetadataSchemaIndexAdapter adapter = new ElasticsearchMetadataSchemaIndexAdapter(
                new OkHttpClient.Builder().addInterceptor(interceptor).build(),
                objectMapper,
                new ElasticsearchKeywordProperties("http://localhost:9200", "chunks",
                        List.of("content"), "", "", "", Duration.ofSeconds(3)));

        adapter.syncField(field("internal_note", MetadataValueType.STRING, MetadataIndexPolicy.NONE,
                "metadata.internal_note"));

        assertThat(interceptor.requests()).isEmpty();
    }

    @Test
    void shouldSkipFieldChangeWhenFieldBecomesGuardOnly() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        ElasticsearchMetadataSchemaIndexAdapter adapter = new ElasticsearchMetadataSchemaIndexAdapter(
                new OkHttpClient.Builder().addInterceptor(interceptor).build(),
                objectMapper,
                new ElasticsearchKeywordProperties("http://localhost:9200", "chunks",
                        List.of("content"), "", "", "", Duration.ofSeconds(3)));

        adapter.syncFieldChange(
                field("department", MetadataValueType.STRING, MetadataIndexPolicy.SEARCH_KEYWORD,
                        "metadata.department.keyword", true, true, false),
                field("department", MetadataValueType.STRING, MetadataIndexPolicy.SEARCH_KEYWORD,
                        "metadata.department.keyword", true, true, true));

        assertThat(interceptor.requests()).isEmpty();
    }

    @Test
    void shouldRecordFailedObservationWhenMappingSyncFails() {
        FailingInterceptor interceptor = new FailingInterceptor();
        RecordingObservationPort observationPort = new RecordingObservationPort();
        RecordingSchemaIndexStatusPort statusPort = new RecordingSchemaIndexStatusPort();
        ElasticsearchMetadataSchemaIndexAdapter adapter = new ElasticsearchMetadataSchemaIndexAdapter(
                new OkHttpClient.Builder().addInterceptor(interceptor).build(),
                objectMapper,
                new ElasticsearchKeywordProperties("http://localhost:9200", "chunks",
                        List.of("content"), "", "", "", Duration.ofSeconds(3)),
                observationPort,
                statusPort);

        assertThatCode(() -> adapter.syncField(field("department", MetadataValueType.STRING,
                MetadataIndexPolicy.SEARCH_KEYWORD, "metadata.department.keyword")))
                .doesNotThrowAnyException();

        assertThat(observationPort.events()).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo("metadata.schema.index.sync.failed");
            assertThat(event.attributes()).containsEntry("backend", "elasticsearch");
            assertThat(event.attributes()).containsEntry("action", "CREATE");
            assertThat(event.attributes()).containsEntry("valueType", "STRING");
            assertThat(event.attributes()).containsEntry("errorType", "IllegalStateException");
            assertThat(event.attributes()).doesNotContainKeys("fieldKey", "errorMessage");
        });
        assertThat(statusPort.statuses()).singleElement().satisfies(status -> {
            assertThat(status.backend()).isEqualTo("elasticsearch");
            assertThat(status.action()).isEqualTo("CREATE");
            assertThat(status.outcome()).isEqualTo("FAILED");
            assertThat(status.fieldKey()).isEqualTo("department");
            assertThat(status.errorMessage()).contains("status=500");
        });
    }

    private MetadataSchemaFieldRecord field(String key,
                                            MetadataValueType valueType,
                                            MetadataIndexPolicy policy,
                                            String searchFieldName) {
        return field(key, valueType, policy, searchFieldName, true, true, false);
    }

    private MetadataSchemaFieldRecord field(String key,
                                            MetadataValueType valueType,
                                            MetadataIndexPolicy policy,
                                            String searchFieldName,
                                            boolean indexed,
                                            boolean pushdownToKeyword,
                                            boolean guardOnly) {
        Instant now = Instant.parse("2026-05-13T00:00:00Z");
        return new MetadataSchemaFieldRecord("field-1", "tenant-1", "kb-1", key, key, valueType,
                Set.of(MetadataOperator.EQ), false, true, false, false,
                indexed && !MetadataIndexPolicy.NONE.equals(policy), policy, 0.8D, Set.of(), Map.of(),
                new BackendFieldMapping(key, "", "", searchFieldName, false, pushdownToKeyword, guardOnly, Map.of()),
                2, now, now);
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

    private static final class FailingInterceptor implements Interceptor {

        @Override
        public Response intercept(Chain chain) {
            okhttp3.Request request = chain.request();
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("ERR")
                    .body(ResponseBody.create("{\"error\":\"failed\"}", ElasticsearchKeywordHttpClient.JSON))
                    .build();
        }
    }

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new RecordingObservationScope(events);
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }

        List<ObservationEvent> events() {
            return events;
        }
    }

    private record RecordingObservationScope(List<ObservationEvent> events) implements ObservationScope {

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingSchemaIndexStatusPort implements MetadataSchemaIndexStatusPort {

        private final List<MetadataSchemaIndexSyncStatusRecord> statuses = new ArrayList<>();

        @Override
        public void recordSyncResult(MetadataSchemaIndexSyncStatusRecord status) {
            statuses.add(status);
        }

        List<MetadataSchemaIndexSyncStatusRecord> statuses() {
            return statuses;
        }
    }
}
