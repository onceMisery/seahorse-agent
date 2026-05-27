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

package com.miracle.ai.seahorse.agent.kernel.domain.stream;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StreamEventEnvelopeTests {

    @Test
    void factoryMethodShouldAssignIncrementingSeq() {
        StreamEventEnvelope e1 = StreamEventEnvelope.of(1, StreamEventType.MESSAGE, "run-1", "hello");
        StreamEventEnvelope e2 = StreamEventEnvelope.of(2, StreamEventType.MESSAGE, "run-1", "world");

        assertEquals(1, e1.eventSeq());
        assertEquals(2, e2.eventSeq());
        assertNotEquals(e1.eventId(), e2.eventId());
    }

    @Test
    void factoryMethodShouldSetFieldsCorrectly() {
        Object payload = Map.of("type", "response", "delta", "hi");
        StreamEventEnvelope envelope = StreamEventEnvelope.of(5, StreamEventType.RUN_STARTED, "run-42", payload);

        assertEquals(5, envelope.eventSeq());
        assertEquals(StreamEventType.RUN_STARTED, envelope.eventType());
        assertEquals("run-42", envelope.runId());
        assertNull(envelope.stepId());
        assertNotNull(envelope.timestamp());
        assertEquals(payload, envelope.typedPayload());
        assertNotNull(envelope.eventId());
        assertFalse(envelope.eventId().isBlank());
    }

    @Test
    void factoryMethodWithStepIdShouldSetStepId() {
        StreamEventEnvelope envelope = StreamEventEnvelope.of(
                3, StreamEventType.STEP_STARTED, "run-1", "step-1", Map.of("title", "Search"));

        assertEquals("step-1", envelope.stepId());
        assertEquals("run-1", envelope.runId());
    }

    @Test
    void shouldRejectNullEventId() {
        assertThrows(NullPointerException.class, () -> new StreamEventEnvelope(
                null, 1, StreamEventType.MESSAGE, "run-1", null, Instant.now(), "payload"));
    }

    @Test
    void shouldRejectNullEventType() {
        assertThrows(NullPointerException.class, () -> new StreamEventEnvelope(
                "id-1", 1, null, "run-1", null, Instant.now(), "payload"));
    }

    @Test
    void shouldRejectNullRunId() {
        assertThrows(NullPointerException.class, () -> new StreamEventEnvelope(
                "id-1", 1, StreamEventType.MESSAGE, null, null, Instant.now(), "payload"));
    }

    @Test
    void shouldAllowNullPayload() {
        StreamEventEnvelope envelope = StreamEventEnvelope.of(1, StreamEventType.DONE, "run-1", null);
        assertNull(envelope.typedPayload());
    }
}
