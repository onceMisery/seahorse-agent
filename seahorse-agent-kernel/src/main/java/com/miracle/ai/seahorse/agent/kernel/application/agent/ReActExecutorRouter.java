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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Routes ReAct execution to the executor selected by the resolved run context.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ReActExecutorRouter implements ReActExecutorPort {

    private final Map<String, ReActExecutorPort> executorsByEngine;
    private final String defaultEngine;

    public ReActExecutorRouter(List<? extends ReActExecutorPort> executors, String defaultEngine) {
        this(indexExecutors(executors), normalizeEngine(defaultEngine, "kernel"));
    }

    @Override
    public AgentLoopResult execute(AgentLoopRequest request) {
        return executorFor(request).execute(request);
    }

    @Override
    public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
        return executorFor(request).streamExecute(request, callback);
    }

    @Override
    public StreamCancellationHandle streamExecute(
            AgentLoopRequest request,
            StreamCallback callback,
            TraceRunScope traceRunScope) {
        return executorFor(request).streamExecute(request, callback, traceRunScope);
    }

    @Override
    public String engineId() {
        return defaultEngine;
    }

    public boolean supports(String executorEngine) {
        return executorsByEngine.containsKey(normalizeEngine(executorEngine, defaultEngine));
    }

    private ReActExecutorPort executorFor(AgentLoopRequest request) {
        String requestedEngine = request == null ? null : request.executorEngine();
        String engine = normalizeEngine(requestedEngine, defaultEngine);
        ReActExecutorPort executor = executorsByEngine.get(engine);
        if (executor == null) {
            throw new IllegalStateException("No ReActExecutorPort configured for engine: " + engine);
        }
        return executor;
    }

    private static Map<String, ReActExecutorPort> indexExecutors(List<? extends ReActExecutorPort> executors) {
        Map<String, ReActExecutorPort> indexed = new LinkedHashMap<>();
        List<? extends ReActExecutorPort> safeExecutors = executors == null ? List.of() : executors;
        for (ReActExecutorPort executor : safeExecutors) {
            if (executor == null || executor instanceof ReActExecutorRouter) {
                continue;
            }
            String engine = normalizeEngine(executor.engineId(), null);
            if (engine != null) {
                indexed.putIfAbsent(engine, executor);
            }
        }
        if (indexed.isEmpty()) {
            throw new IllegalArgumentException("At least one ReActExecutorPort delegate is required");
        }
        return Map.copyOf(indexed);
    }

    private static String normalizeEngine(String executorEngine, String fallback) {
        String normalized = executorEngine == null || executorEngine.isBlank()
                ? fallback
                : executorEngine.trim();
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return Objects.requireNonNull(normalized).toLowerCase();
    }
}
