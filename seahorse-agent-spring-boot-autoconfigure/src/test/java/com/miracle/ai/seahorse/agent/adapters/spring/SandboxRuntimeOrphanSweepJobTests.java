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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxRuntimeOrphanSweepJobTests {

    @Test
    void shouldSweepOrphanedRuntimeResourcesWhenLockIsAcquired() {
        SandboxRuntimeInboundPort sandboxRuntime = mock(SandboxRuntimeInboundPort.class);
        DistributedLockPort lockPort = mock(DistributedLockPort.class);
        when(lockPort.tryLock(SandboxRuntimeOrphanSweepJob.LOCK_NAME, Duration.ZERO, Duration.ofMinutes(5)))
                .thenReturn(true);
        when(sandboxRuntime.sweepOrphanedRuntimeResources()).thenReturn(new SandboxRuntimeCleanupResult(
                Instant.EPOCH,
                1,
                2,
                1,
                0,
                1,
                0,
                List.of("sandbox_container_orphan"),
                List.of()));

        new SandboxRuntimeOrphanSweepJob(sandboxRuntime, lockPort).sweepOrphanedRuntimeResources();

        verify(sandboxRuntime).sweepOrphanedRuntimeResources();
        verify(lockPort).unlock(SandboxRuntimeOrphanSweepJob.LOCK_NAME);
    }

    @Test
    void shouldSkipRuntimeSweepWhenLockIsHeldByAnotherNode() {
        SandboxRuntimeInboundPort sandboxRuntime = mock(SandboxRuntimeInboundPort.class);
        DistributedLockPort lockPort = mock(DistributedLockPort.class);
        when(lockPort.tryLock(SandboxRuntimeOrphanSweepJob.LOCK_NAME, Duration.ZERO, Duration.ofMinutes(5)))
                .thenReturn(false);

        new SandboxRuntimeOrphanSweepJob(sandboxRuntime, lockPort).sweepOrphanedRuntimeResources();

        verify(sandboxRuntime, never()).sweepOrphanedRuntimeResources();
        verify(lockPort, never()).unlock(SandboxRuntimeOrphanSweepJob.LOCK_NAME);
    }

    @Test
    void shouldReleaseLockWhenRuntimeSweepThrows() {
        SandboxRuntimeInboundPort sandboxRuntime = mock(SandboxRuntimeInboundPort.class);
        DistributedLockPort lockPort = mock(DistributedLockPort.class);
        when(lockPort.tryLock(SandboxRuntimeOrphanSweepJob.LOCK_NAME, Duration.ZERO, Duration.ofMinutes(5)))
                .thenReturn(true);
        when(sandboxRuntime.sweepOrphanedRuntimeResources()).thenThrow(new IllegalStateException("boom"));

        SandboxRuntimeOrphanSweepJob job = new SandboxRuntimeOrphanSweepJob(sandboxRuntime, lockPort);

        assertThatCode(job::sweepOrphanedRuntimeResources).doesNotThrowAnyException();
        verify(sandboxRuntime).sweepOrphanedRuntimeResources();
        verify(lockPort).unlock(SandboxRuntimeOrphanSweepJob.LOCK_NAME);
    }
}
