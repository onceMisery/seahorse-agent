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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * 负责 schema usage report 的统计查询与结果组装，
 * 将主适配器中的报表读取职责收敛为独立协作者。
 */
final class JdbcMetadataSchemaUsageReportSupport {

    private final JdbcTemplate jdbcTemplate;
    private final BiFunction<String, String, List<MetadataSchemaFieldRecord>> schemaFieldLoader;

    JdbcMetadataSchemaUsageReportSupport(
            JdbcTemplate jdbcTemplate,
            BiFunction<String, String, List<MetadataSchemaFieldRecord>> schemaFieldLoader) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.schemaFieldLoader = Objects.requireNonNull(schemaFieldLoader, "schemaFieldLoader must not be null");
    }

    MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId, Integer schemaVersion) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        Integer safeSchemaVersion = schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
        if (blank(safeTenantId) || blank(safeKbId)) {
            return MetadataSchemaUsageReport.empty(safeTenantId, safeKbId, safeSchemaVersion);
        }
        try {
            SqlWhere where = schemaUsageWhere(safeTenantId, safeKbId, safeSchemaVersion);
            long totalCompiledRequests = countLong("""
                    SELECT COUNT(DISTINCT request_id)
                    FROM t_metadata_schema_usage_log
                    """ + where.sql() + """
                    AND event_type = 'COMPILED'
                    """, where.args());
            long totalRejectedRequests = countLong("""
                    SELECT COUNT(DISTINCT request_id)
                    FROM t_metadata_schema_usage_log
                    """ + where.sql() + """
                    AND event_type = 'REJECTED'
                    """, where.args());
            long guardOnlyRequestCount = countLong("""
                    SELECT COUNT(DISTINCT request_id)
                    FROM t_metadata_schema_usage_log
                    """ + where.sql() + """
                    AND event_type = 'COMPILED'
                    AND guard_only = 1
                    """, where.args());

            List<MetadataSchemaUsageAggregate> aggregates = jdbcTemplate.query("""
                    SELECT field_key,
                           SUM(CASE WHEN event_type = 'COMPILED' THEN 1 ELSE 0 END) AS usage_count,
                           SUM(CASE WHEN event_type = 'COMPILED' AND guard_only = 1 THEN 1 ELSE 0 END)
                               AS guard_only_count,
                           SUM(CASE WHEN event_type = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count
                    FROM t_metadata_schema_usage_log
                    """ + where.sql() + """
                    GROUP BY field_key
                    """, (rs, rowNum) -> new MetadataSchemaUsageAggregate(
                            rs.getString("field_key"),
                            rs.getLong("usage_count"),
                            rs.getLong("guard_only_count"),
                            rs.getLong("rejected_count")),
                    where.args().toArray());
            Map<String, MetadataSchemaUsageAggregate> aggregateByField = new LinkedHashMap<>();
            for (MetadataSchemaUsageAggregate aggregate : aggregates) {
                aggregateByField.put(aggregate.fieldKey(), aggregate);
            }

            LinkedHashMap<String, String> displayNames = new LinkedHashMap<>();
            for (MetadataSchemaFieldRecord field : schemaFieldLoader.apply(safeTenantId, safeKbId)) {
                displayNames.putIfAbsent(field.fieldKey(), field.displayName());
            }
            for (String fieldKey : aggregateByField.keySet()) {
                displayNames.putIfAbsent(fieldKey, fieldKey);
            }

            List<MetadataSchemaUsageFieldRecord> fields = displayNames.entrySet().stream()
                    .map(entry -> toSchemaUsageFieldRecord(entry.getKey(), entry.getValue(),
                            aggregateByField.get(entry.getKey())))
                    .sorted(java.util.Comparator
                            .comparingLong(MetadataSchemaUsageFieldRecord::usageCount).reversed()
                            .thenComparingLong(MetadataSchemaUsageFieldRecord::rejectedCount).reversed()
                            .thenComparingLong(MetadataSchemaUsageFieldRecord::guardOnlyCount).reversed()
                            .thenComparing(MetadataSchemaUsageFieldRecord::fieldKey))
                    .toList();

            return new MetadataSchemaUsageReport(
                    safeTenantId,
                    safeKbId,
                    safeSchemaVersion,
                    totalCompiledRequests,
                    totalRejectedRequests,
                    guardOnlyRequestCount,
                    ratio(guardOnlyRequestCount, totalCompiledRequests),
                    ratio(totalRejectedRequests, totalCompiledRequests + totalRejectedRequests),
                    fields,
                    Instant.now());
        } catch (DataAccessException ex) {
            return MetadataSchemaUsageReport.empty(safeTenantId, safeKbId, safeSchemaVersion);
        }
    }

    private SqlWhere schemaUsageWhere(String tenantId, String knowledgeBaseId, Integer schemaVersion) {
        StringBuilder sql = new StringBuilder("""
                WHERE tenant_id = ?
                  AND kb_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(knowledgeBaseId);
        if (schemaVersion != null && schemaVersion > 0) {
            sql.append(" AND schema_version = ?");
            args.add(schemaVersion);
        }
        return new SqlWhere(sql.toString(), args);
    }

    private MetadataSchemaUsageFieldRecord toSchemaUsageFieldRecord(String fieldKey,
                                                                    String displayName,
                                                                    MetadataSchemaUsageAggregate aggregate) {
        MetadataSchemaUsageAggregate safeAggregate = aggregate == null
                ? new MetadataSchemaUsageAggregate(fieldKey, 0L, 0L, 0L)
                : aggregate;
        long usageCount = safeAggregate.usageCount();
        long rejectedCount = safeAggregate.rejectedCount();
        return new MetadataSchemaUsageFieldRecord(
                fieldKey,
                displayName,
                usageCount,
                safeAggregate.guardOnlyCount(),
                rejectedCount,
                ratio(safeAggregate.guardOnlyCount(), usageCount),
                ratio(rejectedCount, usageCount + rejectedCount));
    }

    private long countLong(String sql, List<Object> args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0L ? 0D : (double) numerator / (double) denominator;
    }

    private record SqlWhere(String sql, List<Object> args) {
    }

    private record MetadataSchemaUsageAggregate(
            String fieldKey,
            long usageCount,
            long guardOnlyCount,
            long rejectedCount
    ) {
    }
}
