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

package com.miracle.ai.seahorse.agent.kernel.application.trace;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Seahorse 内核显式 Trace 记录器。
 */
public class KernelRagTraceRecorder {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static final Logger LOG = LoggerFactory.getLogger(KernelRagTraceRecorder.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final RagTraceRepositoryPort repositoryPort;
    private final boolean enabled;
    private final RagTraceRecorderOptions options;

    public KernelRagTraceRecorder(RagTraceRepositoryPort repositoryPort) {
        this(repositoryPort, RagTraceRecorderOptions.always());
    }

    public KernelRagTraceRecorder(RagTraceRepositoryPort repositoryPort, RagTraceRecorderOptions options) {
        this(Objects.requireNonNull(repositoryPort, "repositoryPort must not be null"), true, options);
    }

    private KernelRagTraceRecorder(RagTraceRepositoryPort repositoryPort, boolean enabled) {
        this(repositoryPort, enabled, RagTraceRecorderOptions.always());
    }

    private KernelRagTraceRecorder(RagTraceRepositoryPort repositoryPort,
                                   boolean enabled,
                                   RagTraceRecorderOptions options) {
        this.repositoryPort = repositoryPort;
        this.enabled = enabled;
        this.options = Objects.requireNonNullElseGet(options, RagTraceRecorderOptions::always);
    }

    public static KernelRagTraceRecorder noop() {
        return new KernelRagTraceRecorder(null, false);
    }

    public TraceRunScope startRun(TraceRunStartCommand command) {
        if (!enabled || command == null || !sampled()) {
            return TraceRunScope.disabled();
        }
        Instant startTime = Instant.now();
        String traceId = newTraceId();
        RagTraceRun run = new RagTraceRun();
        run.setTraceId(traceId);
        run.setTraceName(command.traceName());
        run.setEntryMethod(command.entryMethod());
        run.setConversationId(command.conversationId());
        run.setTaskId(command.taskId());
        run.setUserId(command.userId());
        run.setStatus(STATUS_RUNNING);
        run.setStartTime(startTime);
        try {
            repositoryPort.startRun(run);
            return TraceRunScope.active(traceId, startTime);
        } catch (RuntimeException ex) {
            LOG.warn("RAG Trace run 启动失败，按无 Trace 降级，traceName={}", command.traceName(), ex);
            return TraceRunScope.disabled();
        }
    }

    public void finishRun(TraceRunScope scope) {
        finishRun(scope, null);
    }

    public void finishRun(TraceRunScope scope, Throwable error) {
        if (!enabled || scope == null || !scope.active()) {
            return;
        }
        Instant endTime = Instant.now();
        RagTraceRunFinish finish = new RagTraceRunFinish(
                scope.traceId(),
                error == null ? STATUS_SUCCESS : STATUS_FAILED,
                sanitizeError(error),
                endTime,
                durationMs(scope.startTime(), endTime));
        try {
            repositoryPort.finishRun(finish);
        } catch (RuntimeException ex) {
            LOG.warn("RAG Trace run 结束记录失败，traceId={}", scope.traceId(), ex);
        }
    }

    public TraceNodeScope startNode(TraceRunScope runScope, TraceNodeStartCommand command) {
        if (!enabled || runScope == null || !runScope.active() || command == null) {
            return TraceNodeScope.disabled();
        }
        Instant startTime = Instant.now();
        String nodeId = newTraceId();
        RagTraceNode node = new RagTraceNode();
        node.setTraceId(runScope.traceId());
        node.setNodeId(nodeId);
        node.setParentNodeId(command.parentNodeId());
        node.setDepth(command.depth());
        node.setNodeType(command.nodeType());
        node.setNodeName(command.nodeName());
        node.setClassName(command.className());
        node.setMethodName(command.methodName());
        node.setStatus(STATUS_RUNNING);
        node.setStartTime(startTime);
        node.setExtraData(command.extraData());
        try {
            repositoryPort.startNode(node);
            return TraceNodeScope.active(runScope.traceId(), nodeId, startTime);
        } catch (RuntimeException ex) {
            LOG.warn("RAG Trace node 启动失败，按无节点 Trace 降级，traceId={}，nodeName={}",
                    runScope.traceId(), command.nodeName(), ex);
            return TraceNodeScope.disabled();
        }
    }

    public void finishNode(TraceNodeScope scope) {
        finishNode(scope, null);
    }

    public void finishNode(TraceNodeScope scope, Throwable error) {
        finishNode(scope, error, null);
    }

    public void finishNode(TraceNodeScope scope, Throwable error, String extraData) {
        if (!enabled || scope == null || !scope.active()) {
            return;
        }
        Instant endTime = Instant.now();
        RagTraceNodeFinish finish = new RagTraceNodeFinish(
                scope.traceId(),
                scope.nodeId(),
                error == null ? STATUS_SUCCESS : STATUS_FAILED,
                sanitizeError(error),
                endTime,
                durationMs(scope.startTime(), endTime),
                extraData);
        try {
            repositoryPort.finishNode(finish);
        } catch (RuntimeException ex) {
            LOG.warn("RAG Trace node 结束记录失败，traceId={}，nodeId={}", scope.traceId(), scope.nodeId(), ex);
        }
    }

    public void recordNode(TraceRunScope runScope, TraceNodeStartCommand command, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        TraceNodeScope nodeScope = startNode(runScope, command);
        try {
            action.run();
            finishNode(nodeScope);
        } catch (RuntimeException ex) {
            finishNode(nodeScope, ex);
            throw ex;
        }
    }

    public <T> T recordNode(TraceRunScope runScope, TraceNodeStartCommand command, Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        TraceNodeScope nodeScope = startNode(runScope, command);
        try {
            T result = action.get();
            finishNode(nodeScope);
            return result;
        } catch (RuntimeException ex) {
            finishNode(nodeScope, ex);
            throw ex;
        }
    }

    private long durationMs(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            return 0L;
        }
        return Math.max(Duration.between(startTime, endTime).toMillis(), 0L);
    }

    private String sanitizeError(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getClass().getSimpleName() + ": "
                + Objects.requireNonNullElse(throwable.getMessage(), "");
        String normalized = message.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (normalized.length() <= MAX_ERROR_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_LENGTH);
    }

    private String newTraceId() {
        return SnowflakeIds.nextIdString();
    }

    private boolean sampled() {
        double sampleRate = options.sampleRate();
        if (sampleRate >= 1D) {
            return true;
        }
        if (sampleRate <= 0D) {
            return false;
        }
        // 采样只在 run 入口判定一次，后续 node 通过 TraceRunScope 继承该结果。
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }
}
