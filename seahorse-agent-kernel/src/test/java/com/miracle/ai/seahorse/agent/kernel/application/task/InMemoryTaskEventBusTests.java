/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.application.task;

import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTaskEventBusTests {

    @Test
    void publishAssignsMonotonicSeqAndKeepsHistory() {
        InMemoryTaskEventBus bus = new InMemoryTaskEventBus();
        bus.publish("t1", TaskEvent.CREATED, "created", Map.of());
        bus.publish("t1", TaskEvent.STARTED, "started", Map.of());

        List<TaskEvent> history = bus.history("t1");
        assertEquals(2, history.size());
        assertEquals(1L, history.get(0).seq());
        assertEquals(2L, history.get(1).seq());
        assertEquals(TaskEvent.CREATED, history.get(0).type());
    }

    @Test
    void historyIsPerTaskIsolated() {
        InMemoryTaskEventBus bus = new InMemoryTaskEventBus();
        bus.publish("a", TaskEvent.CREATED, "a", Map.of());
        bus.publish("b", TaskEvent.CREATED, "b", Map.of());
        assertEquals(1, bus.history("a").size());
        assertEquals(1, bus.history("b").size());
        assertTrue(bus.history("missing").isEmpty());
    }

    @Test
    void subscriberReceivesSubsequentEventsAndCanUnsubscribe() throws Exception {
        InMemoryTaskEventBus bus = new InMemoryTaskEventBus();
        List<TaskEvent> received = new ArrayList<>();
        AutoCloseable sub = bus.subscribe("t1", received::add);

        bus.publish("t1", TaskEvent.STARTED, "started", Map.of());
        assertEquals(1, received.size());
        assertEquals(TaskEvent.STARTED, received.get(0).type());

        sub.close();
        bus.publish("t1", TaskEvent.COMPLETED, "done", Map.of());
        assertEquals(1, received.size(), "unsubscribed listener must not receive further events");
    }

    @Test
    void terminalEventTypeIsDetectable() {
        TaskEvent ok = new TaskEvent("t1", 1, TaskEvent.COMPLETED, "done", Map.of(), null);
        TaskEvent running = new TaskEvent("t1", 2, TaskEvent.RETRIEVAL_STARTED, "x", Map.of(), null);
        assertTrue(ok.isTerminal());
        assertFalse(running.isTerminal());
    }
}
