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

package com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryAggregationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationAppendResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelMemoryAggregationControlServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-21T10:00:00Z");

    @Test
    void shouldFlushSessionClosedWithSessionClosedTrigger() {
        RecordingAggregationService delegate = new RecordingAggregationService();
        MemoryAggregationInboundPort service = service(delegate);

        MemoryIngestionResult result = service.flushSessionClosed("session-1", " ");

        assertThat(result.status()).isEqualTo(MemoryIngestionStatus.ACCEPTED);
        assertThat(delegate.sessionId).isEqualTo("session-1");
        assertThat(delegate.tenantId).isEqualTo("default");
        assertThat(delegate.trigger).isEqualTo(MemoryFlushTrigger.SESSION_CLOSED);
        assertThat(delegate.now).isEqualTo(NOW);
    }

    @Test
    void shouldFlushManualWithManualTrigger() {
        RecordingAggregationService delegate = new RecordingAggregationService();
        MemoryAggregationInboundPort service = service(delegate);

        MemoryIngestionResult result = service.flushManually("session-2", "tenant-1");

        assertThat(result.status()).isEqualTo(MemoryIngestionStatus.ACCEPTED);
        assertThat(delegate.sessionId).isEqualTo("session-2");
        assertThat(delegate.tenantId).isEqualTo("tenant-1");
        assertThat(delegate.trigger).isEqualTo(MemoryFlushTrigger.MANUAL);
        assertThat(delegate.now).isEqualTo(NOW);
    }

    @Test
    void shouldRejectBlankSessionIdBeforeDelegating() {
        RecordingAggregationService delegate = new RecordingAggregationService();
        MemoryAggregationInboundPort service = service(delegate);

        assertThatThrownBy(() -> service.flushManually(" ", "tenant-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
        assertThat(delegate.trigger).isNull();
    }

    private MemoryAggregationInboundPort service(RecordingAggregationService delegate) {
        return new KernelMemoryAggregationControlService(
                delegate,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class RecordingAggregationService implements MemoryAggregationServicePort {

        private String sessionId;
        private String tenantId;
        private MemoryFlushTrigger trigger;
        private Instant now;

        @Override
        public MemoryAggregationAppendResult appendTurn(MemoryTurnEvent event) {
            return MemoryAggregationAppendResult.pending(new MemoryBufferState(
                    "default",
                    "",
                    "",
                    "",
                    0,
                    0,
                    NOW,
                    false,
                    null));
        }

        @Override
        public MemoryIngestionResult flushReady(String sessionId,
                                                String tenantId,
                                                MemoryFlushTrigger trigger,
                                                Instant now) {
            this.sessionId = sessionId;
            this.tenantId = tenantId;
            this.trigger = trigger;
            this.now = now;
            return MemoryIngestionResult.accepted(List.of("flushed"));
        }

        @Override
        public Optional<MemoryBufferState> state(String sessionId, String tenantId) {
            return Optional.empty();
        }
    }
}
