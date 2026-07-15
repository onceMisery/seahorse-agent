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

package com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeRegistration;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeOwnerIdentity;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeNodeRegistryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeNodeHeartbeatResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeNodeRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class KernelSandboxRuntimeNodeRegistryService implements SandboxRuntimeNodeRegistryInboundPort, AutoCloseable {

    private static final Duration MIN_LEASE_TTL = Duration.ofSeconds(5);
    private static final Duration MAX_LEASE_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 500;

    private final SandboxRuntimePort runtimePort;
    private final SandboxSessionRepositoryPort sessionRepositoryPort;
    private final SandboxRuntimeNodeRegistryPort registryPort;
    private final Clock clock;
    private final String ownerId;
    private final String transportUri;
    private final AtomicReference<SandboxRuntimeNodeRegistration> registeredNode = new AtomicReference<>();
    private boolean closed;

    public KernelSandboxRuntimeNodeRegistryService(SandboxRuntimePort runtimePort,
                                                   SandboxSessionRepositoryPort sessionRepositoryPort,
                                                   SandboxRuntimeNodeRegistryPort registryPort,
                                                   Clock clock) {
        this(runtimePort, sessionRepositoryPort, registryPort, clock, UUID.randomUUID().toString(), "");
    }

    public KernelSandboxRuntimeNodeRegistryService(SandboxRuntimePort runtimePort,
                                                   SandboxSessionRepositoryPort sessionRepositoryPort,
                                                   SandboxRuntimeNodeRegistryPort registryPort,
                                                   String transportUri,
                                                   Clock clock) {
        this(runtimePort, sessionRepositoryPort, registryPort, transportUri, false, clock);
    }

    public KernelSandboxRuntimeNodeRegistryService(SandboxRuntimePort runtimePort,
                                                   SandboxSessionRepositoryPort sessionRepositoryPort,
                                                   SandboxRuntimeNodeRegistryPort registryPort,
                                                   String transportUri,
                                                   boolean allowInsecureHttp,
                                                   Clock clock) {
        this(runtimePort,
                sessionRepositoryPort,
                registryPort,
                transportUri,
                allowInsecureHttp,
                SandboxRuntimeNodeOwnerIdentity.random(),
                clock);
    }

    public KernelSandboxRuntimeNodeRegistryService(SandboxRuntimePort runtimePort,
                                                   SandboxSessionRepositoryPort sessionRepositoryPort,
                                                   SandboxRuntimeNodeRegistryPort registryPort,
                                                   String transportUri,
                                                   boolean allowInsecureHttp,
                                                   SandboxRuntimeNodeOwnerIdentity ownerIdentity,
                                                   Clock clock) {
        this(runtimePort,
                sessionRepositoryPort,
                registryPort,
                clock,
                Objects.requireNonNull(ownerIdentity, "ownerIdentity must not be null").ownerId(),
                normalizeTransportUri(transportUri, allowInsecureHttp));
    }

    KernelSandboxRuntimeNodeRegistryService(SandboxRuntimePort runtimePort,
                                            SandboxSessionRepositoryPort sessionRepositoryPort,
                                            SandboxRuntimeNodeRegistryPort registryPort,
                                            Clock clock,
                                            String ownerId) {
        this(runtimePort, sessionRepositoryPort, registryPort, clock, ownerId, "");
    }

    KernelSandboxRuntimeNodeRegistryService(SandboxRuntimePort runtimePort,
                                            SandboxSessionRepositoryPort sessionRepositoryPort,
                                            SandboxRuntimeNodeRegistryPort registryPort,
                                            Clock clock,
                                            String ownerId,
                                            String transportUri) {
        this.runtimePort = Objects.requireNonNull(runtimePort, "runtimePort must not be null");
        this.sessionRepositoryPort = Objects.requireNonNull(
                sessionRepositoryPort,
                "sessionRepositoryPort must not be null");
        this.registryPort = Objects.requireNonNull(registryPort, "registryPort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.ownerId = requireText(ownerId, "ownerId must not be blank");
        this.transportUri = transportUri == null ? "" : transportUri.trim();
    }

    @Override
    public synchronized SandboxRuntimeNodeHeartbeatResult heartbeat(Duration leaseTtl) {
        if (closed) {
            return SandboxRuntimeNodeHeartbeatResult.closed();
        }
        Duration safeLeaseTtl = normalizeLeaseTtl(leaseTtl);
        SandboxRuntimeHealth health = Objects.requireNonNull(
                runtimePort.inspectHealth(sessionRepositoryPort.listActiveSessionIds()),
                "runtime health result must not be null");
        if (SandboxRuntimeHealth.STATUS_UNSUPPORTED.equals(health.status())) {
            return SandboxRuntimeNodeHeartbeatResult.unsupported();
        }
        Instant now = clock.instant();
        SandboxRuntimeNodeRegistration registration = SandboxRuntimeNodeRegistration.live(
                SandboxRuntimeNodeHealth.fromHealth(health),
                now,
                now.plus(safeLeaseTtl));
        var savedRegistration = registryPort.heartbeat(registration, ownerId, transportUri, safeLeaseTtl);
        if (savedRegistration.isPresent()) {
            registeredNode.set(savedRegistration.get());
            return SandboxRuntimeNodeHeartbeatResult.registered(savedRegistration.get());
        }
        return SandboxRuntimeNodeHeartbeatResult.conflict(registration.nodeId());
    }

    @Override
    public List<SandboxRuntimeNodeRegistration> listRegistrations(int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIST_LIMIT : Math.min(limit, MAX_LIST_LIMIT);
        return registryPort.listRegistrations(safeLimit);
    }

    @Override
    public synchronized void close() {
        closed = true;
        SandboxRuntimeNodeRegistration registration = registeredNode.getAndSet(null);
        if (registration != null) {
            registryPort.release(registration.nodeId(), ownerId);
        }
    }

    private static Duration normalizeLeaseTtl(Duration leaseTtl) {
        Duration safeTtl = Objects.requireNonNullElse(leaseTtl, Duration.ofSeconds(45));
        if (safeTtl.compareTo(MIN_LEASE_TTL) < 0 || safeTtl.compareTo(MAX_LEASE_TTL) > 0) {
            throw new IllegalArgumentException("leaseTtl must be between 5 seconds and 10 minutes");
        }
        return safeTtl;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeTransportUri(String value, boolean allowInsecureHttp) {
        if (value == null || value.isBlank()) {
            return "";
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("sandbox node transport base URL must be a valid URI", ex);
        }
        if ("http".equalsIgnoreCase(uri.getScheme()) && !allowInsecureHttp) {
            throw new IllegalArgumentException(
                    "sandbox node transport requires HTTPS unless allow-insecure-http is explicitly enabled");
        }
        return value.trim();
    }
}
