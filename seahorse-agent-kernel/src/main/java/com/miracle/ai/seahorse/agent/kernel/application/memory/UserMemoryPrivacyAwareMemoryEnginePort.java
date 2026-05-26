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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.UserMemoryPrivacySettingPort;

import java.util.List;
import java.util.Objects;

public class UserMemoryPrivacyAwareMemoryEnginePort implements MemoryEnginePort, MemoryIngestionWorkflowPort {

    private static final String PRIVACY_MODE_REASON = "privacy_mode_enabled";

    private final MemoryEnginePort delegate;
    private final UserMemoryPrivacySettingPort privacySettingPort;

    public UserMemoryPrivacyAwareMemoryEnginePort(MemoryEnginePort delegate,
                                                  UserMemoryPrivacySettingPort privacySettingPort) {
        this.delegate = Objects.requireNonNullElseGet(delegate, MemoryEnginePort::noop);
        this.privacySettingPort = Objects.requireNonNullElseGet(
                privacySettingPort, UserMemoryPrivacySettingPort::defaults);
    }

    @Override
    public MemoryContext loadMemory(MemoryLoadRequest request) {
        if (privacyModeEnabled(request == null ? null : request.userId())) {
            return emptyContext(request);
        }
        return delegate.loadMemory(request);
    }

    @Override
    public void writeMemory(MemoryWriteRequest request) {
        if (privacyModeEnabled(request == null ? null : request.userId())) {
            return;
        }
        delegate.writeMemory(request);
    }

    @Override
    public List<MemoryItem> retrieveMemories(MemoryLoadRequest request) {
        if (privacyModeEnabled(request == null ? null : request.userId())) {
            return List.of();
        }
        return delegate.retrieveMemories(request);
    }

    @Override
    public void executeMemoryDecay() {
        delegate.executeMemoryDecay();
    }

    @Override
    public MemoryQualityReport assessMemoryQuality(String userId) {
        return delegate.assessMemoryQuality(userId);
    }

    @Override
    public MemoryIngestionResult ingest(MemoryIngestionCommand command) {
        MemoryWriteRequest request = command == null ? null : command.writeRequest();
        if (privacyModeEnabled(request == null ? null : request.userId())) {
            return MemoryIngestionResult.ignored(PRIVACY_MODE_REASON);
        }
        if (delegate instanceof MemoryIngestionWorkflowPort workflowPort) {
            return workflowPort.ingest(command);
        }
        delegate.writeMemory(request);
        return MemoryIngestionResult.ignored("delegated_to_memory_engine");
    }

    private boolean privacyModeEnabled(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return privacySettingPort.current(userId).privacyMode();
    }

    private MemoryContext emptyContext(MemoryLoadRequest request) {
        return MemoryContext.builder()
                .conversationId(request == null ? null : request.conversationId())
                .userId(request == null ? null : request.userId())
                .currentQuestion(request == null ? null : request.currentQuestion())
                .workingMemory(List.of())
                .correctionMemories(List.of())
                .profileMemories(List.of())
                .shortTermMemories(List.of())
                .businessDocumentMemories(List.of())
                .longTermMemories(List.of())
                .semanticMemories(List.of())
                .promptMessages(List.of())
                .build();
    }
}
