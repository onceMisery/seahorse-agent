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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultContextWeaverObservationTests {

    @Test
    void shouldEmitWeaveObservationWithoutTruncationWhenBudgetIsLarge() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        DefaultContextWeaver weaver = new DefaultContextWeaver(MemoryTraceRecorder.noop(), observationPort);

        MemoryContext context = MemoryContext.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion("hi")
                .workingMemory(Collections.emptyList())
                .correctionMemories(Collections.emptyList())
                .profileMemories(List.of(item("p-1", "User prefers concise replies.")))
                .shortTermMemories(Collections.emptyList())
                .businessDocumentMemories(Collections.emptyList())
                .longTermMemories(Collections.emptyList())
                .semanticMemories(List.of(item("s-1", "Kubernetes notes.")))
                .promptMessages(Collections.emptyList())
                .build();

        weaver.weave(context, ContextBudget.defaults());

        assertThat(observationPort.events).hasSize(1);
        ObservationEvent event = observationPort.events.get(0);
        assertThat(event.name()).isEqualTo(DefaultContextWeaver.OBSERVATION_WEAVE_EVENT);
        assertThat(event.attributes())
                .containsEntry(DefaultContextWeaver.OBSERVATION_ATTR_OUTCOME,
                        DefaultContextWeaver.OBSERVATION_OUTCOME_SUCCESS)
                .containsEntry(DefaultContextWeaver.OBSERVATION_ATTR_TRUNCATED, "false");
        assertThat(event.amount()).isEqualTo(ObservationEvent.DEFAULT_AMOUNT);
    }

    @Test
    void shouldEmitWeaveObservationMarkedTruncatedWhenItemBudgetIsExceeded() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        DefaultContextWeaver weaver = new DefaultContextWeaver(MemoryTraceRecorder.noop(), observationPort);

        MemoryContext context = MemoryContext.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion("hi")
                .workingMemory(Collections.emptyList())
                .correctionMemories(Collections.emptyList())
                .profileMemories(List.of(item("p-1", "Profile fact a.")))
                .shortTermMemories(List.of(item("s-1", "Short window note.")))
                .businessDocumentMemories(Collections.emptyList())
                .longTermMemories(List.of(item("l-1", "Long term memory.")))
                .semanticMemories(List.of(item("sem-1", "Semantic memory.")))
                .promptMessages(Collections.emptyList())
                .build();

        weaver.weave(context, new ContextBudget(1, 4_096));

        assertThat(observationPort.events).hasSize(1);
        assertThat(observationPort.events.get(0).attributes())
                .containsEntry(DefaultContextWeaver.OBSERVATION_ATTR_TRUNCATED, "true");
    }

    @Test
    void shouldNotEmitWeaveObservationWhenContextIsEmpty() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        DefaultContextWeaver weaver = new DefaultContextWeaver(MemoryTraceRecorder.noop(), observationPort);

        MemoryContext context = MemoryContext.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion("hi")
                .workingMemory(Collections.emptyList())
                .correctionMemories(Collections.emptyList())
                .profileMemories(Collections.emptyList())
                .shortTermMemories(Collections.emptyList())
                .businessDocumentMemories(Collections.emptyList())
                .longTermMemories(Collections.emptyList())
                .semanticMemories(Collections.emptyList())
                .promptMessages(Collections.emptyList())
                .build();

        weaver.weave(context, ContextBudget.defaults());

        assertThat(observationPort.events).isEmpty();
    }

    private static MemoryItem item(String id, String content) {
        return MemoryItem.builder()
                .id(id)
                .content(content)
                .relevanceScore(0.8D)
                .build();
    }

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    events.add(event);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }
}
