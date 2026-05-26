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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.eval;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentEvalSummaryTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldRejectNegativeScoreAndInvalidThreshold() {
        assertThrows(IllegalArgumentException.class, () -> summary(
                AgentEvalStatus.PASS,
                -1.0d,
                0.9d,
                0.7d,
                10,
                NOW));
        assertThrows(IllegalArgumentException.class, () -> summary(
                AgentEvalStatus.PASS,
                0.8d,
                0.6d,
                0.7d,
                10,
                NOW));
        assertThrows(IllegalArgumentException.class, () -> summary(
                AgentEvalStatus.PASS,
                0.8d,
                0.9d,
                0.7d,
                -1,
                NOW));
    }

    @Test
    void shouldProjectStaleWithoutDowngradingFail() {
        Instant staleCreatedAt = NOW.minus(AgentEvalLimits.DEFAULT_MAX_AGE_DAYS + 1L,
                java.time.temporal.ChronoUnit.DAYS);

        AgentEvalSummary stalePass = summary(
                AgentEvalStatus.PASS,
                0.95d,
                0.9d,
                0.7d,
                10,
                staleCreatedAt);
        AgentEvalSummary staleFail = summary(
                AgentEvalStatus.FAIL,
                0.3d,
                0.9d,
                0.7d,
                10,
                staleCreatedAt);

        assertEquals(AgentEvalStatus.STALE, stalePass.effectiveStatus(NOW));
        assertEquals(AgentEvalStatus.FAIL, staleFail.effectiveStatus(NOW));
    }

    private static AgentEvalSummary summary(AgentEvalStatus status,
                                            double score,
                                            double passThreshold,
                                            double warnThreshold,
                                            int caseCount,
                                            Instant createdAt) {
        return new AgentEvalSummary(
                "eval-1",
                "tenant-1",
                "agent-1",
                "version-1",
                AgentEvalType.SAFETY,
                status,
                score,
                passThreshold,
                warnThreshold,
                caseCount,
                "dataset:v1",
                "eval-run-1",
                List.of("trace:1"),
                "admin-1",
                createdAt);
    }
}
