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

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataSchemaUsageSupportTests {

    private final JdbcMetadataSchemaUsageSupport support = new JdbcMetadataSchemaUsageSupport();

    @Test
    void shouldNormalizeFieldKeysAndBuildBatchArgs() {
        JdbcMetadataSchemaUsageSupport.SchemaUsageBatch batch = support.buildBatch(
                "tenant-1",
                "kb-1",
                null,
                List.of(" title ", "author", "title", ""),
                List.of("author"),
                null,
                null);

        assertThat(batch.isEmpty()).isFalse();
        assertThat(batch.args()).hasSize(2);
        assertThat(batch.args().get(0)[2]).isEqualTo("tenant-1");
        assertThat(batch.args().get(0)[3]).isEqualTo("kb-1");
        assertThat(batch.args().get(0)[4]).isEqualTo(1);
        assertThat(batch.args().get(0)[5]).isEqualTo("title");
        assertThat(batch.args().get(0)[6]).isEqualTo("COMPILED");
        assertThat(batch.args().get(1)[7]).isEqualTo(1);
        assertThat(batch.args().get(0)[9]).isInstanceOf(Timestamp.class);
    }

    @Test
    void shouldReturnEmptyBatchWhenScopeInvalid() {
        assertThat(support.buildBatch("", "kb-1", 1, List.of("title"), List.of(), "REJECTED", "bad").isEmpty())
                .isTrue();
        assertThat(support.buildBatch("tenant-1", "kb-1", 1, List.of(" ", ""), List.of(), "REJECTED", "bad")
                .isEmpty()).isTrue();
        assertThat(support.normalizedFieldKeys(Arrays.asList(" title ", "title", null, "author")))
                .containsExactly("title", "author");
    }
}
