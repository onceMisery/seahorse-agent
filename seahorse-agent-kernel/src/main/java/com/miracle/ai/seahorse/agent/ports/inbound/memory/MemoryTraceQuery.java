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

package com.miracle.ai.seahorse.agent.ports.inbound.memory;

import java.util.Objects;

public record MemoryTraceQuery(
        int limit,
        String traceId,
        String tenantId,
        String userId,
        String conversationId,
        String sessionId,
        String component,
        String status) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 500;

    public MemoryTraceQuery {
        limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        traceId = normalize(traceId);
        tenantId = normalize(tenantId);
        userId = normalize(userId);
        conversationId = normalize(conversationId);
        sessionId = normalize(sessionId);
        component = normalize(component);
        status = normalize(status);
    }

    public static MemoryTraceQuery recent(int limit) {
        return new MemoryTraceQuery(limit, "", "", "", "", "", "", "");
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
