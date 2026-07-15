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
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxRuntimeNodeCleanupJobTests {

    private static final Duration RETENTION = Duration.ofDays(7);

    @Test
    void shouldCleanupStaleRegistrationsWhenLockIsAcquired() {
        SandboxRuntimeNodeRegistryInboundPort registry = mock(SandboxRuntimeNodeRegistryInboundPort.class);
        DistributedLockPort lockPort = mock(DistributedLockPort.class);
        when(lockPort.tryLock(SandboxRuntimeNodeCleanupJob.LOCK_NAME, Duration.ZERO, Duration.ofMinutes(5)))
                .thenReturn(true);
        when(registry.cleanupStaleRegistrations(RETENTION, 100)).thenReturn(2);

        new SandboxRuntimeNodeCleanupJob(registry, lockPort, RETENTION, 100).cleanupStaleRegistrations();

        verify(registry).cleanupStaleRegistrations(RETENTION, 100);
        verify(lockPort).unlock(SandboxRuntimeNodeCleanupJob.LOCK_NAME);
    }

    @Test
    void shouldSkipCleanupWhenLockIsHeldByAnotherNode() {
        SandboxRuntimeNodeRegistryInboundPort registry = mock(SandboxRuntimeNodeRegistryInboundPort.class);
        DistributedLockPort lockPort = mock(DistributedLockPort.class);
        when(lockPort.tryLock(SandboxRuntimeNodeCleanupJob.LOCK_NAME, Duration.ZERO, Duration.ofMinutes(5)))
                .thenReturn(false);

        new SandboxRuntimeNodeCleanupJob(registry, lockPort, RETENTION, 100).cleanupStaleRegistrations();

        verify(registry, never()).cleanupStaleRegistrations(RETENTION, 100);
        verify(lockPort, never()).unlock(SandboxRuntimeNodeCleanupJob.LOCK_NAME);
    }

    @Test
    void shouldReleaseLockWhenCleanupThrows() {
        SandboxRuntimeNodeRegistryInboundPort registry = mock(SandboxRuntimeNodeRegistryInboundPort.class);
        DistributedLockPort lockPort = mock(DistributedLockPort.class);
        when(lockPort.tryLock(SandboxRuntimeNodeCleanupJob.LOCK_NAME, Duration.ZERO, Duration.ofMinutes(5)))
                .thenReturn(true);
        when(registry.cleanupStaleRegistrations(RETENTION, 100)).thenThrow(new IllegalStateException("boom"));
        SandboxRuntimeNodeCleanupJob job = new SandboxRuntimeNodeCleanupJob(registry, lockPort, RETENTION, 100);

        assertThatCode(job::cleanupStaleRegistrations).doesNotThrowAnyException();
        verify(lockPort).unlock(SandboxRuntimeNodeCleanupJob.LOCK_NAME);
    }
}
