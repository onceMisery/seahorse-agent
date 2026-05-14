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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldEq;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcKeywordSearchAdapterTests {

    @Test
    void shouldBuildPostgresFtsQueryAndRankBySearchText() throws Exception {
        DriverManagerDataSource dataSource = dataSource("keyword-search");
        JdbcKeywordSearchAdapter adapter = new JdbcKeywordSearchAdapter(dataSource, new ObjectMapper());
        List<Object> args = new ArrayList<>();

        String sql = searchSql(adapter, request("入职 流程", 5), args);

        assertThat(sql).contains("websearch_to_tsquery('simple', ?)");
        assertThat(sql).contains("@@ keyword_query.q");
        assertThat(sql).contains("ts_rank_cd");
        assertThat(sql).contains("rank_score DESC");
        assertThat(sql).doesNotContain("content LIKE");
        assertThat(args).containsExactly("入职 流程", 1, 5);
    }

    @Test
    void shouldPushDownSystemFiltersWhenMetadataJsonExists() throws Exception {
        DriverManagerDataSource dataSource = dataSource("keyword-system-filter");
        createChunkTableWithMetadata(dataSource);
        JdbcKeywordSearchAdapter adapter = new JdbcKeywordSearchAdapter(dataSource, new ObjectMapper());
        SystemRetrievalFilter system = SystemRetrievalFilter.builder()
                .tenantId("tenant-1")
                .knowledgeBaseIds(List.of("kb-1"))
                .documentIds(List.of("doc-1"))
                .collectionNames(List.of("default"))
                .fileTypes(List.of("pdf"))
                .sourceTypes(List.of("upload"))
                .aclSubjectIds(List.of("u:1", "g:hr"))
                .enabledOnly(true)
                .build();
        List<Object> args = new ArrayList<>();

        String sql = searchSql(adapter, request("制度", 3,
                new CompiledMetadataFilter(RetrievalFilter.builder().system(system).build(),
                        new FilterAnd(List.of()), List.of(), List.of())), args);

        assertThat(sql).contains("metadata_json->>'tenant_id' = ?");
        assertThat(sql).contains("metadata_json->>'collection_name' IN (?)");
        assertThat(sql).contains("metadata_json->>'file_type' IN (?)");
        assertThat(sql).contains("metadata_json->>'source_type' IN (?)");
        assertThat(sql).contains("jsonb_array_elements_text");
        assertThat(args).containsExactly("制度", 1, "kb-1", "doc-1", "tenant-1",
                "default", "pdf", "upload", "u:1", "g:hr", 3);
    }

    @Test
    void shouldPushDownCompiledMetadataExpressionWhenMetadataJsonExists() throws Exception {
        DriverManagerDataSource dataSource = dataSource("keyword-metadata-filter");
        createChunkTableWithMetadata(dataSource);
        JdbcKeywordSearchAdapter adapter = new JdbcKeywordSearchAdapter(dataSource, new ObjectMapper());
        MetadataFieldDescriptor category = new MetadataFieldDescriptor(
                "category", "分类", MetadataValueType.STRING, Set.of(MetadataOperator.EQ),
                false, true, false, false, true, MetadataIndexPolicy.SEARCH_KEYWORD, 0.8D,
                Set.of(), Map.of(), new BackendFieldMapping("category", "", "",
                "category", true, true, false, Map.of()));
        List<Object> args = new ArrayList<>();

        String sql = searchSql(adapter, request("政策", 4,
                new CompiledMetadataFilter(RetrievalFilter.builder().build(),
                        new FilterAnd(List.of(new FieldEq(category, "policy"))), List.of(), List.of())), args);

        assertThat(sql).contains("metadata_json->>'category' = ?");
        assertThat(args).containsExactly("政策", 1, "policy", 4);
    }

    @SuppressWarnings("unchecked")
    private String searchSql(JdbcKeywordSearchAdapter adapter, KeywordSearchRequest request, List<Object> args)
            throws Exception {
        Method method = JdbcKeywordSearchAdapter.class
                .getDeclaredMethod("searchSql", KeywordSearchRequest.class, List.class);
        method.setAccessible(true);
        return (String) method.invoke(adapter, request, args);
    }

    private KeywordSearchRequest request(String query, int topK) {
        return new KeywordSearchRequest(query, topK, null, RetrievalOptions.defaults(topK),
                CompiledMetadataFilter.empty());
    }

    private KeywordSearchRequest request(String query, int topK, CompiledMetadataFilter compiledFilter) {
        return new KeywordSearchRequest(query, topK, null, RetrievalOptions.defaults(topK), compiledFilter);
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void createChunkTableWithMetadata(DriverManagerDataSource dataSource) {
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE t_knowledge_chunk (
                    id VARCHAR(64),
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64),
                    chunk_index INTEGER,
                    content VARCHAR(2048),
                    enabled INTEGER,
                    deleted INTEGER,
                    update_time TIMESTAMP,
                    metadata_json VARCHAR(4096)
                )
                """);
    }
}
