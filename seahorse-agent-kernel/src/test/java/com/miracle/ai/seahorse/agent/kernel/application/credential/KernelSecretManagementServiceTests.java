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
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWriteCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWritePort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelSecretManagementServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String GENERATED_SECRET_REF = "secret_test_1";

    @Test
    void shouldCreateSecretRefBehindAdminBoundary() {
        RecordingSecretWritePort writePort = new RecordingSecretWritePort();
        SecretManagementInboundPort service = new KernelSecretManagementService(
                writePort,
                adminUser(),
                FIXED_CLOCK,
                fixedRef(GENERATED_SECRET_REF));

        SecretMetadata metadata = service.create(new SecretCreateCommand(
                "tenant-1",
                SecretValue.of("super-secret-token"),
                "{\"purpose\":\"mcp\"}"));

        assertEquals(GENERATED_SECRET_REF, metadata.secretRef());
        assertEquals("tenant-1", metadata.tenantId());
        assertEquals("{\"purpose\":\"mcp\"}", metadata.metadataJson());
        assertEquals(NOW, metadata.createdAt());
        assertNull(metadata.rotatedAt());
        assertEquals(GENERATED_SECRET_REF, writePort.lastCommand.secretRef());
        assertEquals("super-secret-token", writePort.lastCommand.secretValue().reveal());
        assertEquals(NOW, writePort.lastCommand.createdAt());
    }

    @Test
    void shouldRejectNonAdminSecretCreation() {
        SecretManagementInboundPort service = new KernelSecretManagementService(
                new RecordingSecretWritePort(),
                normalUser(),
                FIXED_CLOCK,
                fixedRef(GENERATED_SECRET_REF));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.create(new SecretCreateCommand(
                "tenant-1",
                SecretValue.of("super-secret-token"),
                "{}")));

        assertEquals("权限不足", error.getMessage());
    }

    @Test
    void shouldRejectSecretCreateMetadataContainingPlaintextSecret() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new SecretCreateCommand(
                "tenant-1",
                SecretValue.of("super-secret-token"),
                "{\"note\":\"super-secret-token\"}"));

        assertEquals("metadataJson must not contain secret plaintext", error.getMessage());
    }

    @Test
    void shouldRejectSecretWriteMetadataContainingPlaintextSecret() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new SecretWriteCommand(
                GENERATED_SECRET_REF,
                "tenant-1",
                SecretValue.of("super-secret-token"),
                "{\"note\":\"super-secret-token\"}",
                NOW));

        assertEquals("metadataJson must not contain secret plaintext", error.getMessage());
    }

    private static CurrentUserPort adminUser() {
        return () -> Optional.of(new CurrentUser(1L, "root", "admin", null));
    }

    private static CurrentUserPort normalUser() {
        return () -> Optional.of(new CurrentUser(2L, "alice", "user", null));
    }

    private static Supplier<String> fixedRef(String secretRef) {
        return () -> secretRef;
    }

    private static final class RecordingSecretWritePort implements SecretWritePort {

        private SecretWriteCommand lastCommand;

        @Override
        public SecretMetadata putSecret(SecretWriteCommand command) {
            lastCommand = command;
            return new SecretMetadata(
                    command.secretRef(),
                    command.tenantId(),
                    command.metadataJson(),
                    command.createdAt(),
                    null);
        }
    }
}
