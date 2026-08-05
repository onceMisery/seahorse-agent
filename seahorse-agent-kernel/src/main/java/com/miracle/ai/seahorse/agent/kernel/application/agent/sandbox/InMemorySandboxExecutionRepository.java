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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版沙箱执行仓库（从 {@link KernelSandboxRuntimeService} 提取的内部实现）。
 */
final class InMemorySandboxExecutionRepository implements SandboxExecutionRepositoryPort {

    private final Map<String, SandboxExecution> store = new ConcurrentHashMap<>();

    @Override
    public SandboxExecution saveExecution(SandboxExecution execution) {
        SandboxExecution safeExecution = Objects.requireNonNull(execution, "execution must not be null");
        store.put(safeExecution.executionId(), safeExecution);
        return safeExecution;
    }

    @Override
    public Optional<SandboxExecution> findExecutionById(String executionId) {
        if (!hasText(executionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(executionId.trim()));
    }

    @Override
    public List<SandboxExecution> listExecutionsBySession(String sessionId) {
        if (!hasText(sessionId)) {
            return List.of();
        }
        String safeSessionId = sessionId.trim();
        List<SandboxExecution> records = new ArrayList<>(store.values().stream()
                .filter(execution -> execution.sessionId().equals(safeSessionId))
                .toList());
        records.sort(Comparator.comparing(SandboxExecution::createdAt)
                .thenComparing(SandboxExecution::executionId));
        return List.copyOf(records);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
