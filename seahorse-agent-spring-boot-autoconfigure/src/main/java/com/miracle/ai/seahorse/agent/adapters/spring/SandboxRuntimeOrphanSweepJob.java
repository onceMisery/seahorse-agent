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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Objects;

public class SandboxRuntimeOrphanSweepJob {

    static final String LOCK_NAME = "job:sandbox-runtime-orphan-sweep";

    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxRuntimeOrphanSweepJob.class);
    private static final Duration LOCK_LEASE = Duration.ofMinutes(5);

    private final SandboxRuntimeInboundPort sandboxRuntime;
    private final DistributedLockPort lockPort;

    public SandboxRuntimeOrphanSweepJob(SandboxRuntimeInboundPort sandboxRuntime,
                                        DistributedLockPort lockPort) {
        this.sandboxRuntime = Objects.requireNonNull(sandboxRuntime, "sandboxRuntime must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
    }

    @Scheduled(
            fixedDelayString = "${seahorse.agent.sandbox.runtime-sweep.fixed-delay-ms:300000}",
            initialDelayString = "${seahorse.agent.sandbox.runtime-sweep.initial-delay-ms:60000}")
    public void sweepOrphanedRuntimeResources() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            SandboxRuntimeCleanupResult result = sandboxRuntime.sweepOrphanedRuntimeResources();
            if (result.removedWorkspaceCount() > 0 || result.failedWorkspaceCount() > 0) {
                LOGGER.info(
                        "Sandbox runtime orphan sweep finished activeSessions={}, inspectedWorkspaces={}, removed={}, failed={}",
                        result.activeSessionCount(),
                        result.inspectedWorkspaceCount(),
                        result.removedWorkspaceCount(),
                        result.failedWorkspaceCount());
            }
        } catch (Exception ex) {
            LOGGER.warn("Sandbox runtime orphan sweep failed", ex);
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }
}
