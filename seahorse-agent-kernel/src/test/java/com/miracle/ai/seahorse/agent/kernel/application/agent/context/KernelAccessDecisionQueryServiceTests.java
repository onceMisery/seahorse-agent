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

package com.miracle.ai.seahorse.agent.kernel.application.agent.context;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AccessDecisionQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelAccessDecisionQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void shouldPageAccessDecisionEntriesBehindAdminBoundary() {
        MemoryAccessDecisionQueryPort queryPort = new MemoryAccessDecisionQueryPort();
        AccessDecisionQueryInboundPort service = new KernelAccessDecisionQueryService(queryPort, adminUser());

        AccessDecisionPage page = service.page(
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                ContextResourceType.MEMORY.value(),
                "memory-1",
                AccessDecisionEffect.ALLOW,
                ResourceAccessReasonCodes.OWNER_MATCH,
                2L,
                20L);

        assertEquals("tenant-1", queryPort.lastQuery.tenantId());
        assertEquals(AccessSubjectType.USER_DELEGATED_AGENT, queryPort.lastQuery.subjectType());
        assertEquals("user-1", queryPort.lastQuery.subjectId());
        assertEquals(ResourceAction.READ, queryPort.lastQuery.action());
        assertEquals(ContextResourceType.MEMORY.value(), queryPort.lastQuery.resourceType());
        assertEquals("memory-1", queryPort.lastQuery.resourceId());
        assertEquals(AccessDecisionEffect.ALLOW, queryPort.lastQuery.effect());
        assertEquals(ResourceAccessReasonCodes.OWNER_MATCH, queryPort.lastQuery.reasonCode());
        assertEquals(2L, queryPort.lastQuery.current());
        assertEquals(20L, queryPort.lastQuery.size());
        assertEquals(1L, page.total());
    }

    @Test
    void shouldRejectNonAdminAccess() {
        AccessDecisionQueryInboundPort service =
                new KernelAccessDecisionQueryService(new MemoryAccessDecisionQueryPort(), user());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.page(null, null, null, null, null, null, null, null, 1L, 10L));

        assertEquals("权限不足", error.getMessage());
    }

    private static AccessDecision decision() {
        return new AccessDecision(
                "decision-1",
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                ContextResourceType.MEMORY.value(),
                "memory-1",
                AccessDecisionEffect.ALLOW,
                ResourceAccessReasonCodes.OWNER_MATCH,
                NOW);
    }

    private static CurrentUserPort adminUser() {
        return () -> Optional.of(new CurrentUser("admin-1", "root", "admin", null));
    }

    private static CurrentUserPort user() {
        return () -> Optional.of(new CurrentUser("user-1", "alice", "user", null));
    }

    private static final class MemoryAccessDecisionQueryPort implements AccessDecisionQueryPort {

        private AccessDecisionQuery lastQuery;

        @Override
        public AccessDecisionPage page(AccessDecisionQuery query) {
            lastQuery = query;
            return new AccessDecisionPage(List.of(decision()), 1L, query.size(), query.current(), 1L);
        }
    }
}
