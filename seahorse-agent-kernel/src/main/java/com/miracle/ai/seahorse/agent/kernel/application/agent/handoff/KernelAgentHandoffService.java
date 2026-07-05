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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.MeshPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.MeshPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentHandoffInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.MeshPolicyPort;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KernelAgentHandoffService implements AgentHandoffInboundPort {

    private static final String HANDOFF_ID_PREFIX = "handoff_";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentHandoffRepositoryPort handoffRepository;
    private final AgentRunInboundPort runPort;
    private final MeshPolicyPort meshPolicyPort;
    private final KernelAuditLedgerService auditLedger;
    private final Clock clock;

    public KernelAgentHandoffService(AgentHandoffRepositoryPort handoffRepository,
                                     AgentRunInboundPort runPort,
                                     MeshPolicyPort meshPolicyPort,
                                     Clock clock) {
        this(handoffRepository, runPort, meshPolicyPort, null, clock);
    }

    public KernelAgentHandoffService(AgentHandoffRepositoryPort handoffRepository,
                                     AgentRunInboundPort runPort,
                                     MeshPolicyPort meshPolicyPort,
                                     KernelAuditLedgerService auditLedger,
                                     Clock clock) {
        this.handoffRepository = Objects.requireNonNull(handoffRepository, "handoffRepository must not be null");
        this.runPort = Objects.requireNonNull(runPort, "runPort must not be null");
        this.meshPolicyPort = Objects.requireNonNull(meshPolicyPort, "meshPolicyPort must not be null");
        this.auditLedger = auditLedger;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    public AgentHandoff createLocalHandoff(AgentHandoffCreateCommand command) {
        AgentHandoffCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        MeshPolicyDecision decision = meshPolicyPort.decide(new MeshPolicyRequest(
                safeCommand.tenantId(),
                safeCommand.parentRunId(),
                safeCommand.sourceAgentId(),
                safeCommand.targetAgentId(),
                safeCommand.depth(),
                safeCommand.ancestorAgentIds()));
        AgentHandoff created = new AgentHandoff(
                nextHandoffId(),
                safeCommand.tenantId(),
                safeCommand.parentRunId(),
                null,
                safeCommand.sourceAgentId(),
                safeCommand.targetAgentId(),
                AgentHandoffStatus.CREATED,
                null,
                safeCommand.handoffReason(),
                safeCommand.contextPackId(),
                inputSummaryJson(safeCommand.inputSummary()),
                safeCommand.contextSummaryJson(),
                now,
                now,
                null);
        if (!decision.allowed()) {
            AgentHandoff failed = handoffRepository.save(created.fail(decision.failureCode(), now));
            appendCreatedAudit(failed);
            appendFinishedAudit(failed);
            return failed;
        }
        AgentRun childRun = runPort.startRun(new AgentRunStartCommand(
                safeCommand.targetAgentId(),
                safeCommand.targetVersionId(),
                null,
                safeCommand.tenantId(),
                safeCommand.parentRunId(),
                AgentRunTriggerType.A2A,
                truncate(safeCommand.inputSummary(), AgentHandoffLimits.INPUT_SUMMARY_MAX_LENGTH),
                safeCommand.traceId(),
                childRunMetadataJson(created)));
        AgentHandoff running = handoffRepository.save(created.running(childRun.runId(), now));
        appendCreatedAudit(running);
        return running;
    }

    @Override
    public AgentHandoff cancel(String handoffId) {
        AgentHandoff current = handoffRepository.findById(requireText(handoffId, "handoffId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Agent handoff does not exist"));
        if (current.status().isTerminal()) {
            return current;
        }
        if (current.childRunId() != null) {
            runPort.cancel(current.childRunId());
        }
        AgentHandoff cancelled = current.cancel(clock.instant());
        AgentHandoff updated = handoffRepository.update(cancelled);
        appendFinishedAudit(updated);
        return updated;
    }

    @Override
    public AgentHandoff findById(String handoffId) {
        return handoffRepository.findById(requireText(handoffId, "handoffId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Agent handoff does not exist"));
    }

    @Override
    public List<AgentHandoff> listByParentRunId(String tenantId, String parentRunId) {
        return handoffRepository.listByParentRunId(tenantId, requireText(parentRunId, "parentRunId must not be blank"));
    }

    private String inputSummaryJson(String inputSummary) {
        return json(Map.of("inputSummary", Objects.requireNonNullElse(
                truncate(inputSummary, AgentHandoffLimits.INPUT_SUMMARY_MAX_LENGTH), "")));
    }

    private String nextHandoffId() {
        return HANDOFF_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private void appendCreatedAudit(AgentHandoff handoff) {
        appendAudit(AuditEventType.AGENT_HANDOFF_CREATED, handoff, createdPayload(handoff));
    }

    private void appendFinishedAudit(AgentHandoff handoff) {
        appendAudit(AuditEventType.AGENT_HANDOFF_FINISHED, handoff, finishedPayload(handoff));
    }

    private void appendAudit(AuditEventType eventType, AgentHandoff handoff, String payload) {
        if (auditLedger == null) {
            return;
        }
        auditLedger.append(new AuditEvent(
                "audit_" + SnowflakeIds.nextIdString(),
                handoff.tenantId(),
                eventType,
                AuditActorType.AGENT,
                handoff.sourceAgentId(),
                handoff.parentRunId(),
                handoff.sourceAgentId(),
                "AGENT_HANDOFF",
                handoff.handoffId(),
                payload,
                clock.instant()));
    }

    private String createdPayload(AgentHandoff handoff) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handoffId", handoff.handoffId());
        payload.put("parentRunId", handoff.parentRunId());
        payload.put("childRunId", handoff.childRunId());
        payload.put("sourceAgentId", handoff.sourceAgentId());
        payload.put("targetAgentId", handoff.targetAgentId());
        payload.put("status", handoff.status().name());
        payload.put("contextPackId", Objects.requireNonNullElse(handoff.contextPackId(), ""));
        payload.put("inputSummaryLength", lengthOf(handoff.inputSummaryJson()));
        payload.put("contextSummaryLength", lengthOf(handoff.contextSummaryJson()));
        return json(payload);
    }

    private String finishedPayload(AgentHandoff handoff) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handoffId", handoff.handoffId());
        payload.put("childRunId", handoff.childRunId());
        payload.put("status", handoff.status().name());
        payload.put("contextPackId", Objects.requireNonNullElse(handoff.contextPackId(), ""));
        payload.put("failureCode", handoff.failureCode() == null ? "" : handoff.failureCode().name());
        return json(payload);
    }

    private String childRunMetadataJson(AgentHandoff handoff) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handoffId", handoff.handoffId());
        payload.put("parentRunId", handoff.parentRunId());
        payload.put("contextPackId", Objects.requireNonNullElse(handoff.contextPackId(), ""));
        payload.put("contextSummaryJson", Objects.requireNonNullElse(handoff.contextSummaryJson(), ""));
        return json(payload);
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String json(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize handoff JSON payload", ex);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
