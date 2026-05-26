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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.cost;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CostUsageRecordTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldRejectNegativeUsageValues() {
        assertThrows(IllegalArgumentException.class, () -> record(-1L, 1L, 0.1d, CostUsageSource.MODEL, null));
        assertThrows(IllegalArgumentException.class, () -> record(1L, -1L, 0.1d, CostUsageSource.MODEL, null));
        assertThrows(IllegalArgumentException.class, () -> record(1L, 1L, -0.1d, CostUsageSource.MODEL, null));
    }

    @Test
    void shouldRequireReasonRefForManualAdjustment() {
        assertThrows(IllegalArgumentException.class, () -> record(
                1L,
                1L,
                0.1d,
                CostUsageSource.MANUAL_ADJUSTMENT,
                null));
    }

    private static CostUsageRecord record(Long tokens,
                                          Long calls,
                                          Double cost,
                                          CostUsageSource source,
                                          String reasonRef) {
        return new CostUsageRecord(
                "usage-1",
                "tenant-a",
                "agent-1",
                "run-1",
                "user-1",
                "tool-1",
                "model-1",
                source,
                tokens,
                calls,
                cost,
                reasonRef,
                NOW);
    }
}
