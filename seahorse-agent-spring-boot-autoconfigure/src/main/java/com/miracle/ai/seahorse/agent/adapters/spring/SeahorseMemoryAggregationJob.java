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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class SeahorseMemoryAggregationJob {

    private static final String LOCK_NAME = "job:memory-aggregation";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(5);
    private static final int DEFAULT_SCAN_LIMIT = 100;

    private final MemoryAggregationServicePort aggregationServicePort;
    private final DistributedLockPort lockPort;
    private final int scanLimit;

    public SeahorseMemoryAggregationJob(MemoryAggregationServicePort aggregationServicePort,
                                        DistributedLockPort lockPort,
                                        int scanLimit) {
        this.aggregationServicePort = Objects.requireNonNull(aggregationServicePort,
                "aggregationServicePort must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.scanLimit = scanLimit <= 0 ? DEFAULT_SCAN_LIMIT : scanLimit;
    }

    @Scheduled(fixedDelayString = "${seahorse.agent.memory.aggregation.scan-delay-ms:5000}")
    public void flushIdleReady() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            aggregationServicePort.flushIdleReady(Instant.now(), scanLimit);
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }
}
