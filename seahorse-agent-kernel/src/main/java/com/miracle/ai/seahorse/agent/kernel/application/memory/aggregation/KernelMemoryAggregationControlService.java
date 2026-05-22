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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class KernelMemoryAggregationControlService implements MemoryAggregationInboundPort {

    private static final String DEFAULT_TENANT_ID = "default";

    private final MemoryAggregationServicePort aggregationServicePort;
    private final Clock clock;

    public KernelMemoryAggregationControlService(MemoryAggregationServicePort aggregationServicePort) {
        this(aggregationServicePort, Clock.systemUTC());
    }

    public KernelMemoryAggregationControlService(MemoryAggregationServicePort aggregationServicePort,
                                                 Clock clock) {
        this.aggregationServicePort = Objects.requireNonNullElseGet(
                aggregationServicePort,
                MemoryAggregationServicePort::noop);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public MemoryIngestionResult flushSessionClosed(String sessionId, String tenantId) {
        return flush(sessionId, tenantId, MemoryFlushTrigger.SESSION_CLOSED);
    }

    @Override
    public MemoryIngestionResult flushManually(String sessionId, String tenantId) {
        return flush(sessionId, tenantId, MemoryFlushTrigger.MANUAL);
    }

    private MemoryIngestionResult flush(String sessionId, String tenantId, MemoryFlushTrigger trigger) {
        return aggregationServicePort.flushReady(
                requireText(sessionId, "sessionId"),
                normalizeTenantId(tenantId),
                trigger,
                Instant.now(clock));
    }

    private String normalizeTenantId(String tenantId) {
        String normalized = Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID).trim();
        return normalized.isBlank() ? DEFAULT_TENANT_ID : normalized;
    }

    private String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
