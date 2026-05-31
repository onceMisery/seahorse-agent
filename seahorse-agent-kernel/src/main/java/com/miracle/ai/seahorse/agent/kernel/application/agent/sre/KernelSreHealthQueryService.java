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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SreHealthInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthContributorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthReportProviderPort;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KernelSreHealthQueryService implements SreHealthInboundPort, SreHealthReportProviderPort {

    private static final String REPORT_ID_PREFIX = "sre_";
    private static final String EXCEPTION_MESSAGE = "Contributor health check failed";

    private final List<SreHealthContributorPort> contributors;
    private final Clock clock;

    public KernelSreHealthQueryService(List<SreHealthContributorPort> contributors, Clock clock) {
        this.contributors = contributors == null ? List.of() : List.copyOf(contributors);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public SreHealthReport current() {
        List<SreHealthItem> items = new ArrayList<>();
        for (int i = 0; i < contributors.size(); i++) {
            items.add(readContributor(contributors.get(i), i + 1));
        }
        return new SreHealthReport(reportId(), null, items, clock.instant());
    }

    private SreHealthItem readContributor(SreHealthContributorPort contributor, int number) {
        try {
            return Objects.requireNonNull(contributor.current(), "contributor returned null health item");
        } catch (Exception ex) {
            return new SreHealthItem("contributor-" + number, SreHealthStatus.WARN, EXCEPTION_MESSAGE, null);
        }
    }

    private String reportId() {
        return REPORT_ID_PREFIX + SnowflakeIds.nextIdString();
    }
}
