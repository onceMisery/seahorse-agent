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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;

public class JdbcMetadataCanonicalWriteRepositoryAdapter implements MetadataCanonicalWritePort {

    private final JdbcMetadataCanonicalWriteSupport canonicalWriteSupport;

    public JdbcMetadataCanonicalWriteRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        JdbcMetadataJsonSupport jsonSupport = new JdbcMetadataJsonSupport(objectMapper);
        this.canonicalWriteSupport = new JdbcMetadataCanonicalWriteSupport(jdbcTemplate, jsonSupport);
    }

    @Override
    public void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata) {
        canonicalWriteSupport.writeDocumentMetadata(documentId, acceptedMetadata);
    }
}
