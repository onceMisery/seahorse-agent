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

package com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DefaultMemoryMaintenanceService implements MemoryMaintenanceInboundPort {

    private final MemoryGarbageCollectionService garbageCollectionService;
    private final boolean compactionEnabled;
    private final boolean aliasEnabled;
    private final boolean garbageCollectionEnabled;

    public DefaultMemoryMaintenanceService(MemoryGarbageCollectionService garbageCollectionService,
                                           boolean compactionEnabled,
                                           boolean aliasEnabled,
                                           boolean garbageCollectionEnabled) {
        this.garbageCollectionService = garbageCollectionService;
        this.compactionEnabled = compactionEnabled;
        this.aliasEnabled = aliasEnabled;
        this.garbageCollectionEnabled = garbageCollectionEnabled;
    }

    @Override
    public MemoryMaintenanceRunResult runMaintenance(MemoryMaintenanceRunCommand command) {
        MemoryMaintenanceRunCommand safeCommand = command == null
                ? new MemoryMaintenanceRunCommand(null, false, false, true)
                : command;
        List<String> skippedTasks = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (safeCommand.compactionEnabled() && !compactionEnabled) {
            skippedTasks.add(MemoryMaintenanceRunResult.SKIP_COMPACTION_UNAVAILABLE);
        }
        if (safeCommand.aliasEnabled() && !aliasEnabled) {
            skippedTasks.add(MemoryMaintenanceRunResult.SKIP_ALIAS_UNAVAILABLE);
        }
        MemoryGarbageCollectionResult garbageCollectionResult = null;
        if (safeCommand.garbageCollectionEnabled()) {
            if (garbageCollectionEnabled && garbageCollectionService != null) {
                try {
                    garbageCollectionResult = garbageCollectionService.run(safeCommand.reason());
                } catch (RuntimeException ex) {
                    errors.add("garbageCollection:" + errorMessage(ex));
                }
            } else {
                skippedTasks.add(MemoryMaintenanceRunResult.SKIP_GARBAGE_COLLECTION_DISABLED);
            }
        }
        return new MemoryMaintenanceRunResult(
                safeCommand.reason(),
                safeCommand.compactionEnabled(),
                safeCommand.aliasEnabled(),
                safeCommand.garbageCollectionEnabled(),
                garbageCollectionResult,
                skippedTasks,
                errors,
                Instant.now());
    }

    private String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
    }
}
