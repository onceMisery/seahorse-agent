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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunAggregate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseMemoryMaintenanceController {

    private static final String DEFAULT_AGGREGATE_LIMIT = MemoryMaintenanceRunAggregate.DEFAULT_LIMIT_LITERAL;

    private final ObjectProvider<MemoryMaintenanceInboundPort> maintenancePortProvider;

    public SeahorseMemoryMaintenanceController(ObjectProvider<MemoryMaintenanceInboundPort> maintenancePortProvider) {
        this.maintenancePortProvider = maintenancePortProvider;
    }

    @PostMapping("/memories/maintenance/run")
    public ApiResponse<Object> runMaintenance(
            @RequestParam(defaultValue = MemoryMaintenanceRunCommand.DEFAULT_REASON) String reason,
            @RequestParam(defaultValue = "false") boolean compaction,
            @RequestParam(defaultValue = "false") boolean alias,
            @RequestParam(defaultValue = "true") boolean gc) {
        return ApiResponses.requireServiceOrError(maintenancePortProvider,
                port -> port.runMaintenance(new MemoryMaintenanceRunCommand(reason, compaction, alias, gc)));
    }

    @GetMapping("/memories/maintenance-runs")
    public ApiResponse<Object> pageMaintenanceRuns(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponses.requireServiceOrError(maintenancePortProvider,
                port -> port.pageMaintenanceRuns(new MemoryMaintenanceRunQuery(status, current, size)));
    }

    @GetMapping("/memories/maintenance-runs/aggregate")
    public ApiResponse<Object> aggregateMaintenanceRuns(
            @RequestParam(defaultValue = DEFAULT_AGGREGATE_LIMIT) int limit) {
        return ApiResponses.requireServiceOrError(maintenancePortProvider, port -> port.aggregateRecent(limit));
    }
}
