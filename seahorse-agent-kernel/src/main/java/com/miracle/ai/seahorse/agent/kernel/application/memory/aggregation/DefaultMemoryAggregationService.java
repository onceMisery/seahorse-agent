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

package com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationAppendResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationSchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferSnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class DefaultMemoryAggregationService implements MemoryAggregationServicePort {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMemoryAggregationService.class);

    private static final String DEFAULT_TENANT_ID = "default";
    private static final String FLUSH_SOURCE = "memory-aggregation-flush";

    private final MemoryAggregationPolicy policy;
    private final MemoryAggregationBufferPort bufferPort;
    private final MemoryAggregationSchedulerPort schedulerPort;
    private final MemoryIngestionWorkflowPort ingestionWorkflowPort;
    private final MemoryTraceRecorder traceRecorder;
    private final Clock clock;

    public DefaultMemoryAggregationService(MemoryAggregationPolicy policy,
                                           MemoryAggregationBufferPort bufferPort,
                                           MemoryAggregationSchedulerPort schedulerPort,
                                           MemoryIngestionWorkflowPort ingestionWorkflowPort) {
        this(policy, bufferPort, schedulerPort, ingestionWorkflowPort, MemoryTraceRecorder.noop(), Clock.systemUTC());
    }

    public DefaultMemoryAggregationService(MemoryAggregationPolicy policy,
                                           MemoryAggregationBufferPort bufferPort,
                                           MemoryAggregationSchedulerPort schedulerPort,
                                           MemoryIngestionWorkflowPort ingestionWorkflowPort,
                                           MemoryTraceRecorder traceRecorder) {
        this(policy, bufferPort, schedulerPort, ingestionWorkflowPort, traceRecorder, Clock.systemUTC());
    }

    public DefaultMemoryAggregationService(MemoryAggregationPolicy policy,
                                           MemoryAggregationBufferPort bufferPort,
                                           MemoryAggregationSchedulerPort schedulerPort,
                                           MemoryIngestionWorkflowPort ingestionWorkflowPort,
                                           Clock clock) {
        this(policy, bufferPort, schedulerPort, ingestionWorkflowPort, MemoryTraceRecorder.noop(), clock);
    }

    public DefaultMemoryAggregationService(MemoryAggregationPolicy policy,
                                           MemoryAggregationBufferPort bufferPort,
                                           MemoryAggregationSchedulerPort schedulerPort,
                                           MemoryIngestionWorkflowPort ingestionWorkflowPort,
                                           MemoryTraceRecorder traceRecorder,
                                           Clock clock) {
        this.policy = Objects.requireNonNullElseGet(policy, MemoryAggregationPolicy::defaults);
        this.bufferPort = Objects.requireNonNullElseGet(bufferPort, MemoryAggregationBufferPort::noop);
        this.schedulerPort = Objects.requireNonNullElseGet(schedulerPort, MemoryAggregationSchedulerPort::noop);
        this.ingestionWorkflowPort = Objects.requireNonNull(ingestionWorkflowPort,
                "ingestionWorkflowPort must not be null");
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public MemoryAggregationAppendResult appendTurn(MemoryTurnEvent event) {
        if (!policy.enabled()) {
            recordTrace("append-turn", MemoryTraceEvent.STATUS_IGNORED, event == null ? "" : event.sessionId(),
                    details("reason", "aggregation_disabled", "forceFlushRequired", false));
            return MemoryAggregationAppendResult.pending(noopState(event));
        }
        MemoryBufferState state = bufferPort.appendTurn(event);
        recordTrace("append-turn", MemoryTraceEvent.STATUS_SUCCESS, state.sessionId(),
                details("turnCount", state.turnCount(),
                        "tokenCount", state.totalTokens(),
                        "forceFlushRequired", state.forceFlushRequired()));
        if (state.forceFlushRequired()) {
            MemoryFlushTrigger trigger = Objects.requireNonNullElse(
                    state.forceFlushTrigger(), MemoryFlushTrigger.FORCE_TURNS);
            Optional<MemoryBufferSnapshot> snapshot = bufferPort.flushReady(
                    state.sessionId(), state.tenantId(), trigger, Instant.now(clock));
            if (snapshot.isPresent()) {
                MemoryIngestionResult result = submit(snapshot.get());
                recordTrace("flush-ready", result.status() == MemoryIngestionStatus.ACCEPTED
                                ? MemoryTraceEvent.STATUS_SUCCESS
                                : MemoryTraceEvent.STATUS_FAILED,
                        snapshot.get().snapshotId(), details(
                                "trigger", trigger.name(),
                                "resultStatus", result.status().name(),
                                "reason", result.reason()));
                return MemoryAggregationAppendResult.flushed(state, snapshot.get(), result);
            }
        }
        schedulerPort.scheduleIdleCheck(
                state.sessionId(),
                state.tenantId(),
                state.lastActivityAt().plusMillis(policy.idleFlushMillis()));
        return MemoryAggregationAppendResult.pending(state);
    }

    @Override
    public MemoryIngestionResult flushReady(String sessionId,
                                            String tenantId,
                                            MemoryFlushTrigger trigger,
                                            Instant now) {
        if (!policy.enabled()) {
            recordTrace("flush-ready", MemoryTraceEvent.STATUS_IGNORED, sessionId,
                    details("reason", "aggregation_disabled", "trigger", trigger == null ? "" : trigger.name()));
            return MemoryIngestionResult.ignored("aggregation_disabled");
        }
        Optional<MemoryBufferSnapshot> snapshot = bufferPort.flushReady(
                        sessionId,
                        normalizeTenantId(tenantId),
                        Objects.requireNonNullElse(trigger, MemoryFlushTrigger.MANUAL),
                        Objects.requireNonNullElseGet(now, () -> Instant.now(clock)));
        if (snapshot.isEmpty()) {
            recordTrace("flush-ready", MemoryTraceEvent.STATUS_IGNORED, sessionId,
                    details("reason", "aggregation_not_ready",
                            "trigger", trigger == null ? "" : trigger.name()));
            return MemoryIngestionResult.ignored("aggregation_not_ready");
        }
        MemoryIngestionResult result = submit(snapshot.get());
        recordTrace("flush-ready", result.status() == MemoryIngestionStatus.ACCEPTED
                        ? MemoryTraceEvent.STATUS_SUCCESS
                        : MemoryTraceEvent.STATUS_FAILED,
                snapshot.get().snapshotId(), details("trigger", trigger == null ? "" : trigger.name(),
                        "resultStatus", result.status().name(),
                        "reason", result.reason()));
        return result;
    }

    @Override
    public int flushIdleReady(Instant now, int limit) {
        if (!policy.enabled()) {
            return 0;
        }
        Instant safeNow = Objects.requireNonNullElseGet(now, () -> Instant.now(clock));
        int safeLimit = limit <= 0 ? policy.maxContextBlocks() : limit;
        int flushed = 0;
        for (MemoryBufferState state : bufferPort.listStates(safeLimit)) {
            MemoryIngestionResult result = flushReady(
                    state.sessionId(), state.tenantId(), MemoryFlushTrigger.IDLE_TIMEOUT, safeNow);
            if (result.status() == MemoryIngestionStatus.ACCEPTED) {
                flushed++;
            }
        }
        return flushed;
    }

    @Override
    public Optional<MemoryBufferState> state(String sessionId, String tenantId) {
        return bufferPort.state(sessionId, normalizeTenantId(tenantId));
    }

    private MemoryIngestionResult submit(MemoryBufferSnapshot snapshot) {
        if (snapshot.turns().isEmpty()) {
            recordTrace("submit", MemoryTraceEvent.STATUS_IGNORED, snapshot.snapshotId(),
                    details("reason", "empty_snapshot"));
            return MemoryIngestionResult.ignored("empty_snapshot");
        }
        try {
            MemoryWriteRequest writeRequest = MemoryWriteRequest.builder()
                    .conversationId(snapshot.conversationId())
                    .userId(snapshot.userId())
                    .messageId(snapshot.snapshotId())
                    .message(ChatMessage.user(formatContextBlock(snapshot)))
                    .build();
            MemoryIngestionResult result = ingestionWorkflowPort.ingest(new MemoryIngestionCommand(
                    operationId(snapshot),
                    snapshot.tenantId(),
                    FLUSH_SOURCE,
                    writeRequest));
            if (result == null) {
                recordTrace("submit", MemoryTraceEvent.STATUS_FAILED, snapshot.snapshotId(), details(
                        "operationId", operationId(snapshot),
                        "error", "null_result",
                        "message", "ingestionWorkflowPort returned null"));
                return MemoryIngestionResult.failed("aggregation_flush_failed");
            }
            recordTrace("submit", MemoryTraceEvent.STATUS_SUCCESS, snapshot.snapshotId(), details(
                    "operationId", operationId(snapshot),
                    "conversationId", snapshot.conversationId(),
                    "turnCount", snapshot.turns().size()));
            return result;
        } catch (Exception ex) {
            LOG.warn("Memory aggregation flush failed: snapshotId={}, userId={}, conversationId={}",
                    snapshot.snapshotId(), snapshot.userId(), snapshot.conversationId(), ex);
            recordTrace("submit", MemoryTraceEvent.STATUS_FAILED, snapshot.snapshotId(), details(
                    "operationId", operationId(snapshot),
                    "error", ex.getClass().getSimpleName(),
                    "message", Objects.requireNonNullElse(ex.getMessage(), "")));
            return MemoryIngestionResult.failed("aggregation_flush_failed");
        }
    }

    private String formatContextBlock(MemoryBufferSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        if (!snapshot.turns().isEmpty()) {
            builder.append(snapshot.turns().get(0).userText());
        }
        for (int i = 0; i < snapshot.turns().size(); i++) {
            MemoryTurnEvent turn = snapshot.turns().get(i);
            if (i > 0 && !turn.userText().isBlank()) {
                builder.append("\nUser: ").append(turn.userText());
            }
            if (!turn.assistantText().isBlank()) {
                builder.append("\nAssistant: ").append(turn.assistantText());
            }
        }
        return builder.toString().trim();
    }

    private String operationId(MemoryBufferSnapshot snapshot) {
        return "memory-aggregate-" + snapshot.snapshotId();
    }

    private MemoryBufferState noopState(MemoryTurnEvent event) {
        return new MemoryBufferState(
                event == null ? DEFAULT_TENANT_ID : event.tenantId(),
                event == null ? "" : event.userId(),
                event == null ? "" : event.conversationId(),
                event == null ? "" : event.sessionId(),
                0,
                0,
                Instant.now(clock),
                false,
                null);
    }

    private String normalizeTenantId(String tenantId) {
        String normalized = Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID).trim();
        return normalized.isBlank() ? DEFAULT_TENANT_ID : normalized;
    }

    private void recordTrace(String eventType, String status, String subjectId, Map<String, Object> details) {
        traceRecorder.record(new MemoryTraceEvent(
                "",
                DEFAULT_TENANT_ID,
                "",
                "",
                "",
                "memory-aggregation",
                eventType,
                status,
                subjectId,
                "snapshot",
                details,
                Instant.now(clock)));
    }

    private Map<String, Object> details(Object... entries) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            details.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return details;
    }
}
