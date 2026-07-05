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

package com.miracle.ai.seahorse.agent.ports.inbound.gate;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileProductionGateCheck;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class GateResults {

    private GateResults() {
    }

    public static GateResult fromAgentReport(ProductionGateReport report) {
        Objects.requireNonNull(report, "report must not be null");
        List<String> blockingCodes = report.items().stream()
                .filter(item -> item.status() == ProductionGateStatus.FAIL)
                .map(item -> item.code().name())
                .toList();
        return new GateResult(
                "AGENT",
                report.agentId(),
                report.status().name(),
                report.status() != ProductionGateStatus.FAIL,
                blockingCodes,
                report.items().stream()
                        .map(GateResults::fromAgentItem)
                        .toList(),
                report.checkedAt(),
                "ProductionGateReport",
                report.reportId());
    }

    public static GateResult fromRunProfileCheck(RunProfileProductionGateCheck check) {
        Objects.requireNonNull(check, "check must not be null");
        List<RunProfileProductionGateCheck.CheckItem> items = Objects.requireNonNullElse(
                check.getCheckItems(),
                List.<RunProfileProductionGateCheck.CheckItem>of());
        List<String> blockingCodes = Objects.requireNonNullElse(
                check.getBlockingCodes(),
                List.<String>of());
        return new GateResult(
                "RUN_PROFILE",
                String.valueOf(check.getRunProfileId()),
                check.isPassed() ? "PASS" : "FAIL",
                check.isPassed(),
                blockingCodes,
                items.stream()
                        .filter(Objects::nonNull)
                        .map(GateResults::fromRunProfileItem)
                        .toList(),
                Instant.now(),
                "RunProfileProductionGateCheck",
                String.valueOf(check.getRunProfileId()));
    }

    private static GateResultItem fromAgentItem(ProductionGateCheckItem item) {
        return new GateResultItem(item.code().name(), item.status().name(), item.message());
    }

    private static GateResultItem fromRunProfileItem(RunProfileProductionGateCheck.CheckItem item) {
        return new GateResultItem(item.getCode(), item.getStatus(), item.getMessage());
    }
}
