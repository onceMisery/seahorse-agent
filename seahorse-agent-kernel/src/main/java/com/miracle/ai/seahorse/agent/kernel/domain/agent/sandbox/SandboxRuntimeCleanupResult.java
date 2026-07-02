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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.time.Instant;
import java.util.List;

public record SandboxRuntimeCleanupResult(Instant sweptAt,
                                          int activeSessionCount,
                                          int inspectedWorkspaceCount,
                                          int skippedActiveWorkspaceCount,
                                          int skippedRecentWorkspaceCount,
                                          int removedWorkspaceCount,
                                          int failedWorkspaceCount,
                                          List<String> removedWorkspaceNames,
                                          List<String> failedWorkspaceNames,
                                          int inspectedContainerCount,
                                          int activeContainerCount,
                                          int orphanContainerCount,
                                          int failedContainerInspectionCount,
                                          List<String> activeContainerNames,
                                          List<String> orphanContainerNames,
                                          List<String> failedContainerInspectionMessages) {

    public SandboxRuntimeCleanupResult {
        removedWorkspaceNames = removedWorkspaceNames == null ? List.of() : List.copyOf(removedWorkspaceNames);
        failedWorkspaceNames = failedWorkspaceNames == null ? List.of() : List.copyOf(failedWorkspaceNames);
        activeContainerNames = activeContainerNames == null ? List.of() : List.copyOf(activeContainerNames);
        orphanContainerNames = orphanContainerNames == null ? List.of() : List.copyOf(orphanContainerNames);
        failedContainerInspectionMessages = failedContainerInspectionMessages == null
                ? List.of()
                : List.copyOf(failedContainerInspectionMessages);
    }

    public SandboxRuntimeCleanupResult(Instant sweptAt,
                                       int activeSessionCount,
                                       int inspectedWorkspaceCount,
                                       int skippedActiveWorkspaceCount,
                                       int skippedRecentWorkspaceCount,
                                       int removedWorkspaceCount,
                                       int failedWorkspaceCount,
                                       List<String> removedWorkspaceNames,
                                       List<String> failedWorkspaceNames) {
        this(sweptAt,
                activeSessionCount,
                inspectedWorkspaceCount,
                skippedActiveWorkspaceCount,
                skippedRecentWorkspaceCount,
                removedWorkspaceCount,
                failedWorkspaceCount,
                removedWorkspaceNames,
                failedWorkspaceNames,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of());
    }

    public static SandboxRuntimeCleanupResult empty(Instant sweptAt, int activeSessionCount) {
        return new SandboxRuntimeCleanupResult(
                sweptAt,
                Math.max(activeSessionCount, 0),
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of());
    }
}
