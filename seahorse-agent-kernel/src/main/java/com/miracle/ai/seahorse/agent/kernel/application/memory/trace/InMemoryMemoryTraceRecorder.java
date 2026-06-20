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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class InMemoryMemoryTraceRecorder implements MemoryTraceRecorder {

    private static final int DEFAULT_MAX_EVENTS = 1000;

    private final int maxEvents;
    private final Deque<MemoryTraceEvent> events = new ArrayDeque<>();

    public InMemoryMemoryTraceRecorder() {
        this(DEFAULT_MAX_EVENTS);
    }

    public InMemoryMemoryTraceRecorder(int maxEvents) {
        this.maxEvents = maxEvents <= 0 ? DEFAULT_MAX_EVENTS : maxEvents;
    }

    @Override
    public synchronized void record(MemoryTraceEvent event) {
        MemoryTraceEvent safeEvent = Objects.requireNonNullElseGet(event,
                () -> new MemoryTraceEvent("memory", "event", MemoryTraceEvent.STATUS_IGNORED, "", "default", "",
                        java.util.Map.of(), null));
        if (events.size() >= maxEvents) {
            events.removeFirst();
        }
        events.addLast(safeEvent);
    }

    @Override
    public synchronized List<MemoryTraceEvent> listRecent(int limit) {
        if (events.isEmpty()) {
            return List.of();
        }
        int safeLimit = limit <= 0 ? maxEvents : Math.min(limit, maxEvents);
        ArrayList<MemoryTraceEvent> recent = new ArrayList<>(Math.min(safeLimit, events.size()));
        int skipped = Math.max(0, events.size() - safeLimit);
        int index = 0;
        for (MemoryTraceEvent event : events) {
            if (index++ < skipped) {
                continue;
            }
            recent.add(event);
        }
        java.util.Collections.reverse(recent);
        return List.copyOf(recent);
    }

    @Override
    public synchronized List<MemoryTraceEvent> listByUser(String userId, String tenantId, int limit) {
        return listRecent(limit <= 0 ? maxEvents : limit).stream()
                .filter(e -> e != null
                        && Objects.equals(tenantId, e.tenantId())
                        && Objects.equals(userId, e.userId()))
                .toList();
    }

    public synchronized List<MemoryTraceEvent> recent(int limit) {
        return listRecent(limit);
    }
}
