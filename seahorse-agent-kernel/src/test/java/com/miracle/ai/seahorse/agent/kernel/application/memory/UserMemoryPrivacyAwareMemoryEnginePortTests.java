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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMemoryPrivacyAwareMemoryEnginePortTests {

    @Test
    void shouldBlockLongTermReadAndWriteWhenPrivacyModeIsEnabled() {
        RecordingMemoryEnginePort delegate = new RecordingMemoryEnginePort();
        InMemoryUserMemoryPrivacySettingPort privacyPort = new InMemoryUserMemoryPrivacySettingPort();
        privacyPort.setPrivacyMode("user-1", true);
        UserMemoryPrivacyAwareMemoryEnginePort port =
                new UserMemoryPrivacyAwareMemoryEnginePort(delegate, privacyPort);

        MemoryContext context = port.loadMemory(loadRequest("user-1"));
        List<MemoryItem> memories = port.retrieveMemories(loadRequest("user-1"));
        port.writeMemory(writeRequest("user-1"));

        assertTrue(context.getLongTermMemories().isEmpty());
        assertTrue(memories.isEmpty());
        assertEquals(0, delegate.loadCount);
        assertEquals(0, delegate.writeCount);
        assertEquals(0, delegate.retrieveCount);
    }

    @Test
    void shouldDelegateWhenPrivacyModeIsDisabled() {
        RecordingMemoryEnginePort delegate = new RecordingMemoryEnginePort();
        UserMemoryPrivacyAwareMemoryEnginePort port = new UserMemoryPrivacyAwareMemoryEnginePort(
                delegate, new InMemoryUserMemoryPrivacySettingPort());

        MemoryContext context = port.loadMemory(loadRequest("user-1"));
        List<MemoryItem> memories = port.retrieveMemories(loadRequest("user-1"));
        port.writeMemory(writeRequest("user-1"));

        assertEquals(1, context.getLongTermMemories().size());
        assertEquals(1, memories.size());
        assertEquals(1, delegate.loadCount);
        assertEquals(1, delegate.writeCount);
        assertEquals(1, delegate.retrieveCount);
    }

    private static MemoryLoadRequest loadRequest(String userId) {
        return MemoryLoadRequest.builder()
                .conversationId("conversation-1")
                .userId(userId)
                .currentQuestion("question")
                .build();
    }

    private static MemoryWriteRequest writeRequest(String userId) {
        return MemoryWriteRequest.builder()
                .conversationId("conversation-1")
                .userId(userId)
                .messageId("message-1")
                .message(ChatMessage.user("remember that I prefer concise answers"))
                .build();
    }

    private static MemoryItem memoryItem(String userId) {
        return MemoryItem.builder()
                .id("memory-1")
                .userId(userId)
                .layer(MemoryLayer.LONG_TERM)
                .type("PREFERENCE")
                .content("Likes concise answers")
                .createTime(LocalDateTime.parse("2026-05-26T00:00:00"))
                .build();
    }

    private static final class RecordingMemoryEnginePort implements MemoryEnginePort {

        private int loadCount;
        private int writeCount;
        private int retrieveCount;

        @Override
        public MemoryContext loadMemory(MemoryLoadRequest request) {
            loadCount++;
            return MemoryContext.builder()
                    .conversationId(request.conversationId())
                    .userId(request.userId())
                    .currentQuestion(request.currentQuestion())
                    .longTermMemories(List.of(memoryItem(request.userId())))
                    .build();
        }

        @Override
        public void writeMemory(MemoryWriteRequest request) {
            writeCount++;
        }

        @Override
        public List<MemoryItem> retrieveMemories(MemoryLoadRequest request) {
            retrieveCount++;
            return List.of(memoryItem(request.userId()));
        }

        @Override
        public void executeMemoryDecay() {
        }

        @Override
        public MemoryQualityReport assessMemoryQuality(String userId) {
            return MemoryQualityReport.builder().userId(userId).longTermCount(1).build();
        }
    }
}
