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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Objects;

public class SeahorseMemoryMaintenanceJob {

    private static final String LOCK_NAME = "job:memory-maintenance";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(30);
    private static final String SCHEDULED_REASON = "scheduled-maintenance";

    private final MemoryMaintenanceInboundPort maintenancePort;
    private final DistributedLockPort lockPort;
    private final boolean compactionEnabled;
    private final boolean aliasEnabled;
    private final boolean garbageCollectionEnabled;

    public SeahorseMemoryMaintenanceJob(MemoryMaintenanceInboundPort maintenancePort,
                                        DistributedLockPort lockPort,
                                        boolean compactionEnabled,
                                        boolean aliasEnabled,
                                        boolean garbageCollectionEnabled) {
        this.maintenancePort = Objects.requireNonNull(maintenancePort, "maintenancePort must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.compactionEnabled = compactionEnabled;
        this.aliasEnabled = aliasEnabled;
        this.garbageCollectionEnabled = garbageCollectionEnabled;
    }

    @Scheduled(cron = "${seahorse.agent.memory.maintenance.cron:0 30 3 * * ?}")
    public void runMaintenance() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            maintenancePort.runMaintenance(new MemoryMaintenanceRunCommand(
                    SCHEDULED_REASON,
                    compactionEnabled,
                    aliasEnabled,
                    garbageCollectionEnabled));
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }
}
