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

package com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 沙箱会话/执行审计协作者（从 {@link KernelSandboxRuntimeService} 提取）。
 * 按 §7 收敛原则外提：只负责向审计台账追加会话/执行/运行时故障转移事件。
 */
final class SandboxAuditSupport {

    private static final ObjectMapper AUDIT_OBJECT_MAPPER = new ObjectMapper();
    private static final String AUDIT_ID_PREFIX = "audit_sandbox_";
    private static final String AUDIT_ACTOR_ID = "sandbox-runtime";
    private static final String RESOURCE_TYPE_SANDBOX_SESSION = "SANDBOX_SESSION";
    private static final String RESOURCE_TYPE_SANDBOX_EXECUTION = "SANDBOX_EXECUTION";

    private final KernelAuditLedgerService auditLedger;
    private final Clock clock;

    SandboxAuditSupport(KernelAuditLedgerService auditLedger, Clock clock) {
        this.auditLedger = auditLedger;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    void appendSessionAudit(SandboxSession session, AuditEventType auditEventType) {
        if (auditLedger == null) {
            return;
        }
        Instant now = clock.instant();
        auditLedger.append(new AuditEvent(
                auditId(),
                session.tenantId(),
                auditEventType,
                AuditActorType.SYSTEM,
                AUDIT_ACTOR_ID,
                session.runId(),
                null,
                RESOURCE_TYPE_SANDBOX_SESSION,
                session.sessionId(),
                """
                        {"sessionId":"%s","runtimeType":"%s","profileId":"%s","expiresAt":"%s","status":"%s","reasonCode":"%s"}
                        """.formatted(session.sessionId(),
                        session.runtimeType().name(),
                        session.profileId(),
                        session.expiresAt(),
                        session.status().name(),
                        session.reasonCode().name()),
                now));
    }

    void appendExecutionAudit(SandboxSession session,
                              SandboxExecution execution,
                              int artifactCount,
                              int promptVisibleArtifactCount,
                              SandboxPolicyReasonCode reasonCode) {
        if (auditLedger == null) {
            return;
        }
        Instant now = clock.instant();
        auditLedger.append(new AuditEvent(
                auditId(),
                session.tenantId(),
                AuditEventType.SANDBOX_EXECUTION_FINISHED,
                AuditActorType.SYSTEM,
                AUDIT_ACTOR_ID,
                session.runId(),
                null,
                RESOURCE_TYPE_SANDBOX_EXECUTION,
                execution.executionId(),
                """
                        {"sessionId":"%s","executionId":"%s","runtimeType":"%s","status":"%s","reasonCode":"%s","artifactCount":%d,"promptVisibleArtifactCount":%d}
                        """.formatted(session.sessionId(),
                        execution.executionId(),
                        execution.runtimeType().name(),
                        execution.status().name(),
                        reasonCode.name(),
                        artifactCount,
                        promptVisibleArtifactCount),
                now));
    }

    void appendRuntimeCreateFailoverAudit(SandboxSession session,
                                          KernelSandboxRuntimeService.RuntimeCreateFailoverAudit failoverAudit,
                                          int attemptCount) {
        if (auditLedger == null) {
            return;
        }
        auditLedger.append(new AuditEvent(
                auditId(),
                session.tenantId(),
                AuditEventType.SANDBOX_RUNTIME_CREATE_FAILED_OVER,
                AuditActorType.SYSTEM,
                AUDIT_ACTOR_ID,
                session.runId(),
                null,
                RESOURCE_TYPE_SANDBOX_SESSION,
                session.sessionId(),
                auditPayload(Map.of(
                        "fromNodeId", failoverAudit.fromNodeId(),
                        "toNodeId", session.runtimeNodeId(),
                        "recovery", failoverAudit.recovery().name(),
                        "attemptCount", attemptCount)),
                clock.instant()));
    }

    private String auditId() {
        return AUDIT_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private static String auditPayload(Map<String, ?> payload) {
        try {
            return AUDIT_OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize sandbox audit payload", ex);
        }
    }
}
