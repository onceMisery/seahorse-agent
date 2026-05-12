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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Objects;

public class SeahorseMemoryGovernanceJob {

    private static final String LOCK_NAME = "job:memory-governance";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(30);

    private final MemoryGovernanceInboundPort governancePort;
    private final DistributedLockPort lockPort;

    public SeahorseMemoryGovernanceJob(MemoryGovernanceInboundPort governancePort) {
        this(governancePort, DistributedLockPort.noop());
    }

    public SeahorseMemoryGovernanceJob(MemoryGovernanceInboundPort governancePort,
                                       DistributedLockPort lockPort) {
        this.governancePort = Objects.requireNonNull(governancePort, "governancePort must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
    }

    @Scheduled(cron = "${seahorse-agent.memory.cleanup-cron:0 0/30 * * * ?}")
    public void runDecay() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            governancePort.runDecay("scheduled");
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }
}
