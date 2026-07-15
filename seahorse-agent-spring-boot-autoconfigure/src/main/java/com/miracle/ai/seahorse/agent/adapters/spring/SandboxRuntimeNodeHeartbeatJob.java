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
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeNodeHeartbeatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Objects;

public class SandboxRuntimeNodeHeartbeatJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxRuntimeNodeHeartbeatJob.class);

    private final SandboxRuntimeNodeRegistryInboundPort nodeRegistry;
    private final Duration leaseTtl;

    public SandboxRuntimeNodeHeartbeatJob(SandboxRuntimeNodeRegistryInboundPort nodeRegistry,
                                          Duration leaseTtl,
                                          Duration heartbeatInterval) {
        this.nodeRegistry = Objects.requireNonNull(nodeRegistry, "nodeRegistry must not be null");
        this.leaseTtl = Objects.requireNonNull(leaseTtl, "leaseTtl must not be null");
        Duration safeInterval = Objects.requireNonNull(heartbeatInterval, "heartbeatInterval must not be null");
        if (safeInterval.isNegative() || safeInterval.isZero()
                || leaseTtl.compareTo(safeInterval.multipliedBy(2L)) < 0) {
            throw new IllegalArgumentException("leaseTtl must be at least twice the heartbeat interval");
        }
    }

    @Scheduled(
            fixedDelayString = "${seahorse.agent.sandbox.node-heartbeat.fixed-delay-ms:15000}",
            initialDelayString = "${seahorse.agent.sandbox.node-heartbeat.initial-delay-ms:1000}")
    public void heartbeat() {
        try {
            SandboxRuntimeNodeHeartbeatResult result = nodeRegistry.heartbeat(leaseTtl);
            if (SandboxRuntimeNodeHeartbeatResult.STATUS_CONFLICT.equals(result.status())) {
                LOGGER.warn("Sandbox runtime node heartbeat rejected because node id is owned by a live instance, nodeId={}",
                        result.nodeId());
            } else if (SandboxRuntimeNodeHeartbeatResult.STATUS_UNSUPPORTED.equals(result.status())) {
                LOGGER.debug("Sandbox runtime node heartbeat skipped because runtime is unsupported");
            }
        } catch (Exception ex) {
            LOGGER.warn("Sandbox runtime node heartbeat failed", ex);
        }
    }
}
