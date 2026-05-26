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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class KernelAuditLedgerService implements AuditQueryInboundPort {

    private final AuditEventRepositoryPort repository;
    private final AuditRedactionPolicy redactionPolicy;
    private final AuditWriteFailurePolicy failurePolicy;

    public KernelAuditLedgerService(AuditEventRepositoryPort repository,
                                    AuditRedactionPolicy redactionPolicy,
                                    AuditWriteFailurePolicy failurePolicy) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.redactionPolicy = Objects.requireNonNull(redactionPolicy, "redactionPolicy must not be null");
        this.failurePolicy = Objects.requireNonNullElse(failurePolicy, AuditWriteFailurePolicy.FAIL_CLOSED);
    }

    public AuditEvent append(AuditEvent event) {
        AuditEvent safeEvent = Objects.requireNonNull(event, "event must not be null")
                .withRedactedPayload(redactionPolicy.redact(event.redactedPayload()));
        if (failurePolicy == AuditWriteFailurePolicy.NOOP) {
            return safeEvent;
        }
        try {
            return repository.save(safeEvent);
        } catch (RuntimeException ex) {
            if (failurePolicy == AuditWriteFailurePolicy.WARN_AND_CONTINUE) {
                return safeEvent;
            }
            throw new IllegalStateException("Failed to write audit event", ex);
        }
    }

    @Override
    public Optional<AuditEvent> findById(String auditId) {
        return repository.findById(auditId);
    }

    @Override
    public AuditEventPage page(String tenantId,
                               String runId,
                               String agentId,
                               String resourceType,
                               String resourceId,
                               AuditEventType eventType,
                               Instant occurredFrom,
                               Instant occurredTo,
                               long current,
                               long size) {
        return repository.page(new AuditEventQuery(
                tenantId,
                runId,
                agentId,
                resourceType,
                resourceId,
                eventType,
                occurredFrom,
                occurredTo,
                current,
                size));
    }
}
