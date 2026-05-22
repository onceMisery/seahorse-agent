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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class KernelMemoryTraceQueryService implements MemoryTraceInboundPort {

    private final MemoryTraceRecorder traceRecorder;

    public KernelMemoryTraceQueryService(MemoryTraceRecorder traceRecorder) {
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
    }

    @Override
    public List<MemoryTraceEvent> listRecent(MemoryTraceQuery query) {
        MemoryTraceQuery safeQuery = query == null ? MemoryTraceQuery.recent(MemoryTraceQuery.DEFAULT_LIMIT) : query;
        return traceRecorder.listRecent(MemoryTraceQuery.MAX_LIMIT).stream()
                .filter(event -> matches(safeQuery.traceId(), event.traceId(), false))
                .filter(event -> matches(safeQuery.tenantId(), event.tenantId(), false))
                .filter(event -> matches(safeQuery.userId(), event.userId(), false))
                .filter(event -> matches(safeQuery.conversationId(), event.conversationId(), false))
                .filter(event -> matches(safeQuery.sessionId(), event.sessionId(), false))
                .filter(event -> matches(safeQuery.component(), event.component(), true))
                .filter(event -> matches(safeQuery.status(), event.status(), true))
                .limit(safeQuery.limit())
                .toList();
    }

    private boolean matches(String expected, String actual, boolean ignoreCase) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String safeActual = Objects.requireNonNullElse(actual, "");
        if (!ignoreCase) {
            return expected.equals(safeActual);
        }
        return expected.toUpperCase(Locale.ROOT).equals(safeActual.toUpperCase(Locale.ROOT));
    }
}
