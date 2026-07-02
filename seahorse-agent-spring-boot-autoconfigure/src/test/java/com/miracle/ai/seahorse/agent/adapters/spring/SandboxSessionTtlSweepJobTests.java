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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxSessionTtlSweepJobTests {

    @Test
    void shouldSweepExpiredSessionsWithConfiguredTenantAndLimit() {
        SandboxRuntimeInboundPort sandboxRuntime = mock(SandboxRuntimeInboundPort.class);
        DistributedLockPort lockPort = mock(DistributedLockPort.class);
        when(lockPort.tryLock(eq(SandboxSessionTtlSweepJob.LOCK_NAME), eq(Duration.ZERO), any(Duration.class)))
                .thenReturn(true);
        when(sandboxRuntime.sweepExpiredSessions("tenant-a", 7)).thenReturn(new SandboxSessionSweepResult(
                "tenant-a",
                Instant.EPOCH,
                2,
                1,
                0,
                List.of()));

        new SandboxSessionTtlSweepJob(sandboxRuntime, lockPort, "tenant-a", 7).sweepExpiredSessions();

        verify(sandboxRuntime).sweepExpiredSessions("tenant-a", 7);
        verify(lockPort).unlock(SandboxSessionTtlSweepJob.LOCK_NAME);
    }

    @Test
    void shouldSkipSweepWhenLockIsHeldByAnotherNode() {
        SandboxRuntimeInboundPort sandboxRuntime = mock(SandboxRuntimeInboundPort.class);
        DistributedLockPort lockPort = mock(DistributedLockPort.class);
        when(lockPort.tryLock(eq(SandboxSessionTtlSweepJob.LOCK_NAME), eq(Duration.ZERO), any(Duration.class)))
                .thenReturn(false);

        new SandboxSessionTtlSweepJob(sandboxRuntime, lockPort, "tenant-a", 7).sweepExpiredSessions();

        verify(sandboxRuntime, never()).sweepExpiredSessions(any(), anyInt());
        verify(lockPort, never()).unlock(SandboxSessionTtlSweepJob.LOCK_NAME);
    }

    @Test
    void shouldReleaseLockWhenSweepThrows() {
        SandboxRuntimeInboundPort sandboxRuntime = mock(SandboxRuntimeInboundPort.class);
        DistributedLockPort lockPort = mock(DistributedLockPort.class);
        when(lockPort.tryLock(eq(SandboxSessionTtlSweepJob.LOCK_NAME), eq(Duration.ZERO), any(Duration.class)))
                .thenReturn(true);
        when(sandboxRuntime.sweepExpiredSessions("default", 1)).thenThrow(new IllegalStateException("boom"));

        SandboxSessionTtlSweepJob job = new SandboxSessionTtlSweepJob(sandboxRuntime, lockPort, " ", 0);

        assertThatCode(job::sweepExpiredSessions).doesNotThrowAnyException();
        verify(sandboxRuntime).sweepExpiredSessions("default", 1);
        verify(lockPort).unlock(SandboxSessionTtlSweepJob.LOCK_NAME);
    }
}
