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

import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuditedResourceAccessPolicyPortTests {

    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void shouldRecordDelegateDecision() {
        AccessDecision decision = new AccessDecision(
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
        ResourceAccessPolicyPort delegate = request -> decision;
        RecordingAccessDecisionLogPort logPort = new RecordingAccessDecisionLogPort();
        AuditedResourceAccessPolicyPort policy = new AuditedResourceAccessPolicyPort(delegate, logPort);

        AccessDecision result = policy.decide(new ResourceAccessRequest(
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                new ResourceRef(ContextResourceType.MEMORY, "memory-1", "tenant-1", "user-1", "{}")));

        assertSame(decision, result);
        assertSame(decision, logPort.recordedDecision);
        assertEquals(1, logPort.recordCount);
    }

    @Test
    void shouldWriteContextAccessAuditWithoutChangingDecision() {
        AccessDecision decision = new AccessDecision(
                "decision-2",
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "agent-1",
                ResourceAction.READ,
                ContextResourceType.DOCUMENT.value(),
                "doc-1",
                AccessDecisionEffect.DENY,
                ResourceAccessReasonCodes.RESOURCE_ACL_DENY,
                NOW);
        ResourceAccessPolicyPort delegate = request -> decision;
        RecordingAuditEventRepository auditRepository = new RecordingAuditEventRepository();
        KernelAuditLedgerService auditLedger = new KernelAuditLedgerService(
                auditRepository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);
        AuditedResourceAccessPolicyPort policy = new AuditedResourceAccessPolicyPort(
                delegate,
                AccessDecisionLogPort.empty(),
                auditLedger);

        AccessDecision result = policy.decide(new ResourceAccessRequest(
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "agent-1",
                ResourceAction.READ,
                new ResourceRef(ContextResourceType.DOCUMENT, "doc-1", "tenant-1", "owner-1",
                        "{\"secret\":\"raw-secret\"}")));

        assertSame(decision, result);
        assertEquals(1, auditRepository.events.size());
        AuditEvent event = auditRepository.events.get(0);
        assertEquals(AuditEventType.CONTEXT_ACCESSED, event.eventType());
        assertEquals("tenant-1", event.tenantId());
        assertEquals(ContextResourceType.DOCUMENT.value(), event.resourceType());
        assertEquals("doc-1", event.resourceId());
        assertFalse(event.redactedPayload().contains("raw-secret"));
        assertFalse(event.redactedPayload().contains("secret"));
        assertEquals(true, event.redactedPayload().contains("\"decisionId\":\"decision-2\""));
    }

    private static final class RecordingAccessDecisionLogPort implements AccessDecisionLogPort {

        private AccessDecision recordedDecision;
        private int recordCount;

        @Override
        public void record(AccessDecision decision) {
            recordedDecision = decision;
            recordCount++;
        }
    }

    private static final class RecordingAuditEventRepository implements AuditEventRepositoryPort {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public AuditEvent save(AuditEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public Optional<AuditEvent> findById(String auditId) {
            return events.stream().filter(event -> event.auditId().equals(auditId)).findFirst();
        }

        @Override
        public AuditEventPage page(AuditEventQuery query) {
            return new AuditEventPage(events, events.size(), 10L, 1L, events.isEmpty() ? 0L : 1L);
        }
    }
}
