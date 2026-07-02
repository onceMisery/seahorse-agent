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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionSweepResult;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Objects;

public class SandboxSessionTtlSweepJob {

    static final String LOCK_NAME = "job:sandbox-session-ttl-sweep";

    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxSessionTtlSweepJob.class);
    private static final Duration LOCK_LEASE = Duration.ofMinutes(5);
    private static final String DEFAULT_TENANT_ID = "default";

    private final SandboxRuntimeInboundPort sandboxRuntime;
    private final DistributedLockPort lockPort;
    private final String tenantId;
    private final int limit;

    public SandboxSessionTtlSweepJob(SandboxRuntimeInboundPort sandboxRuntime,
                                     DistributedLockPort lockPort,
                                     String tenantId,
                                     int limit) {
        this.sandboxRuntime = Objects.requireNonNull(sandboxRuntime, "sandboxRuntime must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.tenantId = normalizeTenantId(tenantId);
        this.limit = Math.max(limit, 1);
    }

    @Scheduled(
            fixedDelayString = "${seahorse.agent.sandbox.session-sweep.fixed-delay-ms:60000}",
            initialDelayString = "${seahorse.agent.sandbox.session-sweep.initial-delay-ms:30000}")
    public void sweepExpiredSessions() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            SandboxSessionSweepResult result = sandboxRuntime.sweepExpiredSessions(tenantId, limit);
            if (result.matchedCount() > 0 || result.failedCount() > 0) {
                LOGGER.info(
                        "Sandbox session TTL sweep finished tenantId={}, matched={}, closed={}, failed={}",
                        result.tenantId(),
                        result.matchedCount(),
                        result.closedCount(),
                        result.failedCount());
            }
        } catch (Exception ex) {
            LOGGER.warn("Sandbox session TTL sweep failed tenantId={}, limit={}", tenantId, limit, ex);
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }

    private static String normalizeTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return DEFAULT_TENANT_ID;
        }
        return tenantId.trim();
    }
}
