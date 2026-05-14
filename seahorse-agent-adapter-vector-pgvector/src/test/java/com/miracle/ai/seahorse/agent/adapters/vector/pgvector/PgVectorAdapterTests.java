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

package com.miracle.ai.seahorse.agent.adapters.vector.pgvector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorAdapterTests {

    @Test
    void shouldPushDownAclSubjectsIntoSqlFilter() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement hnswStatement = mock(Statement.class);
        PreparedStatement searchStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(hnswStatement);
        when(connection.prepareStatement(anyString())).thenReturn(searchStatement);
        when(searchStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        PgVectorAdapter adapter = new PgVectorAdapter(dataSource, new ObjectMapper(),
                new PgVectorProperties("t_vector_chunk", 2));

        adapter.search(searchRequest());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("metadata->>'tenant_id' = ?")
                .contains("jsonb_exists_any(metadata->'acl_subjects', ARRAY[?::text, ?::text])")
                .contains("metadata->>'acl_subjects' IN (?, ?)")
                .contains("(metadata->>'enabled' IS NULL OR metadata->>'enabled' = 'true')");
        // ACL 数组交集和兼容性标量判断都使用参数绑定，避免权限主体直接拼接进 SQL。
        verify(searchStatement).setObject(3, "tenant-1");
        verify(searchStatement).setObject(4, "dept-a");
        verify(searchStatement).setObject(5, "user-1");
        verify(searchStatement).setObject(6, "dept-a");
        verify(searchStatement).setObject(7, "user-1");
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
