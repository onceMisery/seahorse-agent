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

package com.miracle.ai.seahorse.agent.kernel.application.memory.trace;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMemoryTraceRecorderTests {

    @Test
    void shouldReturnMostRecentEventsFirstAndEvictOldestEntries() {
        InMemoryMemoryTraceRecorder recorder = new InMemoryMemoryTraceRecorder(2);

        recorder.record(event("trace-1", "ingest"));
        recorder.record(event("trace-2", "refine"));
        recorder.record(event("trace-3", "review"));

        List<MemoryTraceEvent> events = recorder.recent(10);

        assertThat(events)
                .extracting(MemoryTraceEvent::traceId)
                .containsExactly("trace-3", "trace-2");
    }

    @Test
    void shouldNormalizeTraceEventDefaultsAndCopyDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("score", 0.9D);

        MemoryTraceEvent event = new MemoryTraceEvent(
                " trace-1 ",
                null,
                " user-1 ",
                " conversation-1 ",
                " session-1 ",
                " refiner ",
                " extracted ",
                " SUCCESS ",
                " memory-1 ",
                " memory ",
                details,
                Instant.EPOCH);
        details.put("score", 0.1D);

        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.tenantId()).isEqualTo("default");
        assertThat(event.userId()).isEqualTo("user-1");
        assertThat(event.conversationId()).isEqualTo("conversation-1");
        assertThat(event.sessionId()).isEqualTo("session-1");
        assertThat(event.component()).isEqualTo("refiner");
        assertThat(event.eventType()).isEqualTo("extracted");
        assertThat(event.status()).isEqualTo("SUCCESS");
        assertThat(event.subjectId()).isEqualTo("memory-1");
        assertThat(event.subjectType()).isEqualTo("memory");
        assertThat(event.details()).containsEntry("score", 0.9D);
    }

    @Test
    void noopRecorderShouldIgnoreEvents() {
        MemoryTraceRecorder recorder = MemoryTraceRecorder.noop();

        recorder.record(event("trace-1", "ingest"));

        assertThat(recorder.recent(10)).isEmpty();
    }

    private MemoryTraceEvent event(String traceId, String eventType) {
        return new MemoryTraceEvent(
                traceId,
                "tenant-1",
                "user-1",
                "conversation-1",
                "session-1",
                "memory-engine",
                eventType,
                MemoryTraceEvent.STATUS_SUCCESS,
                traceId + "-memory",
                "memory",
                Map.of("eventType", eventType),
                Instant.EPOCH);
    }
}
