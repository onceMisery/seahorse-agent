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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemoryAggregationBufferPort {

    MemoryBufferState appendTurn(MemoryTurnEvent event);

    Optional<MemoryBufferSnapshot> flushReady(String sessionId,
                                              String tenantId,
                                              MemoryFlushTrigger trigger,
                                              Instant now);

    Optional<MemoryBufferState> state(String sessionId, String tenantId);

    default List<MemoryBufferState> listStates(int limit) {
        return List.of();
    }

    default void discardSnapshot(String snapshotId) {
    }

    static MemoryAggregationBufferPort noop() {
        return new MemoryAggregationBufferPort() {
            @Override
            public MemoryBufferState appendTurn(MemoryTurnEvent event) {
                return new MemoryBufferState(
                        event == null ? "default" : event.tenantId(),
                        event == null ? "" : event.userId(),
                        event == null ? "" : event.conversationId(),
                        event == null ? "" : event.sessionId(),
                        0,
                        0,
                        Instant.now(),
                        false,
                        null);
            }

            @Override
            public Optional<MemoryBufferSnapshot> flushReady(String sessionId,
                                                             String tenantId,
                                                             MemoryFlushTrigger trigger,
                                                             Instant now) {
                return Optional.empty();
            }

            @Override
            public Optional<MemoryBufferState> state(String sessionId, String tenantId) {
                return Optional.empty();
            }
        };
    }
}
