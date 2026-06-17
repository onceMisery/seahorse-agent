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

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusSkillVectorIndexAdapterTests {

    @Test
    void shouldKeepExistingCollectionWhenDimensionMatches() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any(HasCollectionReq.class))).thenReturn(true);
        when(client.describeCollection(any(DescribeCollectionReq.class))).thenReturn(describeCollection(768));

        MilvusSkillVectorIndexAdapter adapter = new MilvusSkillVectorIndexAdapter(client);

        boolean changed = adapter.ensureCollection(768);

        assertThat(changed).isFalse();
        verify(client, never()).dropCollection(any(DropCollectionReq.class));
        verify(client, never()).createCollection(any(CreateCollectionReq.class));
    }

    @Test
    void shouldRecreateExistingCollectionWhenDimensionDiffers() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any(HasCollectionReq.class))).thenReturn(true, false);
        when(client.describeCollection(any(DescribeCollectionReq.class))).thenReturn(describeCollection(1536));

        MilvusSkillVectorIndexAdapter adapter = new MilvusSkillVectorIndexAdapter(client);

        boolean changed = adapter.ensureCollection(768);

        assertThat(changed).isTrue();
        verify(client).dropCollection(any(DropCollectionReq.class));

        ArgumentCaptor<CreateCollectionReq> requestCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(requestCaptor.capture());
        CreateCollectionReq.FieldSchema embeddingField = requestCaptor.getValue()
                .getCollectionSchema()
                .getField("embedding");
        assertThat(embeddingField.getDimension()).isEqualTo(768);
    }

    @Test
    void shouldEscapeStringLiteralsInMilvusFilters() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        MilvusSkillVectorIndexAdapter adapter = new MilvusSkillVectorIndexAdapter(client);

        adapter.searchSimilar("tenant\"\\x", new float[] {1.0f, 0.0f}, 3);
        adapter.delete("tenant\"\\x", "skill\"\\name");

        ArgumentCaptor<SearchReq> searchCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(searchCaptor.capture());
        assertThat(searchCaptor.getValue().getFilter())
                .isEqualTo("tenant_id == \"tenant\\\"\\\\x\"");

        ArgumentCaptor<DeleteReq> deleteCaptor = ArgumentCaptor.forClass(DeleteReq.class);
        verify(client).delete(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().getFilter())
                .isEqualTo("id == \"tenant\\\"\\\\x:skill\\\"\\\\name\"");
    }

    private DescribeCollectionResp describeCollection(int dimension) {
        CreateCollectionReq.FieldSchema embeddingField = CreateCollectionReq.FieldSchema.builder()
                .name("embedding")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build();
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(List.of(embeddingField))
                .build();
        return DescribeCollectionResp.builder()
                .collectionName("seahorse_skill_vectors")
                .collectionSchema(schema)
                .build();
    }
}
