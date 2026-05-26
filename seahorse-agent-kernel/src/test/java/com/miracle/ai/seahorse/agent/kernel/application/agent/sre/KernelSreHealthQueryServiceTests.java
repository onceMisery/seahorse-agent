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

package com.miracle.ai.seahorse.agent.kernel.application.agent.sre;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthContributorPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KernelSreHealthQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldAggregateWorstContributorStatus() {
        KernelSreHealthQueryService service = new KernelSreHealthQueryService(List.of(
                () -> new SreHealthItem("database", SreHealthStatus.GREEN, "ok", "db:primary"),
                () -> new SreHealthItem("queue", SreHealthStatus.RED, "lag exceeded", "queue:p95")),
                CLOCK);

        var report = service.current();

        assertEquals(SreHealthStatus.RED, report.status());
        assertEquals("database", report.items().get(0).contributorName());
        assertEquals("queue", report.items().get(1).contributorName());
    }

    @Test
    void shouldConvertContributorExceptionToWarnItem() {
        SreHealthContributorPort broken = () -> {
            throw new IllegalStateException("secret-token stack should not be exposed");
        };
        KernelSreHealthQueryService service = new KernelSreHealthQueryService(List.of(broken), CLOCK);

        var report = service.current();

        assertEquals(SreHealthStatus.WARN, report.status());
        assertEquals("contributor-1", report.items().get(0).contributorName());
        assertEquals(SreHealthStatus.WARN, report.items().get(0).status());
        assertFalse(report.items().get(0).message().contains("secret-token"));
    }
}
