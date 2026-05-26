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

package com.miracle.ai.seahorse.agent.kernel.application.agent.audit;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAuditLedgerServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldAppendRedactedAuditEvent() {
        RecordingAuditRepository repository = new RecordingAuditRepository(false);
        KernelAuditLedgerService service = new KernelAuditLedgerService(
                repository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);

        AuditEvent saved = service.append(event("{\"token\":\"secret-token\",\"safe\":\"ok\"}"));

        assertEquals(saved, repository.saved);
        assertTrue(saved.redactedPayload().contains(AuditRedactionPolicy.REDACTED_VALUE));
        assertTrue(saved.redactedPayload().contains("ok"));
        assertFalse(saved.redactedPayload().contains("secret-token"));
    }

    @Test
    void shouldRespectWriteFailurePolicies() {
        AuditEvent event = event("{\"safe\":\"ok\"}");

        assertThrows(IllegalStateException.class, () -> new KernelAuditLedgerService(
                new RecordingAuditRepository(true),
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED).append(event));

        AuditEvent warned = new KernelAuditLedgerService(
                new RecordingAuditRepository(true),
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.WARN_AND_CONTINUE).append(event);
        assertEquals(event.auditId(), warned.auditId());

        RecordingAuditRepository noopRepository = new RecordingAuditRepository(false);
        AuditEvent nooped = new KernelAuditLedgerService(
                noopRepository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.NOOP).append(event);
        assertEquals(event.auditId(), nooped.auditId());
        assertEquals(null, noopRepository.saved);
    }

    private static AuditEvent event(String payload) {
        return new AuditEvent(
                "audit-1",
                "tenant-a",
                AuditEventType.SECRET_USED,
                AuditActorType.SYSTEM,
                "credential-provider",
                "run-1",
                "agent-1",
                "secret",
                "secret://tenant/a",
                payload,
                NOW);
    }

    private static final class RecordingAuditRepository implements AuditEventRepositoryPort {

        private final boolean fail;
        private AuditEvent saved;

        private RecordingAuditRepository(boolean fail) {
            this.fail = fail;
        }

        @Override
        public AuditEvent save(AuditEvent event) {
            if (fail) {
                throw new IllegalStateException("audit store unavailable");
            }
            saved = event;
            return event;
        }

        @Override
        public Optional<AuditEvent> findById(String auditId) {
            return Optional.ofNullable(saved);
        }

        @Override
        public AuditEventPage page(AuditEventQuery query) {
            return new AuditEventPage(saved == null ? List.of() : List.of(saved), saved == null ? 0L : 1L, 10L, 1L, 1L);
        }
    }
}
