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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.quota;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

class QuotaPolicyTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldRequireAtLeastOneLimit() {
        assertThrows(IllegalArgumentException.class, () -> new QuotaPolicy(
                "policy-1",
                "tenant-a",
                QuotaScope.TENANT,
                "tenant-a",
                QuotaPolicyStatus.ACTIVE,
                null,
                null,
                null,
                QuotaPolicyLimits.DEFAULT_WARN_RATIO,
                NOW,
                NOW));
    }

    @Test
    void shouldRejectNegativeLimitAndInvalidWarnRatio() {
        assertThrows(IllegalArgumentException.class, () -> new QuotaPolicy(
                "policy-1",
                "tenant-a",
                QuotaScope.AGENT,
                "agent-1",
                QuotaPolicyStatus.ACTIVE,
                -1L,
                null,
                null,
                QuotaPolicyLimits.DEFAULT_WARN_RATIO,
                NOW,
                NOW));

        assertThrows(IllegalArgumentException.class, () -> new QuotaPolicy(
                "policy-1",
                "tenant-a",
                QuotaScope.AGENT,
                "agent-1",
                QuotaPolicyStatus.ACTIVE,
                100L,
                null,
                null,
                1.25d,
                NOW,
                NOW));
    }
}
