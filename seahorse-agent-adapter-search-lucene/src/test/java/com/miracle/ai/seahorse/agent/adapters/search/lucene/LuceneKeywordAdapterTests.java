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

package com.miracle.ai.seahorse.agent.adapters.search.lucene;

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
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneKeywordAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void shouldIndexSearchWithCompiledFilterAndDeleteDocument() throws Exception {
        LuceneKeywordProperties properties = new LuceneKeywordProperties(tempDir, List.of("content^2"));
        try (LuceneKeywordIndexAdapter indexAdapter = new LuceneKeywordIndexAdapter(objectMapper, properties);
             LuceneKeywordSearchAdapter searchAdapter = new LuceneKeywordSearchAdapter(objectMapper, properties)) {
            indexAdapter.indexDocumentChunks("kb-1", "doc-1", List.of(VectorChunk.builder()
                    .chunkId("chunk-1")
                    .index(1)
                    .content("metadata search 企业级元数据治理")
                    .metadata(Map.of(
                            "tenant_id", "tenant-1",
                            "collection_name", "default",
                            "acl_subjects", List.of("dept-a"),
                            "category", "policy",
                            "file_type", "pdf",
                            "enabled", true))
                    .build()));

            var results = searchAdapter.search(new KeywordSearchRequest("metadata search", 5, null, null,
                    compiledFilter("policy")));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo("chunk-1");
            assertThat(results.get(0).getKbId()).isEqualTo("kb-1");
            assertThat(results.get(0).getDocId()).isEqualTo("doc-1");
            assertThat(results.get(0).getTenantId()).isEqualTo("tenant-1");
            assertThat(results.get(0).getMetadata())
                    .containsEntry("category", "policy")
                    .containsEntry("file_type", "pdf")
                    .containsEntry("enabled", true);

            indexAdapter.deleteDocumentChunks("kb-1", "doc-1");

            assertThat(searchAdapter.search(new KeywordSearchRequest("metadata search", 5, null, null,
                    compiledFilter("policy")))).isEmpty();
        }
    }

    @Test
    void shouldReturnEmptyWhenIndexDoesNotExist() throws Exception {
        LuceneKeywordProperties properties = new LuceneKeywordProperties(tempDir, List.of("content"));
        try (LuceneKeywordSearchAdapter searchAdapter = new LuceneKeywordSearchAdapter(objectMapper, properties)) {
            assertThat(searchAdapter.search(new KeywordSearchRequest("metadata search", 5, null, null,
                    CompiledMetadataFilter.empty()))).isEmpty();
        }
    }

    private CompiledMetadataFilter compiledFilter(String categoryValue) {
        MetadataFieldDescriptor category = new MetadataFieldDescriptor(
                "category", "分类", MetadataValueType.STRING, Set.of(MetadataOperator.EQ),
                false, true, false, false, true, MetadataIndexPolicy.SEARCH_KEYWORD, 0.8D,
                Set.of(), Map.of(), new BackendFieldMapping("category", "", "",
                "metadata.category.keyword", false, true, false, Map.of()));
        RetrievalFilter sourceFilter = RetrievalFilter.builder()
                .system(SystemRetrievalFilter.builder()
                        .tenantId("tenant-1")
                        .knowledgeBaseIds(List.of("kb-1"))
                        .aclSubjectIds(List.of("dept-a"))
                        .enabledOnly(true)
                        .build())
                .build();
        return new CompiledMetadataFilter(sourceFilter, new FieldEq(category, categoryValue), List.of(), List.of());
    }
}
