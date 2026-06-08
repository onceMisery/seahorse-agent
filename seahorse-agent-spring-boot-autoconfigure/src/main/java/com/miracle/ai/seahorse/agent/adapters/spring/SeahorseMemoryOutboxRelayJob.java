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

import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryOutboxRelayService;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Objects;

public class SeahorseMemoryOutboxRelayJob {

    private static final String LOCK_NAME = "job:memory-outbox-relay";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(5);

    private final MemoryOutboxRelayService relayService;
    private final DistributedLockPort lockPort;
    private final int batchSize;

    public SeahorseMemoryOutboxRelayJob(MemoryOutboxRelayService relayService,
                                        DistributedLockPort lockPort,
                                        int batchSize) {
        this.relayService = Objects.requireNonNull(relayService, "relayService must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.batchSize = batchSize <= 0 ? 50 : batchSize;
    }

    @Scheduled(fixedDelayString = "${seahorse-agent.memory.outbox.relay-delay-ms:5000}")
    public void relay() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            relayService.processBatch(batchSize);
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }
}
