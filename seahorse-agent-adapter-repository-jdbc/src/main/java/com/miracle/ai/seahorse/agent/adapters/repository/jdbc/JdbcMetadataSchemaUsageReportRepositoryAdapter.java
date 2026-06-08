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

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

public class JdbcMetadataSchemaUsageReportRepositoryAdapter implements MetadataSchemaUsageReportRepositoryPort {

    private static final String SCHEMA_USAGE_EVENT_COMPILED = "COMPILED";
    private static final String SCHEMA_USAGE_EVENT_REJECTED = "REJECTED";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataSchemaUsageSupport schemaUsageSupport;
    private final JdbcMetadataSchemaUsageReportSupport schemaUsageReportSupport;

    public JdbcMetadataSchemaUsageReportRepositoryAdapter(
            DataSource dataSource,
            MetadataSchemaManagementRepositoryPort schemaManagementRepositoryPort) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.schemaUsageSupport = new JdbcMetadataSchemaUsageSupport();
        this.schemaUsageReportSupport = new JdbcMetadataSchemaUsageReportSupport(
                jdbcTemplate, schemaManagementRepositoryPort::listSchemaFields);
    }

    @Override
    public void recordCompiled(String tenantId,
                               String knowledgeBaseId,
                               Integer schemaVersion,
                               List<String> fieldKeys,
                               List<String> guardOnlyFieldKeys) {
        recordSchemaUsage(tenantId, knowledgeBaseId, schemaVersion, fieldKeys, guardOnlyFieldKeys,
                SCHEMA_USAGE_EVENT_COMPILED, "");
    }

    @Override
    public void recordRejected(String tenantId,
                               String knowledgeBaseId,
                               Integer schemaVersion,
                               List<String> fieldKeys,
                               String rejectReason) {
        recordSchemaUsage(tenantId, knowledgeBaseId, schemaVersion, fieldKeys, List.of(),
                SCHEMA_USAGE_EVENT_REJECTED, rejectReason);
    }

    @Override
    public MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId, Integer schemaVersion) {
        return schemaUsageReportSupport.report(tenantId, knowledgeBaseId, schemaVersion);
    }

    private void recordSchemaUsage(String tenantId,
                                   String knowledgeBaseId,
                                   Integer schemaVersion,
                                   List<String> fieldKeys,
                                   List<String> guardOnlyFieldKeys,
                                   String eventType,
                                   String rejectReason) {
        JdbcMetadataSchemaUsageSupport.SchemaUsageBatch batch = schemaUsageSupport.buildBatch(
                tenantId, knowledgeBaseId, schemaVersion, fieldKeys, guardOnlyFieldKeys, eventType, rejectReason);
        if (batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO t_metadata_schema_usage_log(
                    id, request_id, tenant_id, kb_id, schema_version, field_key,
                    event_type, guard_only, reject_reason, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch.args());
    }
}
