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

package com.miracle.ai.seahorse.agent.ports.outbound.model;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task A7 契约测试：StreamingChatModelPort 工具调用扩展。
 */
class StreamingChatModelPortToolsTests {

    @Test
    void customAdapterMissingToolsSupportThrowsUnsupported() {
        StreamingChatModelPort adapter = (request, callback) -> {
            callback.onComplete();
            return () -> { };
        };

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> adapter.streamChatWithTools(
                        ChatRequest.builder().build(),
                        new RecordingCallback(),
                        ToolCallCollector.noop()));

        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("工具调用"),
                "异常信息应包含中文提示，实际: " + ex.getMessage());
    }

    @Test
    void noopAdapterCallsCollectorWithEmptyListBeforeComplete() {
        List<String> events = new ArrayList<>();
        AtomicReference<List<AgentToolCall>> seen = new AtomicReference<>();

        StreamCancellationHandle handle = StreamingChatModelPort.noop().streamChatWithTools(
                ChatRequest.builder().build(),
                new RecordingCallback(events),
                calls -> {
                    seen.set(calls);
                    events.add("collector");
                });

        assertDoesNotThrow(handle::cancel);
        assertNotNull(seen.get());
        assertTrue(seen.get().isEmpty());
        assertEquals(List.of("collector", "complete"), events);
    }

    @Test
    void toolCallCollectorNoopDoesNotThrow() {
        assertDoesNotThrow(() -> ToolCallCollector.noop().onToolCalls(List.of()));
    }

    private static final class RecordingCallback implements StreamCallback {

        private final List<String> events;

        private RecordingCallback() {
            this(new ArrayList<>());
        }

        private RecordingCallback(List<String> events) {
            this.events = events;
        }

        @Override
        public void onContent(String content) { }

        @Override
        public void onComplete() {
            events.add("complete");
        }

        @Override
        public void onError(Throwable error) { }
    }
}
