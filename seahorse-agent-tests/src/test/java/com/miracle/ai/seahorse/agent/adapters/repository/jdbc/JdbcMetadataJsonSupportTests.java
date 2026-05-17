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
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataJsonSupportTests {

    private final JdbcMetadataJsonSupport support = new JdbcMetadataJsonSupport(new ObjectMapper());

    @Test
    void shouldReadBackendMappingAndCollections() {
        Map<String, Object> values = support.readMap("""
                {"canonicalName":"title","milvusPath":"metadata.title","guardOnly":true}
                """);

        assertThat(values).containsEntry("canonicalName", "title");
        assertThat(support.readList("[\"kb\", \"doc\"]")).containsExactly("kb", "doc");
        assertThat(support.readMapList("[{\"field\":\"title\"}]"))
                .singleElement()
                .satisfies(item -> assertThat(item).containsEntry("field", "title"));
        assertThat(support.backendMapping("{\"searchFieldName\":\"title.keyword\",\"guardOnly\":true}", "title")
                .searchFieldName()).isEqualTo("title.keyword");
    }

    @Test
    void shouldFallbackWhenJsonInvalidOrMissing() {
        assertThat(support.readMap("not-json").entrySet()).isEmpty();
        assertThat(support.readList("not-json")).isEmpty();
        assertThat(support.operators(null)).containsExactlyInAnyOrder(MetadataOperator.EQ, MetadataOperator.IN);
        assertThat(support.backendMapping(null, "title").canonicalName()).isEqualTo("title");
        assertThat(support.json(null)).isEqualTo("{}");
    }
}
