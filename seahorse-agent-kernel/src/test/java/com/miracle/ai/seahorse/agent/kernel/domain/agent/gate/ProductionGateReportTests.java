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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.gate;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionGateReportTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldAggregateFailBeforeWarnAndPass() {
        ProductionGateReport report = new ProductionGateReport(
                "gate-1",
                "agent-1",
                "version-1",
                null,
                List.of(
                        ProductionGateCheckItem.pass(ProductionGateCheckCode.OWNER_PRESENT, "owner exists"),
                        ProductionGateCheckItem.warn(ProductionGateCheckCode.EVAL_PASSING, "eval not connected"),
                        ProductionGateCheckItem.fail(ProductionGateCheckCode.AUDIT_LEDGER_ENABLED, "audit unavailable")),
                NOW);

        assertEquals(ProductionGateStatus.FAIL, report.status());
        assertEquals(ProductionGateStatus.WARN,
                report.item(ProductionGateCheckCode.EVAL_PASSING).orElseThrow().status());
    }
}
