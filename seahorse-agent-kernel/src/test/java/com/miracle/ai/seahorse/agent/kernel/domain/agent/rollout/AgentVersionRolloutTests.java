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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVersionRolloutTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldRejectCanaryPercentOutsideNamedLimits() {
        assertThrows(IllegalArgumentException.class, () -> rollout(AgentRolloutLimits.MIN_CANARY_PERCENT - 1));
        assertThrows(IllegalArgumentException.class, () -> rollout(AgentRolloutLimits.MAX_CANARY_PERCENT + 1));
    }

    @Test
    void shouldPreventTerminalStatusRollback() {
        AgentVersionRollout promoted = rollout(AgentRolloutLimits.DEFAULT_CANARY_PERCENT)
                .promote("gate-1", NOW.plusSeconds(1));

        assertTrue(promoted.status().terminal());
        assertThrows(IllegalStateException.class, () -> promoted.pause(NOW.plusSeconds(2)));
        assertThrows(IllegalStateException.class, () -> promoted.fail(
                AgentRolloutFailureCode.GATE_FAILED,
                NOW.plusSeconds(2)));
    }

    @Test
    void shouldRecordGateFailureAsTerminalFailure() {
        AgentVersionRollout failed = rollout(AgentRolloutLimits.DEFAULT_CANARY_PERCENT)
                .fail(AgentRolloutFailureCode.GATE_MISSING, NOW.plusSeconds(1));

        assertEquals(AgentRolloutStatus.FAILED, failed.status());
        assertEquals(AgentRolloutFailureCode.GATE_MISSING, failed.failureCode());
        assertTrue(failed.status().terminal());
    }

    private static AgentVersionRollout rollout(int canaryPercent) {
        return new AgentVersionRollout(
                "rollout-1",
                "tenant-1",
                "agent-1",
                "version-1",
                canaryPercent,
                AgentRolloutStatus.RUNNING,
                null,
                null,
                "operator-1",
                NOW,
                NOW,
                null);
    }
}
