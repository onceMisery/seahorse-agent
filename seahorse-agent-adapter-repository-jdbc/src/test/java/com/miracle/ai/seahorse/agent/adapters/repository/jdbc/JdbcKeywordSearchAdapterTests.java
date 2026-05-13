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
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcKeywordSearchAdapterTests {

    @Test
    void shouldBuildPostgresFtsQueryAndRankBySearchText() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:keyword-search;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
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
}
