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

import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryGarbageCollectionService;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Objects;

public class SeahorseMemoryGarbageCollectionJob {

    private static final String LOCK_NAME = "job:memory-garbage-collection";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(10);

    private final MemoryGarbageCollectionService garbageCollectionService;
    private final DistributedLockPort lockPort;

    public SeahorseMemoryGarbageCollectionJob(MemoryGarbageCollectionService garbageCollectionService,
                                              DistributedLockPort lockPort) {
        this.garbageCollectionService = Objects.requireNonNull(
                garbageCollectionService,
                "garbageCollectionService must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
    }

    @Scheduled(cron = "${seahorse-agent.memory.gc.cron:0 10/30 * * * ?}")
    public void runGarbageCollection() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            garbageCollectionService.run("scheduled-gc");
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }
}
