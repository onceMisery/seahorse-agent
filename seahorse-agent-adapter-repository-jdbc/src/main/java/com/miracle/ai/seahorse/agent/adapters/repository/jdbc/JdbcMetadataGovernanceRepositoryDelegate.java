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

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Shared JDBC metadata implementation source that is not exposed as a metadata port bean.
 */
public final class JdbcMetadataGovernanceRepositoryDelegate {

    private final JdbcMetadataGovernanceRepositoryAdapter adapter;

    public JdbcMetadataGovernanceRepositoryDelegate(DataSource dataSource, ObjectMapper objectMapper) {
        this.adapter = new JdbcMetadataGovernanceRepositoryAdapter(
                Objects.requireNonNull(dataSource, "dataSource must not be null"),
                Objects.requireNonNull(objectMapper, "objectMapper must not be null"));
    }

    public JdbcMetadataGovernanceRepositoryDelegate(JdbcMetadataGovernanceRepositoryAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
    }

    public JdbcMetadataGovernanceRepositoryAdapter adapter() {
        return adapter;
    }
}
