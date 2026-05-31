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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;

/**
 * 负责元数据 Schema Usage 的参数规整与批量写入参数组装，
 * 让主适配器聚焦仓储接口而不是统计日志拼装细节。
 */
public final class JdbcMetadataSchemaUsageSupport {

    private static final String SCHEMA_USAGE_EVENT_COMPILED = "COMPILED";

    public SchemaUsageBatch buildBatch(String tenantId,
                                       String knowledgeBaseId,
                                       Integer schemaVersion,
                                       List<String> fieldKeys,
                                       List<String> guardOnlyFieldKeys,
                                       String eventType,
                                       String rejectReason) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        if (blank(safeTenantId) || blank(safeKbId)) {
            return SchemaUsageBatch.empty();
        }
        List<String> safeFieldKeys = normalizedFieldKeys(fieldKeys);
        if (safeFieldKeys.isEmpty()) {
            return SchemaUsageBatch.empty();
        }
        Set<String> safeGuardOnlyFieldKeys = Set.copyOf(normalizedFieldKeys(guardOnlyFieldKeys));
        int safeSchemaVersion = schemaVersion == null || schemaVersion <= 0 ? 1 : schemaVersion;
        String requestId = SnowflakeIds.nextIdString();
        Instant now = Instant.now();
        List<Object[]> batchArgs = new ArrayList<>(safeFieldKeys.size());
        for (String fieldKey : safeFieldKeys) {
            batchArgs.add(new Object[]{
                    SnowflakeIds.nextIdString(),
                    requestId,
                    safeTenantId,
                    safeKbId,
                    safeSchemaVersion,
                    fieldKey,
                    Objects.requireNonNullElse(eventType, SCHEMA_USAGE_EVENT_COMPILED),
                    safeGuardOnlyFieldKeys.contains(fieldKey) ? 1 : 0,
                    Objects.requireNonNullElse(rejectReason, ""),
                    Timestamp.from(now)
            });
        }
        return new SchemaUsageBatch(batchArgs);
    }

    public List<String> normalizedFieldKeys(List<String> fieldKeys) {
        if (fieldKeys == null || fieldKeys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : fieldKeys) {
            String candidate = Objects.requireNonNullElse(value, "").trim();
            if (!candidate.isBlank()) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record SchemaUsageBatch(List<Object[]> args) {

        public SchemaUsageBatch {
            args = List.copyOf(Objects.requireNonNullElse(args, List.of()));
        }

        public static SchemaUsageBatch empty() {
            return new SchemaUsageBatch(List.of());
        }

        public boolean isEmpty() {
            return args.isEmpty();
        }
    }
}
