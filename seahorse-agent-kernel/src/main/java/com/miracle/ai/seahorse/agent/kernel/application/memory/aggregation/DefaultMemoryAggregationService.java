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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class DefaultMemoryAggregationService implements MemoryAggregationServicePort {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMemoryAggregationService.class);

    private static final String DEFAULT_TENANT_ID = "default";
    private static final String FLUSH_SOURCE = "memory-aggregation-flush";
    private static final String TRACE_COMPONENT = "memory-aggregation";
    private static final String TRACE_EVENT_APPEND_TURN = "append-turn";
    private static final String TRACE_EVENT_FLUSH_READY = "flush-ready";
    private static final String TRACE_EVENT_SUBMIT = "submit";
    private static final String TRACE_SUBJECT_TYPE_SNAPSHOT = "snapshot";
    private static final String DETAIL_CONVERSATION_ID = "conversationId";
    private static final String DETAIL_ERROR = "error";
    private static final String DETAIL_FORCE_FLUSH_REQUIRED = "forceFlushRequired";
    private static final String DETAIL_FROM = "from";
    private static final String DETAIL_MESSAGE = "message";
    private static final String DETAIL_OPERATION_ID = "operationId";
    private static final String DETAIL_REASON = "reason";
    private static final String DETAIL_RESULT_STATUS = "resultStatus";
    private static final String DETAIL_SOURCE_ASSISTANT_MESSAGE_IDS = "sourceAssistantMessageIds";
    private static final String DETAIL_SOURCE_SPAN_COUNT = "sourceSpanCount";
    private static final String DETAIL_SOURCE_USER_MESSAGE_IDS = "sourceUserMessageIds";
    private static final String DETAIL_TO = "to";
    private static final String DETAIL_TOKEN_COUNT = "tokenCount";
    private static final String DETAIL_TOTAL_TOKENS = "totalTokens";
    private static final String DETAIL_TRIGGER = "trigger";
    private static final String DETAIL_TURN_COUNT = "turnCount";
    private static final String DETAIL_WINDOW_DURATION_MILLIS = "windowDurationMillis";
    private static final String REASON_AGGREGATION_DISABLED = "aggregation_disabled";
    private static final String REASON_AGGREGATION_NOT_READY = "aggregation_not_ready";
    private static final String REASON_EMPTY_SNAPSHOT = "empty_snapshot";
    private static final String REASON_NULL_RESULT = "null_result";
    private static final String REASON_AGGREGATION_FLUSH_FAILED = "aggregation_flush_failed";

    static final String OBSERVATION_FLUSH_EVENT = "memory-aggregation-flush";
    static final String OBSERVATION_ATTR_TRIGGER = "trigger";
    static final String OBSERVATION_ATTR_STATUS = "status";

    private final MemoryAggregationPolicy policy;
    private final MemoryAggregationBufferPort bufferPort;
    private final MemoryAggregationSchedulerPort schedulerPort;
    private final MemoryIngestionWorkflowPort ingestionWorkflowPort;
    private final MemoryTraceRecorder traceRecorder;
    private final MemoryAggregationTopicShiftDetector topicShiftDetector;
    private final ObservationPort observationPort;
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
        this(policy, bufferPort, schedulerPort, ingestionWorkflowPort, traceRecorder,
                new ExplicitCueMemoryAggregationTopicShiftDetector(), clock);
    }

    public DefaultMemoryAggregationService(MemoryAggregationPolicy policy,
                                           MemoryAggregationBufferPort bufferPort,
                                           MemoryAggregationSchedulerPort schedulerPort,
                                           MemoryIngestionWorkflowPort ingestionWorkflowPort,
                                           MemoryTraceRecorder traceRecorder,
                                           MemoryAggregationTopicShiftDetector topicShiftDetector,
                                           Clock clock) {
        this(policy, bufferPort, schedulerPort, ingestionWorkflowPort, traceRecorder, topicShiftDetector,
                ObservationPort.noop(), clock);
    }

    public DefaultMemoryAggregationService(MemoryAggregationPolicy policy,
                                           MemoryAggregationBufferPort bufferPort,
                                           MemoryAggregationSchedulerPort schedulerPort,
                                           MemoryIngestionWorkflowPort ingestionWorkflowPort,
                                           MemoryTraceRecorder traceRecorder,
                                           MemoryAggregationTopicShiftDetector topicShiftDetector,
                                           ObservationPort observationPort,
                                           Clock clock) {
        this.policy = Objects.requireNonNullElseGet(policy, MemoryAggregationPolicy::defaults);
        this.bufferPort = Objects.requireNonNullElseGet(bufferPort, MemoryAggregationBufferPort::noop);
        this.schedulerPort = Objects.requireNonNullElseGet(schedulerPort, MemoryAggregationSchedulerPort::noop);
        this.ingestionWorkflowPort = Objects.requireNonNull(ingestionWorkflowPort,
                "ingestionWorkflowPort must not be null");
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
        this.topicShiftDetector = Objects.requireNonNullElseGet(topicShiftDetector,
                ExplicitCueMemoryAggregationTopicShiftDetector::new);
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public MemoryAggregationAppendResult appendTurn(MemoryTurnEvent event) {
        if (!policy.enabled()) {
            recordTrace(TRACE_EVENT_APPEND_TURN, MemoryTraceEvent.STATUS_IGNORED, event == null ? "" : event.sessionId(),
                    traceContext(event),
                    details(DETAIL_REASON, REASON_AGGREGATION_DISABLED, DETAIL_FORCE_FLUSH_REQUIRED, false));
            return MemoryAggregationAppendResult.pending(noopState(event));
        }
        MemoryAggregationAppendResult topicShiftFlush = flushForTopicShiftIfNeeded(event);
        MemoryBufferState state = bufferPort.appendTurn(event);
        recordTrace(TRACE_EVENT_APPEND_TURN, MemoryTraceEvent.STATUS_SUCCESS, state.sessionId(),
                traceContext(state),
                details(DETAIL_TURN_COUNT, state.turnCount(),
                        DETAIL_TOKEN_COUNT, state.totalTokens(),
                        DETAIL_FORCE_FLUSH_REQUIRED, state.forceFlushRequired()));
        if (topicShiftFlush != null) {
            schedulerPort.scheduleIdleCheck(
                    state.sessionId(),
                    state.tenantId(),
                    state.lastActivityAt().plusMillis(policy.idleFlushMillis()));
            return MemoryAggregationAppendResult.flushed(
                    state, topicShiftFlush.snapshot(), topicShiftFlush.ingestionResult());
        }
        if (state.forceFlushRequired()) {
            MemoryFlushTrigger trigger = Objects.requireNonNullElse(
                    state.forceFlushTrigger(), MemoryFlushTrigger.FORCE_TURNS);
            Optional<MemoryBufferSnapshot> snapshot = bufferPort.flushReady(
                    state.sessionId(), state.tenantId(), trigger, Instant.now(clock));
            if (snapshot.isPresent()) {
                MemoryIngestionResult result = submit(snapshot.get());
                recordTrace(TRACE_EVENT_FLUSH_READY, result.status() == MemoryIngestionStatus.ACCEPTED
                                ? MemoryTraceEvent.STATUS_SUCCESS
                                : MemoryTraceEvent.STATUS_FAILED,
                        snapshot.get().snapshotId(), traceContext(snapshot.get()),
                        flushDetails(snapshot.get(), trigger, result));
                return MemoryAggregationAppendResult.flushed(state, snapshot.get(), result);
            }
        }
        schedulerPort.scheduleIdleCheck(
                state.sessionId(),
                state.tenantId(),
                state.lastActivityAt().plusMillis(policy.idleFlushMillis()));
        return MemoryAggregationAppendResult.pending(state);
    }

    private MemoryAggregationAppendResult flushForTopicShiftIfNeeded(MemoryTurnEvent event) {
        if (event == null || !policy.topicShiftFlushEnabled()) {
            return null;
        }
        Optional<MemoryBufferState> currentState = bufferPort.state(event.sessionId(), event.tenantId());
        if (currentState.isEmpty() || !topicShiftDetector.shouldStartNewTopic(event, currentState.get())) {
            return null;
        }
        Optional<MemoryBufferSnapshot> snapshot = bufferPort.flushReady(
                event.sessionId(), event.tenantId(), MemoryFlushTrigger.TOPIC_SHIFT, Instant.now(clock));
        if (snapshot.isEmpty()) {
            return null;
        }
        MemoryIngestionResult result = submit(snapshot.get());
        recordTrace(TRACE_EVENT_FLUSH_READY, result.status() == MemoryIngestionStatus.ACCEPTED
                        ? MemoryTraceEvent.STATUS_SUCCESS
                        : MemoryTraceEvent.STATUS_FAILED,
                snapshot.get().snapshotId(), traceContext(snapshot.get()),
                flushDetails(snapshot.get(), MemoryFlushTrigger.TOPIC_SHIFT, result));
        return MemoryAggregationAppendResult.flushed(currentState.get(), snapshot.get(), result);
    }

    @Override
    public MemoryIngestionResult flushReady(String sessionId,
                                            String tenantId,
                                            MemoryFlushTrigger trigger,
                                            Instant now) {
        if (!policy.enabled()) {
            recordTrace(TRACE_EVENT_FLUSH_READY, MemoryTraceEvent.STATUS_IGNORED, sessionId,
                    traceContext(tenantId, sessionId),
                    details(DETAIL_REASON, REASON_AGGREGATION_DISABLED,
                            DETAIL_TRIGGER, trigger == null ? "" : trigger.name()));
            return MemoryIngestionResult.ignored(REASON_AGGREGATION_DISABLED);
        }
        Optional<MemoryBufferSnapshot> snapshot = bufferPort.flushReady(
                        sessionId,
                        normalizeTenantId(tenantId),
                        Objects.requireNonNullElse(trigger, MemoryFlushTrigger.MANUAL),
                        Objects.requireNonNullElseGet(now, () -> Instant.now(clock)));
        if (snapshot.isEmpty()) {
            recordTrace(TRACE_EVENT_FLUSH_READY, MemoryTraceEvent.STATUS_IGNORED, sessionId,
                    traceContext(tenantId, sessionId),
                    details(DETAIL_REASON, REASON_AGGREGATION_NOT_READY,
                            DETAIL_TRIGGER, trigger == null ? "" : trigger.name()));
            return MemoryIngestionResult.ignored(REASON_AGGREGATION_NOT_READY);
        }
        MemoryIngestionResult result = submit(snapshot.get());
        recordTrace(TRACE_EVENT_FLUSH_READY, result.status() == MemoryIngestionStatus.ACCEPTED
                        ? MemoryTraceEvent.STATUS_SUCCESS
                        : MemoryTraceEvent.STATUS_FAILED,
                snapshot.get().snapshotId(), traceContext(snapshot.get()), flushDetails(
                        snapshot.get(),
                        Objects.requireNonNullElse(trigger, MemoryFlushTrigger.MANUAL),
                        result));
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
            recordTrace(TRACE_EVENT_SUBMIT, MemoryTraceEvent.STATUS_IGNORED, snapshot.snapshotId(),
                    traceContext(snapshot),
                    details(DETAIL_REASON, REASON_EMPTY_SNAPSHOT));
            return MemoryIngestionResult.ignored(REASON_EMPTY_SNAPSHOT);
        }
        try {
            MemoryWriteRequest writeRequest = MemoryWriteRequest.builder()
                    .conversationId(snapshot.conversationId())
                    .userId(snapshot.userId())
                    .messageId(snapshot.snapshotId())
                    .message(ChatMessage.user(MemoryContextBlockFormatter.format(snapshot)))
                    .build();
            MemoryIngestionResult result = ingestionWorkflowPort.ingest(new MemoryIngestionCommand(
                    operationId(snapshot),
                    snapshot.tenantId(),
                    FLUSH_SOURCE,
                    writeRequest));
            if (result == null) {
                recordTrace(TRACE_EVENT_SUBMIT, MemoryTraceEvent.STATUS_FAILED, snapshot.snapshotId(),
                        traceContext(snapshot), details(
                        DETAIL_OPERATION_ID, operationId(snapshot),
                        DETAIL_ERROR, REASON_NULL_RESULT,
                        DETAIL_MESSAGE, "ingestionWorkflowPort returned null"));
                return MemoryIngestionResult.failed(REASON_AGGREGATION_FLUSH_FAILED);
            }
            recordTrace(TRACE_EVENT_SUBMIT, MemoryTraceEvent.STATUS_SUCCESS, snapshot.snapshotId(),
                    traceContext(snapshot), details(
                    DETAIL_OPERATION_ID, operationId(snapshot),
                    DETAIL_CONVERSATION_ID, snapshot.conversationId(),
                    DETAIL_TURN_COUNT, snapshot.turns().size()));
            return result;
        } catch (Exception ex) {
            LOG.warn("Memory aggregation flush failed: snapshotId={}, userId={}, conversationId={}",
                    snapshot.snapshotId(), snapshot.userId(), snapshot.conversationId(), ex);
            recordTrace(TRACE_EVENT_SUBMIT, MemoryTraceEvent.STATUS_FAILED, snapshot.snapshotId(),
                    traceContext(snapshot), details(
                    DETAIL_OPERATION_ID, operationId(snapshot),
                    DETAIL_ERROR, ex.getClass().getSimpleName(),
                    DETAIL_MESSAGE, Objects.requireNonNullElse(ex.getMessage(), "")));
            return MemoryIngestionResult.failed(REASON_AGGREGATION_FLUSH_FAILED);
        }
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

    private void recordTrace(String eventType,
                             String status,
                             String subjectId,
                             TraceContext context,
                             Map<String, Object> details) {
        TraceContext safeContext = Objects.requireNonNullElseGet(context, TraceContext::empty);
        traceRecorder.record(new MemoryTraceEvent(
                "",
                safeContext.tenantId(),
                safeContext.userId(),
                safeContext.conversationId(),
                safeContext.sessionId(),
                TRACE_COMPONENT,
                eventType,
                status,
                subjectId,
                TRACE_SUBJECT_TYPE_SNAPSHOT,
                details,
                Instant.now(clock)));
        emitFlushMetricIfApplicable(eventType, status, details);
    }

    private void emitFlushMetricIfApplicable(String eventType, String status, Map<String, Object> details) {
        if (!TRACE_EVENT_FLUSH_READY.equals(eventType)) {
            return;
        }
        Object trigger = details == null ? null : details.get(DETAIL_TRIGGER);
        String triggerLabel = trigger == null ? "" : trigger.toString();
        try {
            observationPort.recordEvent(new ObservationEvent(
                    OBSERVATION_FLUSH_EVENT,
                    Instant.now(clock),
                    ObservationEvent.DEFAULT_AMOUNT,
                    Map.of(
                            OBSERVATION_ATTR_TRIGGER, triggerLabel,
                            OBSERVATION_ATTR_STATUS, Objects.requireNonNullElse(status, ""))));
        } catch (RuntimeException ignored) {
            // Observation emission is best-effort and must not change aggregation execution semantics.
        }
    }

    private TraceContext traceContext(MemoryTurnEvent event) {
        if (event == null) {
            return TraceContext.empty();
        }
        return new TraceContext(event.tenantId(), event.userId(), event.conversationId(), event.sessionId());
    }

    private TraceContext traceContext(MemoryBufferState state) {
        if (state == null) {
            return TraceContext.empty();
        }
        return new TraceContext(state.tenantId(), state.userId(), state.conversationId(), state.sessionId());
    }

    private TraceContext traceContext(MemoryBufferSnapshot snapshot) {
        if (snapshot == null) {
            return TraceContext.empty();
        }
        return new TraceContext(snapshot.tenantId(), snapshot.userId(), snapshot.conversationId(),
                snapshot.sessionId());
    }

    private TraceContext traceContext(String tenantId, String sessionId) {
        return new TraceContext(tenantId, "", "", sessionId);
    }

    private record TraceContext(String tenantId, String userId, String conversationId, String sessionId) {

        private TraceContext {
            tenantId = normalizeTraceValue(tenantId, DEFAULT_TENANT_ID);
            userId = normalizeTraceValue(userId, "");
            conversationId = normalizeTraceValue(conversationId, "");
            sessionId = normalizeTraceValue(sessionId, conversationId);
        }

        private static TraceContext empty() {
            return new TraceContext(DEFAULT_TENANT_ID, "", "", "");
        }
    }

    private static String normalizeTraceValue(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }

    private Map<String, Object> flushDetails(MemoryBufferSnapshot snapshot,
                                             MemoryFlushTrigger trigger,
                                             MemoryIngestionResult result) {
        long windowDurationMillis = Math.max(0L, Duration.between(snapshot.from(), snapshot.to()).toMillis());
        return details(
                DETAIL_TRIGGER, trigger.name(),
                DETAIL_RESULT_STATUS, result.status().name(),
                DETAIL_REASON, result.reason(),
                DETAIL_TURN_COUNT, snapshot.turns().size(),
                DETAIL_TOTAL_TOKENS, snapshot.totalTokens(),
                DETAIL_SOURCE_SPAN_COUNT, snapshot.turns().size(),
                DETAIL_SOURCE_USER_MESSAGE_IDS, sourceUserMessageIds(snapshot),
                DETAIL_SOURCE_ASSISTANT_MESSAGE_IDS, sourceAssistantMessageIds(snapshot),
                DETAIL_FROM, snapshot.from().toString(),
                DETAIL_TO, snapshot.to().toString(),
                DETAIL_WINDOW_DURATION_MILLIS, windowDurationMillis);
    }

    private List<String> sourceUserMessageIds(MemoryBufferSnapshot snapshot) {
        return sourceMessageIds(snapshot, MemoryTurnEvent::userMessageId);
    }

    private List<String> sourceAssistantMessageIds(MemoryBufferSnapshot snapshot) {
        return sourceMessageIds(snapshot, MemoryTurnEvent::assistantMessageId);
    }

    private List<String> sourceMessageIds(MemoryBufferSnapshot snapshot,
                                          Function<MemoryTurnEvent, String> messageIdExtractor) {
        return snapshot.turns().stream()
                .map(messageIdExtractor)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Map<String, Object> details(Object... entries) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            details.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return details;
    }
}
