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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchSseBridgeThrottlingTests {

    @Test
    void shouldMergeContentEventsWithinThrottleWindowAndFlushBeforeLifecycleEvents() throws Exception {
        ResearchSseBridge.ThrottledEventSender sender = new ResearchSseBridge.ThrottledEventSender(50L);
        List<StreamEventEnvelope> sent = new ArrayList<>();

        sender.accept(event(1, StreamEventType.ARTIFACT_START, Map.of("artifactId", "artifact-1")),
                1_000L, sent::add);
        sender.accept(event(2, StreamEventType.ARTIFACT_CONTENT, Map.of(
                "artifactId", "artifact-1",
                "delta", "hello ")), 1_001L, sent::add);
        sender.accept(event(3, StreamEventType.ARTIFACT_CONTENT, Map.of(
                "artifactId", "artifact-1",
                "delta", "world")), 1_002L, sent::add);
        sender.flushDue(1_002L, sent::add);
        sender.flushDue(1_052L, sent::add);
        sender.accept(event(4, StreamEventType.ARTIFACT_CONTENT, Map.of(
                "artifactId", "artifact-1",
                "delta", "!")), 1_060L, sent::add);
        sender.flushDue(1_060L, sent::add);
        sender.accept(event(5, StreamEventType.ARTIFACT_END, Map.of(
                "artifactId", "artifact-1",
                "totalChars", 12)), 1_061L, sent::add);

        assertThat(sent).extracting(StreamEventEnvelope::eventType).containsExactly(
                StreamEventType.ARTIFACT_START,
                StreamEventType.ARTIFACT_CONTENT,
                StreamEventType.ARTIFACT_CONTENT,
                StreamEventType.ARTIFACT_END);
        assertThat(sent.get(1).eventSeq()).isEqualTo(3L);
        assertThat(sent.get(1).typedPayload()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) sent.get(1).typedPayload()).get("delta")).isEqualTo("hello world");
        assertThat(sent.get(2).eventSeq()).isEqualTo(4L);
        assertThat(((Map<?, ?>) sent.get(2).typedPayload()).get("delta")).isEqualTo("!");
    }

    private static StreamEventEnvelope event(long seq, StreamEventType type, Object payload) {
        return new StreamEventEnvelope(
                "event-" + seq,
                seq,
                type,
                "run-1",
                null,
                Instant.parse("2026-05-29T00:00:00Z").plusMillis(seq),
                payload);
    }
}
