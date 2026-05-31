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

package com.miracle.ai.seahorse.agent.kernel.application.credential;

import com.miracle.ai.seahorse.agent.kernel.domain.credential.SecretMetadata;
import com.miracle.ai.seahorse.agent.ports.inbound.credential.SecretCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.credential.SecretManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWriteCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWritePort;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Secret management application service. It orchestrates authorization and secretRef creation only.
 */
public class KernelSecretManagementService implements SecretManagementInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String SECRET_REF_PREFIX = "secret_";

    private final SecretWritePort secretWritePort;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;
    private final Supplier<String> secretRefSupplier;

    public KernelSecretManagementService(SecretWritePort secretWritePort,
                                         CurrentUserPort currentUserPort,
                                         Clock clock) {
        this(secretWritePort, currentUserPort, clock, KernelSecretManagementService::newSecretRef);
    }

    KernelSecretManagementService(SecretWritePort secretWritePort,
                                  CurrentUserPort currentUserPort,
                                  Clock clock,
                                  Supplier<String> secretRefSupplier) {
        this.secretWritePort = Objects.requireNonNull(secretWritePort, "secretWritePort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.secretRefSupplier = Objects.requireNonNull(secretRefSupplier, "secretRefSupplier must not be null");
    }

    @Override
    public SecretMetadata create(SecretCreateCommand command) {
        currentUserPort.requireRole(ADMIN_ROLE);
        SecretCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        return secretWritePort.putSecret(new SecretWriteCommand(
                requireText(secretRefSupplier.get(), "secretRef must not be blank"),
                safeCommand.tenantId(),
                safeCommand.secretValue(),
                safeCommand.metadataJson(),
                clock.instant()));
    }

    private static String newSecretRef() {
        return SECRET_REF_PREFIX + SnowflakeIds.nextIdString();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
