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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeNodeRegistryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Objects;

public class SandboxRuntimeNodeCleanupJob {

    static final String LOCK_NAME = "job:sandbox-runtime-node-cleanup";

    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxRuntimeNodeCleanupJob.class);
    private static final Duration LOCK_LEASE = Duration.ofMinutes(5);

    private final SandboxRuntimeNodeRegistryInboundPort nodeRegistry;
    private final DistributedLockPort lockPort;
    private final Duration retention;
    private final int limit;

    public SandboxRuntimeNodeCleanupJob(SandboxRuntimeNodeRegistryInboundPort nodeRegistry,
                                        DistributedLockPort lockPort,
                                        Duration retention,
                                        int limit) {
        this.nodeRegistry = Objects.requireNonNull(nodeRegistry, "nodeRegistry must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.retention = Objects.requireNonNull(retention, "retention must not be null");
        this.limit = limit;
    }

    @Scheduled(
            fixedDelayString = "${seahorse.agent.sandbox.node-cleanup.fixed-delay-ms:3600000}",
            initialDelayString = "${seahorse.agent.sandbox.node-cleanup.initial-delay-ms:300000}")
    public void cleanupStaleRegistrations() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            int removed = nodeRegistry.cleanupStaleRegistrations(retention, limit);
            if (removed > 0) {
                LOGGER.info("Sandbox runtime node cleanup removed stale registrations count={}", removed);
            }
        } catch (Exception ex) {
            LOGGER.warn("Sandbox runtime node cleanup failed", ex);
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }
}
