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

package com.miracle.ai.seahorse.agent.kernel.support;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdsTests {

    @Test
    void shouldGenerateMonotonicStringIds() {
        String first = SnowflakeIds.nextIdString();
        String second = SnowflakeIds.nextIdString();

        assertThat(first).containsOnlyDigits();
        assertThat(second).containsOnlyDigits();
        assertThat(first.length()).isLessThanOrEqualTo(19);
        assertThat(second.length()).isLessThanOrEqualTo(19);
        assertThat(Long.parseLong(second)).isGreaterThan(Long.parseLong(first));
    }

    @Test
    void shouldGenerateUniqueIdsForBatch() {
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(SnowflakeIds.nextIdString());
        }
        assertThat(ids).hasSize(1000);
    }
}
