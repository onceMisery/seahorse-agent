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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRetry;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;

public class JdbcMetadataQuarantineRepositoryAdapter implements MetadataQuarantinePort,
        MetadataQuarantineManagementRepositoryPort {

    private final JdbcMetadataQuarantineSupport quarantineSupport;

    public JdbcMetadataQuarantineRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        JdbcMetadataJsonSupport jsonSupport = new JdbcMetadataJsonSupport(objectMapper);
        this.quarantineSupport = new JdbcMetadataQuarantineSupport(jdbcTemplate, jsonSupport);
    }

    @Override
    public void quarantine(MetadataQuarantineItem item) {
        quarantineSupport.quarantine(item);
    }

    @Override
    public MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query) {
        return quarantineSupport.pageQuarantineItems(query);
    }

    @Override
    public Optional<MetadataQuarantineRecord> findQuarantineItem(String itemId) {
        return quarantineSupport.findQuarantineItem(itemId);
    }

    @Override
    public MetadataQuarantineRecord resolveQuarantineItem(MetadataQuarantineResolution resolution) {
        return quarantineSupport.resolveQuarantineItem(resolution);
    }

    @Override
    public MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry) {
        return quarantineSupport.scheduleQuarantineRetry(retry);
    }
}
