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

import com.miracle.ai.seahorse.agent.kernel.application.memory.trace.InMemoryMemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMemoryTraceQueryServiceTests {

    @Test
    void shouldFilterRecentMemoryTraceEvents() {
        InMemoryMemoryTraceRecorder recorder = new InMemoryMemoryTraceRecorder(10);
        recorder.record(event("trace-1", "memory-review", MemoryTraceEvent.STATUS_SUCCESS, "user-1"));
        recorder.record(event("trace-2", "memory-recall", MemoryTraceEvent.STATUS_FAILED, "user-1"));
        recorder.record(event("trace-3", "memory-review", MemoryTraceEvent.STATUS_FAILED, "user-2"));
        KernelMemoryTraceQueryService service = new KernelMemoryTraceQueryService(recorder);

        var events = service.listRecent(new MemoryTraceQuery(
                10,
                "",
                "",
                "user-1",
                "",
                "",
                "MEMORY-RECALL",
                "failed"));

        assertThat(events).extracting(MemoryTraceEvent::traceId).containsExactly("trace-2");
    }

    @Test
    void shouldLimitAfterFiltering() {
        InMemoryMemoryTraceRecorder recorder = new InMemoryMemoryTraceRecorder(10);
        recorder.record(event("trace-1", "memory-review", MemoryTraceEvent.STATUS_SUCCESS, "user-1"));
        recorder.record(event("trace-2", "memory-review", MemoryTraceEvent.STATUS_SUCCESS, "user-1"));
        KernelMemoryTraceQueryService service = new KernelMemoryTraceQueryService(recorder);

        var events = service.listRecent(new MemoryTraceQuery(
                1,
                "",
                "",
                "user-1",
                "",
                "",
                "memory-review",
                ""));

        assertThat(events).extracting(MemoryTraceEvent::traceId).containsExactly("trace-2");
    }

    private static MemoryTraceEvent event(String traceId, String component, String status, String userId) {
        return new MemoryTraceEvent(
                traceId,
                "tenant-1",
                userId,
                "conv-1",
                "session-1",
                component,
                "test",
                status,
                "subject-1",
                "memory",
                Map.of("source", "test"),
                Instant.EPOCH);
    }
}
