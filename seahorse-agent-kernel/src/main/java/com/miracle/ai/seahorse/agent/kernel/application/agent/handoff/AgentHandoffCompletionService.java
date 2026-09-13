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

package com.miracle.ai.seahorse.agent.kernel.application.agent.handoff;

import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffFailureCode;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Handoff 完成态回写服务（Multi-Agent A2A 设计 §5.2 P0）。
 *
 * <p>当 child run 到达终态时，将关联 handoff 从 RUNNING 收敛为
 * SUCCEEDED / FAILED / CANCELLED 并写完成审计。只依赖 handoff 仓储与审计，
 * 不依赖 run 执行面，因此可以安全地被 {@code KernelAgentRunService} 在
 * 终态转移处调用而不引入循环依赖。幂等：已终态的 handoff 原样返回。
 */
public class AgentHandoffCompletionService {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final AgentHandoffRepositoryPort handoffRepository;
    private final KernelAuditLedgerService auditLedger;
    private final Clock clock;

    public AgentHandoffCompletionService(AgentHandoffRepositoryPort handoffRepository,
                                         KernelAuditLedgerService auditLedger,
                                         Clock clock) {
        this.handoffRepository = Objects.requireNonNull(handoffRepository, "handoffRepository must not be null");
        this.auditLedger = auditLedger;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    /**
     * 按 childRunId 回写 handoff 完成态。
     *
     * @param childRunId  child run ID
     * @param success     true 收敛为 SUCCEEDED；false 收敛为 FAILED（failureCode 为空时用 CHILD_RUN_FAILED）
     * @param failureCode 失败码（success 为 true 时忽略）
     * @return 回写后的 handoff；未找到关联 handoff 或已终态时返回 null
     */
    public AgentHandoff completeForChildRun(String childRunId, boolean success, AgentHandoffFailureCode failureCode) {
        if (childRunId == null || childRunId.isBlank()) {
            return null;
        }
        return handoffRepository.findByChildRunId(childRunId.trim())
                .map(handoff -> complete(handoff, success, failureCode))
                .orElse(null);
    }

    private AgentHandoff complete(AgentHandoff current, boolean success, AgentHandoffFailureCode failureCode) {
        if (current.status().isTerminal()) {
            return null;
        }
        Instant now = clock.instant();
        AgentHandoff updated = success
                ? current.succeed(now)
                : current.fail(Objects.requireNonNullElse(failureCode, AgentHandoffFailureCode.CHILD_RUN_FAILED),
                        now);
        AgentHandoff saved = handoffRepository.update(updated);
        appendFinishedAudit(saved);
        return saved;
    }

    /**
     * child run 被取消时把 handoff 收敛为 CANCELLED。
     */
    public AgentHandoff cancelForChildRun(String childRunId) {
        if (childRunId == null || childRunId.isBlank()) {
            return null;
        }
        return handoffRepository.findByChildRunId(childRunId.trim())
                .map(handoff -> {
                    if (handoff.status().isTerminal()) {
                        return null;
                    }
                    AgentHandoff saved = handoffRepository.update(handoff.cancel(clock.instant()));
                    appendFinishedAudit(saved);
                    return saved;
                })
                .orElse(null);
    }

    private void appendFinishedAudit(AgentHandoff handoff) {
        if (auditLedger == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handoffId", handoff.handoffId());
        payload.put("childRunId", handoff.childRunId());
        payload.put("status", handoff.status().name());
        payload.put("failureCode", handoff.failureCode() == null ? "" : handoff.failureCode().name());
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize handoff completion audit payload", ex);
        }
        auditLedger.append(new AuditEvent(
                "audit_" + SnowflakeIds.nextIdString(),
                handoff.tenantId(),
                AuditEventType.AGENT_HANDOFF_FINISHED,
                AuditActorType.AGENT,
                handoff.sourceAgentId(),
                handoff.parentRunId(),
                handoff.sourceAgentId(),
                "AGENT_HANDOFF",
                handoff.handoffId(),
                json,
                clock.instant()));
    }
}
