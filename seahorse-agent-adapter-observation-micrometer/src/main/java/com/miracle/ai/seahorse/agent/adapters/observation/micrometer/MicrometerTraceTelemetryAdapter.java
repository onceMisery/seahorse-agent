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

package com.miracle.ai.seahorse.agent.adapters.observation.micrometer;

import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.TraceTelemetryPort;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors Seahorse's explicit run/node trace tree into Micrometer Tracing.
 */
public class MicrometerTraceTelemetryAdapter implements TraceTelemetryPort {

    private static final String TAG_TRACE_ID = "seahorse.trace.id";
    private static final String TAG_NODE_ID = "seahorse.node.id";
    private static final Set<String> ALLOWED_RUN_ATTRIBUTES = Set.of(
            "seahorse.tenant.id",
            "seahorse.agent.id",
            "seahorse.executor.engine",
            "seahorse.run.id");

    private final Tracer tracer;
    private final Map<String, TraceState> traces = new ConcurrentHashMap<>();

    public MicrometerTraceTelemetryAdapter(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
    }

    @Override
    public void startRun(String traceId, TraceRunStartCommand command, Instant startTime) {
        Objects.requireNonNull(command, "command must not be null");
        Span.Builder builder = tracer.spanBuilder()
                .name("agent.run")
                .tag(TAG_TRACE_ID, traceId)
                .tag("seahorse.trace.name", command.traceName())
                .tag("seahorse.entry.method", command.entryMethod());
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null || currentSpan.isNoop()) {
            builder.setNoParent();
        } else {
            builder.setParent(currentSpan.context());
        }
        tag(builder, "seahorse.conversation.id", command.conversationId());
        tag(builder, "seahorse.task.id", command.taskId());
        command.attributes().forEach((key, value) -> {
            if (ALLOWED_RUN_ATTRIBUTES.contains(key)) {
                tag(builder, key, value);
            }
        });
        applyStartTime(builder, startTime);
        Span span = builder.start();
        traces.put(traceId, new TraceState(span));
    }

    @Override
    public void finishRun(String traceId, String errorMessage, Instant endTime) {
        TraceState state = traces.remove(traceId);
        if (state == null) {
            return;
        }
        state.activeNodes.values().forEach(span -> finish(span, errorMessage, endTime));
        state.activeNodes.clear();
        finish(state.root, errorMessage, endTime);
    }

    @Override
    public void startNode(String traceId, String nodeId, TraceNodeStartCommand command, Instant startTime) {
        TraceState state = traces.get(traceId);
        if (state == null || command == null) {
            return;
        }
        TraceContext parent = state.parentContext(command.parentNodeId());
        Span.Builder builder = tracer.spanBuilder()
                .setParent(parent)
                .name(spanName(command))
                .tag(TAG_TRACE_ID, traceId)
                .tag(TAG_NODE_ID, nodeId)
                .tag("seahorse.node.name", command.nodeName())
                .tag("seahorse.node.type", command.nodeType())
                .tag("code.namespace", command.className())
                .tag("code.function", command.methodName())
                .tag("seahorse.node.depth", command.depth());
        applyStartTime(builder, startTime);
        Span span = builder.start();
        state.contexts.put(nodeId, span.context());
        state.activeNodes.put(nodeId, span);
    }

    @Override
    public void finishNode(String traceId, String nodeId, String errorMessage, Instant endTime) {
        TraceState state = traces.get(traceId);
        if (state == null) {
            return;
        }
        Span span = state.activeNodes.remove(nodeId);
        finish(span, errorMessage, endTime);
    }

    @Override
    public void recordRunAttribute(String traceId, String key, String value) {
        TraceState state = traces.get(traceId);
        if (state != null && ALLOWED_RUN_ATTRIBUTES.contains(key) && value != null && !value.isBlank()) {
            state.root.tag(key, value);
        }
    }

    private String spanName(TraceNodeStartCommand command) {
        return switch (command.nodeType()) {
            case "AGENT_STEP" -> "agent.step";
            case "AGENT_TOOL", "AGENTSCOPE_TOOL_CALL" -> "tool.call";
            case "AGENT_MODEL" -> "model.call";
            case "AGENTSCOPE_TOOL_RESULT" -> "tool.result";
            case "AGENTSCOPE_MODEL_CALL" -> "model.call";
            case "AGENTSCOPE_AGENT" -> "agent.execute";
            default -> command.nodeName();
        };
    }

    private void applyStartTime(Span.Builder builder, Instant startTime) {
        if (startTime != null) {
            builder.startTimestamp(toEpochNanos(startTime), TimeUnit.NANOSECONDS);
        }
    }

    private void finish(Span span, String errorMessage, Instant endTime) {
        if (span == null) {
            return;
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            span.tag("error.message", errorMessage);
            span.error(new IllegalStateException(errorMessage));
        }
        if (endTime == null) {
            span.end();
        } else {
            span.end(toEpochNanos(endTime), TimeUnit.NANOSECONDS);
        }
    }

    private void tag(Span.Builder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.tag(key, safeValue(value));
        }
    }

    private String safeValue(String value) {
        String redacted = CredentialTextRedactor.redact(value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return redacted.length() <= 256 ? redacted : redacted.substring(0, 256);
    }

    private long toEpochNanos(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }

    private static final class TraceState {
        private final Span root;
        private final Map<String, TraceContext> contexts = new ConcurrentHashMap<>();
        private final Map<String, Span> activeNodes = new ConcurrentHashMap<>();

        private TraceState(Span root) {
            this.root = root;
        }

        private TraceContext parentContext(String parentNodeId) {
            return parentNodeId == null ? root.context() : contexts.getOrDefault(parentNodeId, root.context());
        }
    }
}
