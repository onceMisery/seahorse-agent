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

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    private VectorSearchRequest searchRequest() {
        SystemRetrievalFilter system = SystemRetrievalFilter.builder()
                .tenantId("tenant-1")
                .aclSubjectIds(List.of("dept-a", "user-1"))
                .enabledOnly(true)
                .build();
        RetrievalFilter filter = RetrievalFilter.builder().system(system).build();
        CompiledMetadataFilter compiledFilter = new CompiledMetadataFilter(
                filter,
                new FilterAnd(List.of()),
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
}
