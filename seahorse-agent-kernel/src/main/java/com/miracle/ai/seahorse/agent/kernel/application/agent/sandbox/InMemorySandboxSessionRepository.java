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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存版沙箱会话仓库（从 {@link KernelSandboxRuntimeService} 提取的内部实现）。
 */
final class InMemorySandboxSessionRepository implements SandboxSessionRepositoryPort {

    private static final int DEFAULT_SESSION_LIST_LIMIT = 20;
    private static final int MAX_SESSION_LIST_LIMIT = 100;

    private final Map<String, SandboxSession> store = new ConcurrentHashMap<>();

    @Override
    public SandboxSession saveSession(SandboxSession session) {
        SandboxSession safeSession = Objects.requireNonNull(session, "session must not be null");
        store.put(safeSession.sessionId(), safeSession);
        return safeSession;
    }

    @Override
    public Optional<SandboxSession> findSessionById(String sessionId) {
        if (!hasText(sessionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(sessionId.trim()));
    }

    @Override
    public List<SandboxSession> listSessionsByTenant(String tenantId, int limit) {
        if (!hasText(tenantId)) {
            return List.of();
        }
        String safeTenantId = tenantId.trim();
        int safeLimit = normalizeSessionListLimit(limit);
        return store.values().stream()
                .filter(session -> session.tenantId().equals(safeTenantId))
                .sorted(Comparator.comparing(SandboxSession::updatedAt)
                        .thenComparing(SandboxSession::createdAt)
                        .thenComparing(SandboxSession::sessionId)
                        .reversed())
                .limit(safeLimit)
                .toList();
    }

    @Override
    public List<SandboxSession> listExpiredActiveSessions(String tenantId, Instant now, int limit) {
        if (!hasText(tenantId) || now == null) {
            return List.of();
        }
        String safeTenantId = tenantId.trim();
        int safeLimit = normalizeSessionListLimit(limit);
        return store.values().stream()
                .filter(session -> session.tenantId().equals(safeTenantId))
                .filter(session -> !session.status().isTerminal())
                .filter(session -> !session.expiresAt().isAfter(now))
                .sorted(Comparator.comparing(SandboxSession::expiresAt)
                        .thenComparing(SandboxSession::createdAt)
                        .thenComparing(SandboxSession::sessionId))
                .limit(safeLimit)
                .toList();
    }

    @Override
    public Set<String> listActiveSessionIds() {
        return store.values().stream()
                .filter(session -> !session.status().isTerminal())
                .map(SandboxSession::sessionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static int normalizeSessionListLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SESSION_LIST_LIMIT;
        }
        return Math.min(limit, MAX_SESSION_LIST_LIMIT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
