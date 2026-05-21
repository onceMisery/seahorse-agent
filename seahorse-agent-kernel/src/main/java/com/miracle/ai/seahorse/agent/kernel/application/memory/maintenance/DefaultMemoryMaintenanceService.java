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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRepositoryPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DefaultMemoryMaintenanceService implements MemoryMaintenanceInboundPort {

    private final MemoryGarbageCollectionService garbageCollectionService;
    private final MemoryCompactionService compactionService;
    private final MemoryMaintenanceRunRepositoryPort maintenanceRunRepositoryPort;
    private final boolean compactionEnabled;
    private final boolean aliasEnabled;
    private final boolean garbageCollectionEnabled;

    public DefaultMemoryMaintenanceService(MemoryGarbageCollectionService garbageCollectionService,
                                           boolean compactionEnabled,
                                           boolean aliasEnabled,
                                           boolean garbageCollectionEnabled) {
        this(garbageCollectionService,
                null,
                MemoryMaintenanceRunRepositoryPort.noop(),
                compactionEnabled,
                aliasEnabled,
                garbageCollectionEnabled);
    }

    public DefaultMemoryMaintenanceService(MemoryGarbageCollectionService garbageCollectionService,
                                           MemoryMaintenanceRunRepositoryPort maintenanceRunRepositoryPort,
                                           boolean compactionEnabled,
                                           boolean aliasEnabled,
                                           boolean garbageCollectionEnabled) {
        this(garbageCollectionService,
                null,
                maintenanceRunRepositoryPort,
                compactionEnabled,
                aliasEnabled,
                garbageCollectionEnabled);
    }

    public DefaultMemoryMaintenanceService(MemoryGarbageCollectionService garbageCollectionService,
                                           MemoryCompactionService compactionService,
                                           MemoryMaintenanceRunRepositoryPort maintenanceRunRepositoryPort,
                                           boolean compactionEnabled,
                                           boolean aliasEnabled,
                                           boolean garbageCollectionEnabled) {
        this.garbageCollectionService = garbageCollectionService;
        this.compactionService = compactionService;
        this.maintenanceRunRepositoryPort = Objects.requireNonNullElseGet(
                maintenanceRunRepositoryPort,
                MemoryMaintenanceRunRepositoryPort::noop);
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
        MemoryCompactionResult compactionResult = runCompaction(safeCommand, skippedTasks, errors);
        if (safeCommand.aliasEnabled() && !aliasEnabled) {
            skippedTasks.add(MemoryMaintenanceRunResult.SKIP_ALIAS_UNAVAILABLE);
        }
        MemoryGarbageCollectionResult garbageCollectionResult = null;
        if (safeCommand.garbageCollectionEnabled()) {
            if (garbageCollectionEnabled && garbageCollectionService != null) {
                try {
                    garbageCollectionResult = garbageCollectionService.run(safeCommand.reason());
                    errors.addAll(garbageCollectionResult.errors());
                } catch (RuntimeException ex) {
                    errors.add("garbageCollection:" + errorMessage(ex));
                }
            } else {
                skippedTasks.add(MemoryMaintenanceRunResult.SKIP_GARBAGE_COLLECTION_DISABLED);
            }
        }
        MemoryMaintenanceRunResult result = new MemoryMaintenanceRunResult(
                safeCommand.reason(),
                safeCommand.compactionEnabled(),
                safeCommand.aliasEnabled(),
                safeCommand.garbageCollectionEnabled(),
                compactionResult,
                garbageCollectionResult,
                skippedTasks,
                errors,
                Instant.now());
        persistRunRecord(result);
        return result;
    }

    private MemoryCompactionResult runCompaction(MemoryMaintenanceRunCommand command,
                                                 List<String> skippedTasks,
                                                 List<String> errors) {
        if (!command.compactionEnabled()) {
            return null;
        }
        if (!compactionEnabled || compactionService == null) {
            skippedTasks.add(MemoryMaintenanceRunResult.SKIP_COMPACTION_UNAVAILABLE);
            return null;
        }
        try {
            MemoryCompactionResult result = compactionService.run(command.reason());
            errors.addAll(result.errors());
            return result;
        } catch (RuntimeException ex) {
            errors.add("compaction:" + errorMessage(ex));
            return null;
        }
    }

    @Override
    public MemoryMaintenanceRunPage pageMaintenanceRuns(MemoryMaintenanceRunQuery query) {
        MemoryMaintenanceRunQuery safeQuery = query == null ? new MemoryMaintenanceRunQuery(null, 1L, 20L) : query;
        return maintenanceRunRepositoryPort.pageMaintenanceRuns(safeQuery);
    }

    private void persistRunRecord(MemoryMaintenanceRunResult result) {
        try {
            MemoryCompactionResult compaction = result.compactionResult();
            MemoryGarbageCollectionResult gc = result.garbageCollectionResult();
            maintenanceRunRepositoryPort.save(new MemoryMaintenanceRunRecord(
                    "maintenance-" + UUID.randomUUID(),
                    result.reason(),
                    status(result),
                    result.compactionEnabled(),
                    result.aliasEnabled(),
                    result.garbageCollectionEnabled(),
                    compaction == null ? 0 : compaction.scannedGroupCount(),
                    compaction == null ? 0 : compaction.compactedGroupCount(),
                    compaction == null ? 0 : compaction.compactedFragmentCount(),
                    gc == null ? 0 : gc.scannedCount(),
                    gc == null ? 0 : gc.enqueuedDeleteTaskCount(),
                    gc == null ? 0 : gc.markedIndexDeletedCount(),
                    gc != null && gc.dryRun(),
                    result.skippedTasks(),
                    result.errors(),
                    result.executedAt(),
                    Instant.now()));
        } catch (RuntimeException ignored) {
            // Maintenance records are observability data and must not change maintenance execution semantics.
        }
    }

    private String status(MemoryMaintenanceRunResult result) {
        if (!result.errors().isEmpty()) {
            return MemoryMaintenanceRunRecord.STATUS_FAILED;
        }
        if (!result.skippedTasks().isEmpty()) {
            return MemoryMaintenanceRunRecord.STATUS_SUCCEEDED_WITH_WARNINGS;
        }
        return MemoryMaintenanceRunRecord.STATUS_SUCCEEDED;
    }

    private String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
    }
}
