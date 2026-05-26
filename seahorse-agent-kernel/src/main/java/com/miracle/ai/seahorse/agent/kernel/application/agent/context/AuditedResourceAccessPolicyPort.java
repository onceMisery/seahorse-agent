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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;

import java.util.Objects;
import java.util.UUID;

public class AuditedResourceAccessPolicyPort implements ResourceAccessPolicyPort {

    private static final String AUDIT_ID_PREFIX = "audit_";

    private final ResourceAccessPolicyPort delegate;
    private final AccessDecisionLogPort logPort;
    private final KernelAuditLedgerService auditLedger;

    public AuditedResourceAccessPolicyPort(ResourceAccessPolicyPort delegate,
                                           AccessDecisionLogPort logPort) {
        this(delegate, logPort, null);
    }

    public AuditedResourceAccessPolicyPort(ResourceAccessPolicyPort delegate,
                                           AccessDecisionLogPort logPort,
                                           KernelAuditLedgerService auditLedger) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.logPort = Objects.requireNonNullElseGet(logPort, AccessDecisionLogPort::empty);
        this.auditLedger = auditLedger;
    }

    @Override
    public AccessDecision decide(ResourceAccessRequest request) {
        AccessDecision decision = delegate.decide(request);
        logPort.record(decision);
        appendAudit(decision);
        return decision;
    }

    private void appendAudit(AccessDecision decision) {
        if (auditLedger == null) {
            return;
        }
        auditLedger.append(new AuditEvent(
                AUDIT_ID_PREFIX + UUID.randomUUID().toString().replace("-", ""),
                decision.tenantId(),
                AuditEventType.CONTEXT_ACCESSED,
                actorType(decision.subjectType()),
                decision.subjectId(),
                null,
                null,
                decision.resourceType(),
                decision.resourceId(),
                """
                        {"decisionId":"%s","effect":"%s","reasonCode":"%s","resourceType":"%s","resourceId":"%s"}
                        """.formatted(
                        json(decision.decisionId()),
                        decision.effect().name(),
                        json(decision.reasonCode()),
                        json(decision.resourceType()),
                        json(decision.resourceId())),
                decision.createdAt()));
    }

    private AuditActorType actorType(AccessSubjectType subjectType) {
        return subjectType == AccessSubjectType.USER ? AuditActorType.USER : AuditActorType.AGENT;
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
