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
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceTaskOutcome;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolutionRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunAggregate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DefaultMemoryMaintenanceService implements MemoryMaintenanceInboundPort {

    private final MemoryGarbageCollectionService garbageCollectionService;
    private final MemoryCompactionService compactionService;
    private final MemoryAliasResolutionService aliasResolutionService;
    private final MemoryMaintenanceRunRepositoryPort maintenanceRunRepositoryPort;
    private final MemoryTraceRecorder traceRecorder;
    private final boolean compactionEnabled;
    private final boolean aliasEnabled;
    private final boolean garbageCollectionEnabled;

    public DefaultMemoryMaintenanceService(MemoryGarbageCollectionService garbageCollectionService,
                                           boolean compactionEnabled,
                                           boolean aliasEnabled,
                                           boolean garbageCollectionEnabled) {
        this(garbageCollectionService,
                null,
                null,
                MemoryMaintenanceRunRepositoryPort.noop(),
                MemoryTraceRecorder.noop(),
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
                null,
                maintenanceRunRepositoryPort,
                MemoryTraceRecorder.noop(),
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
        this(garbageCollectionService,
                compactionService,
                null,
                maintenanceRunRepositoryPort,
                MemoryTraceRecorder.noop(),
                compactionEnabled,
                aliasEnabled,
                garbageCollectionEnabled);
    }

    public DefaultMemoryMaintenanceService(MemoryGarbageCollectionService garbageCollectionService,
                                           MemoryCompactionService compactionService,
                                           MemoryAliasResolutionService aliasResolutionService,
                                           MemoryMaintenanceRunRepositoryPort maintenanceRunRepositoryPort,
                                           MemoryTraceRecorder traceRecorder,
                                           boolean compactionEnabled,
                                           boolean aliasEnabled,
                                           boolean garbageCollectionEnabled) {
        this.garbageCollectionService = garbageCollectionService;
        this.compactionService = compactionService;
        this.aliasResolutionService = aliasResolutionService;
        this.maintenanceRunRepositoryPort = Objects.requireNonNullElseGet(
                maintenanceRunRepositoryPort,
                MemoryMaintenanceRunRepositoryPort::noop);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
        this.compactionEnabled = compactionEnabled;
        this.aliasEnabled = aliasEnabled;
        this.garbageCollectionEnabled = garbageCollectionEnabled;
    }

    public DefaultMemoryMaintenanceService(MemoryGarbageCollectionService garbageCollectionService,
                                           MemoryCompactionService compactionService,
                                           MemoryAliasResolutionService aliasResolutionService,
                                           MemoryMaintenanceRunRepositoryPort maintenanceRunRepositoryPort,
                                           boolean compactionEnabled,
                                           boolean aliasEnabled,
                                           boolean garbageCollectionEnabled) {
        this(garbageCollectionService,
                compactionService,
                aliasResolutionService,
                maintenanceRunRepositoryPort,
                MemoryTraceRecorder.noop(),
                compactionEnabled,
                aliasEnabled,
                garbageCollectionEnabled);
    }

    @Override
    public MemoryMaintenanceRunResult runMaintenance(MemoryMaintenanceRunCommand command) {
        MemoryMaintenanceRunCommand safeCommand = command == null
                ? new MemoryMaintenanceRunCommand(null, false, false, true)
                : command;
        List<String> skippedTasks = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<MemoryMaintenanceTaskOutcome> taskOutcomes = new ArrayList<>();
        MemoryCompactionResult compactionResult = runCompaction(safeCommand, skippedTasks, errors, taskOutcomes);
        MemoryAliasResolutionRunResult aliasResolutionResult = runAliasResolution(
                safeCommand, skippedTasks, errors, taskOutcomes);
        MemoryGarbageCollectionResult garbageCollectionResult = null;
        if (safeCommand.garbageCollectionEnabled()) {
            if (garbageCollectionEnabled && garbageCollectionService != null) {
                try {
                    garbageCollectionResult = garbageCollectionService.run(safeCommand.reason());
                    errors.addAll(garbageCollectionResult.errors());
                    taskOutcomes.add(outcomeFromErrors(
                            MemoryMaintenanceTaskOutcome.TASK_GARBAGE_COLLECTION,
                            garbageCollectionResult.errors()));
                } catch (RuntimeException ex) {
                    String message = errorMessage(ex);
                    errors.add("garbageCollection:" + message);
                    taskOutcomes.add(MemoryMaintenanceTaskOutcome.failed(
                            MemoryMaintenanceTaskOutcome.TASK_GARBAGE_COLLECTION, message));
                }
            } else {
                skippedTasks.add(MemoryMaintenanceRunResult.SKIP_GARBAGE_COLLECTION_DISABLED);
                taskOutcomes.add(MemoryMaintenanceTaskOutcome.skipped(
                        MemoryMaintenanceTaskOutcome.TASK_GARBAGE_COLLECTION,
                        MemoryMaintenanceRunResult.SKIP_GARBAGE_COLLECTION_DISABLED));
            }
        } else {
            taskOutcomes.add(MemoryMaintenanceTaskOutcome.notRequested(
                    MemoryMaintenanceTaskOutcome.TASK_GARBAGE_COLLECTION));
        }
        MemoryMaintenanceRunResult result = new MemoryMaintenanceRunResult(
                safeCommand.reason(),
                safeCommand.compactionEnabled(),
                safeCommand.aliasEnabled(),
                safeCommand.garbageCollectionEnabled(),
                compactionResult,
                aliasResolutionResult,
                garbageCollectionResult,
                skippedTasks,
                errors,
                taskOutcomes,
                Instant.now());
        persistRunRecord(result);
        recordTrace(result, compactionResult, aliasResolutionResult, garbageCollectionResult);
        return result;
    }

    private MemoryCompactionResult runCompaction(MemoryMaintenanceRunCommand command,
                                                 List<String> skippedTasks,
                                                 List<String> errors,
                                                 List<MemoryMaintenanceTaskOutcome> taskOutcomes) {
        if (!command.compactionEnabled()) {
            taskOutcomes.add(MemoryMaintenanceTaskOutcome.notRequested(
                    MemoryMaintenanceTaskOutcome.TASK_COMPACTION));
            return null;
        }
        if (!compactionEnabled || compactionService == null) {
            skippedTasks.add(MemoryMaintenanceRunResult.SKIP_COMPACTION_UNAVAILABLE);
            taskOutcomes.add(MemoryMaintenanceTaskOutcome.skipped(
                    MemoryMaintenanceTaskOutcome.TASK_COMPACTION,
                    MemoryMaintenanceRunResult.SKIP_COMPACTION_UNAVAILABLE));
            return null;
        }
        try {
            MemoryCompactionResult result = compactionService.run(command.reason());
            errors.addAll(result.errors());
            taskOutcomes.add(outcomeFromErrors(MemoryMaintenanceTaskOutcome.TASK_COMPACTION, result.errors()));
            return result;
        } catch (RuntimeException ex) {
            String message = errorMessage(ex);
            errors.add("compaction:" + message);
            taskOutcomes.add(MemoryMaintenanceTaskOutcome.failed(
                    MemoryMaintenanceTaskOutcome.TASK_COMPACTION, message));
            return null;
        }
    }

    private MemoryAliasResolutionRunResult runAliasResolution(MemoryMaintenanceRunCommand command,
                                                              List<String> skippedTasks,
                                                              List<String> errors,
                                                              List<MemoryMaintenanceTaskOutcome> taskOutcomes) {
        if (!command.aliasEnabled()) {
            taskOutcomes.add(MemoryMaintenanceTaskOutcome.notRequested(MemoryMaintenanceTaskOutcome.TASK_ALIAS));
            return null;
        }
        if (!aliasEnabled || aliasResolutionService == null) {
            skippedTasks.add(MemoryMaintenanceRunResult.SKIP_ALIAS_UNAVAILABLE);
            taskOutcomes.add(MemoryMaintenanceTaskOutcome.skipped(
                    MemoryMaintenanceTaskOutcome.TASK_ALIAS,
                    MemoryMaintenanceRunResult.SKIP_ALIAS_UNAVAILABLE));
            return null;
        }
        try {
            MemoryAliasResolutionRunResult result = aliasResolutionService.run(command.reason());
            errors.addAll(result.errors());
            taskOutcomes.add(outcomeFromErrors(MemoryMaintenanceTaskOutcome.TASK_ALIAS, result.errors()));
            return result;
        } catch (RuntimeException ex) {
            String message = errorMessage(ex);
            errors.add("alias:" + message);
            taskOutcomes.add(MemoryMaintenanceTaskOutcome.failed(
                    MemoryMaintenanceTaskOutcome.TASK_ALIAS, message));
            return null;
        }
    }

    @Override
    public MemoryMaintenanceRunPage pageMaintenanceRuns(MemoryMaintenanceRunQuery query) {
        MemoryMaintenanceRunQuery safeQuery = query == null ? new MemoryMaintenanceRunQuery(null, 1L, 20L) : query;
        return maintenanceRunRepositoryPort.pageMaintenanceRuns(safeQuery);
    }

    @Override
    public MemoryMaintenanceRunAggregate aggregateRecent(int limit) {
        return maintenanceRunRepositoryPort.aggregateRecent(limit);
    }

    private void persistRunRecord(MemoryMaintenanceRunResult result) {
        try {
            MemoryCompactionResult compaction = result.compactionResult();
            MemoryAliasResolutionRunResult alias = result.aliasResolutionResult();
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
                    alias == null ? 0 : alias.scannedCount(),
                    alias == null ? 0 : alias.normalizedCount(),
                    alias == null ? 0 : alias.dictionaryMatchCount(),
                    alias == null ? 0 : alias.skippedCount(),
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

    private void recordTrace(MemoryMaintenanceRunResult result,
                             MemoryCompactionResult compactionResult,
                             MemoryAliasResolutionRunResult aliasResolutionResult,
                             MemoryGarbageCollectionResult garbageCollectionResult) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", result.reason());
        details.put("compactionEnabled", result.compactionEnabled());
        details.put("aliasEnabled", result.aliasEnabled());
        details.put("garbageCollectionEnabled", result.garbageCollectionEnabled());
        details.put("skippedTasks", result.skippedTasks());
        details.put("errors", result.errors());
        details.put("taskOutcomes", result.taskOutcomes());
        if (compactionResult != null) {
            details.put("compactionScannedCount", compactionResult.scannedGroupCount());
            details.put("compactionGroupCount", compactionResult.compactedGroupCount());
            details.put("compactionFragmentCount", compactionResult.compactedFragmentCount());
        }
        if (aliasResolutionResult != null) {
            details.put("aliasScannedCount", aliasResolutionResult.scannedCount());
            details.put("aliasNormalizedCount", aliasResolutionResult.normalizedCount());
            details.put("aliasDictionaryMatchCount", aliasResolutionResult.dictionaryMatchCount());
        }
        if (garbageCollectionResult != null) {
            details.put("gcScannedCount", garbageCollectionResult.scannedCount());
            details.put("gcDerivedIndexCandidateCount", garbageCollectionResult.derivedIndexCandidateCount());
            details.put("gcArchiveCandidateCount", garbageCollectionResult.archiveCandidateCount());
            details.put("gcPhysicalDeleteCandidateCount", garbageCollectionResult.physicalDeleteCandidateCount());
            details.put("gcDeleteTaskCount", garbageCollectionResult.enqueuedDeleteTaskCount());
            details.put("gcMarkedIndexDeletedCount", garbageCollectionResult.markedIndexDeletedCount());
        }
        traceRecorder.record(new MemoryTraceEvent(
                result.reason(),
                "default",
                "",
                "",
                "",
                "memory-maintenance",
                "run-maintenance",
                result.errors().isEmpty() ? MemoryTraceEvent.STATUS_SUCCESS : MemoryTraceEvent.STATUS_FAILED,
                result.reason(),
                "run",
                details,
                result.executedAt()));
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

    private MemoryMaintenanceTaskOutcome outcomeFromErrors(String task, List<String> errors) {
        List<String> safeErrors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
        if (safeErrors.isEmpty()) {
            return MemoryMaintenanceTaskOutcome.succeeded(task);
        }
        return MemoryMaintenanceTaskOutcome.failed(task, String.join(";", safeErrors));
    }
}
