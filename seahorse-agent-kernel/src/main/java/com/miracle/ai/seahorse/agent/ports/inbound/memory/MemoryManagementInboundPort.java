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

package com.miracle.ai.seahorse.agent.ports.inbound.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryHealthReport;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReadinessReport;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;

import java.util.List;
import java.util.Optional;

public interface MemoryManagementInboundPort {

    MemoryPage listMemories(String userId, String layer, String conversationId, int limit);

    Optional<MemoryRecord> findMemory(String layer, String memoryId);

    boolean deleteMemory(String layer, String memoryId);

    List<MemoryQualitySnapshot> listQualitySnapshots(String userId, int limit);

    List<MemoryConflictRecord> listConflicts(String userId, String status, int limit);

    boolean resolveConflict(String conflictId, String action, String resolvedBy);

    default boolean resolveConflict(MemoryConflictResolutionCommand command) {
        MemoryConflictResolutionCommand safeCommand = command == null
                ? MemoryConflictResolutionCommand.manual("", "", "")
                : command;
        return resolveConflict(safeCommand.conflictId(), safeCommand.action(), safeCommand.resolvedBy());
    }

    default List<ProfileFact> listProfileFacts(String userId, String tenantId, int limit) {
        return List.of();
    }

    default boolean disableProfileFact(String userId, String tenantId, String slotKey, String operator) {
        return false;
    }

    default List<CorrectionRule> listCorrectionRules(String userId, String tenantId, int limit) {
        return List.of();
    }

    default List<MemoryOperationRecord> listOperations(String userId, String tenantId, String status, int limit) {
        return List.of();
    }

    default List<MemoryOutboxPort.MemoryOutboxTask> listOutboxTasks(int limit) {
        return List.of();
    }

    default MemoryHealthReport memoryHealth(String userId, String tenantId) {
        return new MemoryHealthReport(userId, tenantId, 0, 0, 0, 0, 0,
                java.util.Map.of(), 0D, 0D, 0, 0, 0D, 0D, java.util.Map.of(), List.of(),
                java.time.Instant.now());
    }

    default MemoryReadinessReport memoryReadiness(String userId, String tenantId) {
        return new MemoryReadinessReport(
                userId,
                tenantId,
                MemoryReadinessReport.STATUS_NO_EVIDENCE,
                List.of(),
                List.of("memory readiness service is not available"),
                java.time.Instant.now());
    }

    default MemoryPolicyConfig memoryPolicyConfig() {
        return MemoryPolicyConfig.defaults();
    }

    default MemoryPolicyConfig updatePolicyConfig(MemoryPolicyConfig config) {
        return config == null ? MemoryPolicyConfig.defaults() : config;
    }
}
