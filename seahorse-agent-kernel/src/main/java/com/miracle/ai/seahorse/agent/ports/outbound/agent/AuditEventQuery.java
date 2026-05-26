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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;

import java.time.Instant;

public record AuditEventQuery(String tenantId,
                              String runId,
                              String agentId,
                              String resourceType,
                              String resourceId,
                              AuditEventType eventType,
                              Instant occurredFrom,
                              Instant occurredTo,
                              long current,
                              long size) {

    public static final long DEFAULT_CURRENT = 1L;
    public static final long DEFAULT_PAGE_SIZE = 10L;

    public AuditEventQuery {
        tenantId = trimToNull(tenantId);
        runId = trimToNull(runId);
        agentId = trimToNull(agentId);
        resourceType = trimToNull(resourceType);
        resourceId = trimToNull(resourceId);
        current = current <= 0 ? DEFAULT_CURRENT : current;
        size = size <= 0 ? DEFAULT_PAGE_SIZE : size;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
