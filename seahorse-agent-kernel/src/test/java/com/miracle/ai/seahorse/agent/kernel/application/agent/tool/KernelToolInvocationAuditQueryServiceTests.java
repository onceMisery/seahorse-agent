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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolInvocationAuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelToolInvocationAuditQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-23T00:00:00Z");

    @Test
    void shouldPageAuditEntriesBehindAdminBoundary() {
        MemoryToolInvocationAuditQueryPort queryPort = new MemoryToolInvocationAuditQueryPort();
        ToolInvocationAuditQueryInboundPort service =
                new KernelToolInvocationAuditQueryService(queryPort, adminUser());

        ToolInvocationAuditPage page = service.page(
                "tenant-1",
                "agent-1",
                "agent-1-v1",
                "run-1",
                "weather_query",
                ToolInvocationStatus.SUCCEEDED,
                2L,
                20L);

        assertEquals("tenant-1", queryPort.lastQuery.tenantId());
        assertEquals("agent-1", queryPort.lastQuery.agentId());
        assertEquals("agent-1-v1", queryPort.lastQuery.versionId());
        assertEquals("run-1", queryPort.lastQuery.runId());
        assertEquals("weather_query", queryPort.lastQuery.toolId());
        assertEquals(ToolInvocationStatus.SUCCEEDED, queryPort.lastQuery.status());
        assertEquals(2L, queryPort.lastQuery.current());
        assertEquals(20L, queryPort.lastQuery.size());
        assertEquals(1L, page.total());
    }

    @Test
    void shouldRejectNonAdminAccess() {
        ToolInvocationAuditQueryInboundPort service =
                new KernelToolInvocationAuditQueryService(new MemoryToolInvocationAuditQueryPort(), user());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.page(null, null, null, null, null, null, 1L, 10L));

        assertEquals("权限不足", error.getMessage());
    }

    private static ToolInvocationAuditEntry entry() {
        return new ToolInvocationAuditEntry(
                "invocation-1",
                "run-1",
                "step-1",
                "agent-1",
                "agent-1-v1",
                "tenant-1",
                "user-1",
                "weather_query",
                "run-1:call-1",
                ToolInvocationStatus.SUCCEEDED,
                "decision-1",
                "keys=[query], size=1",
                "length=16",
                null,
                NOW,
                NOW.plusSeconds(1));
    }

    private static CurrentUserPort adminUser() {
        return () -> Optional.of(new CurrentUser(1L, "root", "admin", null));
    }

    private static CurrentUserPort user() {
        return () -> Optional.of(new CurrentUser(2L, "alice", "user", null));
    }

    private static final class MemoryToolInvocationAuditQueryPort implements ToolInvocationAuditQueryPort {
        private ToolInvocationAuditQuery lastQuery;

        @Override
        public ToolInvocationAuditPage page(ToolInvocationAuditQuery query) {
            lastQuery = query;
            return new ToolInvocationAuditPage(List.of(entry()), 1L, query.size(), query.current(), 1L);
        }
    }
}
