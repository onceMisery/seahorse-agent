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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class KernelMemoryManagementService implements MemoryManagementInboundPort {

    private static final int DEFAULT_LIMIT = 20;

    private final MemoryManagementServicePorts ports;

    public KernelMemoryManagementService(MemoryManagementServicePorts ports) {
        this.ports = Objects.requireNonNull(ports, "ports must not be null");
    }

    @Override
    public MemoryPage listMemories(String userId, String layer, String conversationId, int limit) {
        MemoryStorePort storePort = store(layer);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        List<MemoryRecord> records = hasText(conversationId)
                ? storePort.listByConversation(conversationId.trim(), safeLimit)
                : storePort.listByUser(requireText(userId, "userId"), safeLimit);
        return new MemoryPage(normalizeLayer(layer), records);
    }

    @Override
    public Optional<MemoryRecord> findMemory(String layer, String memoryId) {
        return store(layer).findById(requireText(memoryId, "memoryId"));
    }

    @Override
    public boolean deleteMemory(String layer, String memoryId) {
        return store(layer).deleteById(requireText(memoryId, "memoryId"));
    }

    @Override
    public List<MemoryQualitySnapshot> listQualitySnapshots(String userId, int limit) {
        return ports.qualitySnapshotRepositoryPort()
                .listByUser(requireText(userId, "userId"), limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public List<MemoryConflictRecord> listConflicts(String userId, String status, int limit) {
        return ports.conflictLogRepositoryPort()
                .listByUser(requireText(userId, "userId"), status, limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public boolean resolveConflict(String conflictId, String action, String resolvedBy) {
        return ports.conflictLogRepositoryPort()
                .resolve(requireText(conflictId, "conflictId"), requireText(action, "action"),
                        Objects.requireNonNullElse(resolvedBy, "").trim());
    }

    @Override
    public List<ProfileFact> listProfileFacts(String userId, String tenantId, int limit) {
        return ports.profileMemoryPort()
                .listActive(requireText(userId, "userId"), tenantId, limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public List<CorrectionRule> listCorrectionRules(String userId, String tenantId, int limit) {
        return ports.correctionLedgerPort()
                .listActive(requireText(userId, "userId"), tenantId, limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public List<MemoryOperationRecord> listOperations(String userId, String tenantId, String status, int limit) {
        return ports.operationLogPort()
                .listByUser(requireText(userId, "userId"), tenantId, status, limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public List<MemoryOutboxPort.MemoryOutboxTask> listOutboxTasks(int limit) {
        return ports.outboxPort().pollPending(limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    private MemoryStorePort store(String layer) {
        return switch (normalizeLayer(layer)) {
            case "working" -> ports.workingMemoryPort();
            case "short_term" -> ports.shortTermMemoryPort();
            case "long_term" -> ports.longTermMemoryPort();
            case "semantic" -> ports.semanticMemoryPort();
            default -> throw new IllegalArgumentException("unsupported memory layer: " + layer);
        };
    }

    private String normalizeLayer(String layer) {
        return requireText(layer, "layer")
                .toLowerCase(Locale.ROOT)
                .replace("-", "_");
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
