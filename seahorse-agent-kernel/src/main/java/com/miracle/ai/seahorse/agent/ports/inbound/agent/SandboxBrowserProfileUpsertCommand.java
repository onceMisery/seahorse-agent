/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxBrowserProfileStatus;

import java.time.Instant;

public record SandboxBrowserProfileUpsertCommand(String profileId,
                                                 String tenantId,
                                                 String name,
                                                 String sessionStateArtifactId,
                                                 SandboxBrowserProfileStatus status,
                                                 Instant expiresAt) {}
