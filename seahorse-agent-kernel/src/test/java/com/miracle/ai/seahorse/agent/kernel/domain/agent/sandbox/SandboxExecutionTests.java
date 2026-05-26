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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SandboxExecutionTests {

    private static final Instant CREATED_AT = Instant.parse("2026-05-26T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-05-26T00:00:01Z");

    @Test
    void shouldAllowCreatedRunningSucceededTransitionOnly() {
        SandboxExecution created = SandboxExecution.created(
                "exec-1",
                "session-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                CREATED_AT);

        SandboxExecution running = created.markRunning(UPDATED_AT);
        SandboxExecution succeeded = running.markSucceeded(UPDATED_AT.plusSeconds(1), "ok");

        assertEquals(SandboxExecutionStatus.RUNNING, running.status());
        assertEquals(SandboxExecutionStatus.SUCCEEDED, succeeded.status());
        assertEquals("ok", succeeded.resultSummary());
    }

    @Test
    void shouldRejectInvalidExecutionTransition() {
        SandboxExecution created = SandboxExecution.created(
                "exec-1",
                "session-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                CREATED_AT);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> created.markSucceeded(UPDATED_AT, "bad"));

        assertEquals("Sandbox execution must be RUNNING before completion", error.getMessage());
    }
}
