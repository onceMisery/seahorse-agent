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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentHandoffTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldKeepTerminalStatusImmutable() {
        AgentHandoff handoff = handoff(AgentHandoffStatus.RUNNING, null, "child-run-1");

        AgentHandoff succeeded = handoff.succeed(NOW);
        AgentHandoff cancelled = succeeded.cancel(NOW.plusSeconds(60));
        AgentHandoff failed = succeeded.fail(AgentHandoffFailureCode.POLICY_DENIED, NOW.plusSeconds(120));

        assertEquals(AgentHandoffStatus.SUCCEEDED, succeeded.status());
        assertEquals(AgentHandoffStatus.SUCCEEDED, cancelled.status());
        assertEquals(AgentHandoffStatus.SUCCEEDED, failed.status());
        assertEquals(NOW, cancelled.finishedAt());
        assertEquals(NOW, failed.finishedAt());
        assertSame(succeeded, cancelled);
        assertSame(succeeded, failed);
    }

    private static AgentHandoff handoff(AgentHandoffStatus status,
                                        AgentHandoffFailureCode failureCode,
                                        String childRunId) {
        return new AgentHandoff(
                "handoff-1",
                "tenant-1",
                "parent-run-1",
                childRunId,
                "source-agent",
                "target-agent",
                status,
                failureCode,
                "delegate analysis",
                "{\"input\":\"summary\"}",
                "{\"items\":[]}",
                NOW,
                NOW,
                null);
    }
}
