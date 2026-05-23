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

import java.util.Map;

@RestController
public class SeahorseMemoryMaintenanceController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_AGGREGATE_LIMIT = MemoryMaintenanceRunAggregate.DEFAULT_LIMIT_LITERAL;

    private final ObjectProvider<MemoryMaintenanceInboundPort> maintenancePortProvider;

    public SeahorseMemoryMaintenanceController(ObjectProvider<MemoryMaintenanceInboundPort> maintenancePortProvider) {
        this.maintenancePortProvider = maintenancePortProvider;
    }

    @PostMapping("/memories/maintenance/run")
    public Map<String, Object> runMaintenance(
            @RequestParam(defaultValue = MemoryMaintenanceRunCommand.DEFAULT_REASON) String reason,
            @RequestParam(defaultValue = "false") boolean compaction,
            @RequestParam(defaultValue = "false") boolean alias,
            @RequestParam(defaultValue = "true") boolean gc) {
        MemoryMaintenanceInboundPort maintenancePort = maintenancePortProvider.getIfAvailable();
        if (maintenancePort == null) {
            return Map.of(KEY_CODE, "1", "message", "Service not available");
        }
        return ok(maintenancePort.runMaintenance(new MemoryMaintenanceRunCommand(reason, compaction, alias, gc)));
    }

    @GetMapping("/memories/maintenance-runs")
    public Map<String, Object> pageMaintenanceRuns(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        MemoryMaintenanceInboundPort maintenancePort = maintenancePortProvider.getIfAvailable();
        if (maintenancePort == null) {
            return Map.of(KEY_CODE, "1", "message", "Service not available");
        }
        return ok(maintenancePort.pageMaintenanceRuns(new MemoryMaintenanceRunQuery(status, current, size)));
    }

    @GetMapping("/memories/maintenance-runs/aggregate")
    public Map<String, Object> aggregateMaintenanceRuns(
            @RequestParam(defaultValue = DEFAULT_AGGREGATE_LIMIT) int limit) {
        MemoryMaintenanceInboundPort maintenancePort = maintenancePortProvider.getIfAvailable();
        if (maintenancePort == null) {
            return Map.of(KEY_CODE, "1", "message", "Service not available");
        }
        return ok(maintenancePort.aggregateRecent(limit));
    }

    private Map<String, Object> ok(Object data) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data == null ? Map.of() : data);
    }
}
