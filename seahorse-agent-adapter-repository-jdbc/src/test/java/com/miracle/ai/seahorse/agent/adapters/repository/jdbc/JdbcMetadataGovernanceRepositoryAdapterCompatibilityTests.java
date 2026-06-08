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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataGovernanceRepositoryAdapterCompatibilityTests {

    @Test
    void compatibilityFacadeShouldOnlyOwnGranularRepositoryAdapters() {
        Set<Class<?>> fieldTypes = Arrays.stream(JdbcMetadataGovernanceRepositoryAdapter.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getType)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(fieldTypes).containsExactlyInAnyOrder(
                JdbcMetadataSchemaRepositoryAdapter.class,
                JdbcMetadataDictionaryRepositoryAdapter.class,
                JdbcMetadataExtractionResultRepositoryAdapter.class,
                JdbcMetadataReviewRepositoryAdapter.class,
                JdbcMetadataQuarantineRepositoryAdapter.class,
                JdbcMetadataCanonicalWriteRepositoryAdapter.class,
                JdbcMetadataBackfillJobRepositoryAdapter.class,
                JdbcMetadataQualityReportRepositoryAdapter.class,
                JdbcMetadataSchemaUsageReportRepositoryAdapter.class);
    }
}
