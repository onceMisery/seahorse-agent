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

public record SandboxRuntimeHealth(Instant checkedAt,
                                   String runtime,
                                   String engine,
                                   String nodeId,
                                   boolean admissionEnabled,
                                   String status,
                                   boolean engineAvailable,
                                   boolean workspaceAvailable,
                                   long workspaceFreeBytes,
                                   long workspaceMinFreeBytes,
                                   boolean workspaceDiskAvailable,
                                   String workspaceDiskStatus,
                                   int activeSessionCount,
                                   int activeSessionLimit,
                                   int activeSessionRemaining,
                                   boolean activeSessionCapacityAvailable,
                                   String capacityStatus,
                                   int inspectedContainerCount,
                                   int activeContainerCount,
                                   int orphanContainerCount,
                                   int failedContainerInspectionCount,
                                   List<String> activeContainerNames,
                                   List<String> orphanContainerNames,
                                   List<String> browserPrivateNetworkAllowedHosts,
                                   boolean dropAllCapabilities,
                                   boolean noNewPrivileges,
                                   boolean readOnlyRootFilesystem,
                                   long maxSessionFileBytes,
                                   int maxSessionWorkspaceFiles,
                                   List<String> failureMessages,
                                   String ociRuntime) {

    public static final String STATUS_HEALTHY = "HEALTHY";
    public static final String STATUS_DEGRADED = "DEGRADED";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    public static final String STATUS_UNSUPPORTED = "UNSUPPORTED";
    public static final String CAPACITY_UNBOUNDED = "UNBOUNDED";
    public static final String CAPACITY_AVAILABLE = "AVAILABLE";
    public static final String CAPACITY_SATURATED = "SATURATED";
    public static final String DISK_UNBOUNDED = "UNBOUNDED";
    public static final String DISK_AVAILABLE = "AVAILABLE";
    public static final String DISK_LOW = "LOW";
    public static final String DISK_UNKNOWN = "UNKNOWN";

    public SandboxRuntimeHealth {
        runtime = normalize(runtime, "unsupported");
        engine = normalize(engine, "");
        nodeId = normalize(nodeId, "");
        status = normalize(status, STATUS_UNAVAILABLE);
        workspaceFreeBytes = workspaceFreeBytes < 0 ? -1L : workspaceFreeBytes;
        workspaceMinFreeBytes = Math.max(workspaceMinFreeBytes, 0L);
        workspaceDiskStatus = normalize(workspaceDiskStatus, DISK_UNKNOWN);
        activeSessionLimit = Math.max(activeSessionLimit, 0);
        activeSessionRemaining = Math.max(activeSessionRemaining, 0);
        capacityStatus = normalize(capacityStatus, CAPACITY_UNBOUNDED);
        activeContainerNames = activeContainerNames == null ? List.of() : List.copyOf(activeContainerNames);
        orphanContainerNames = orphanContainerNames == null ? List.of() : List.copyOf(orphanContainerNames);
        browserPrivateNetworkAllowedHosts = browserPrivateNetworkAllowedHosts == null
                ? List.of()
                : List.copyOf(browserPrivateNetworkAllowedHosts);
        maxSessionFileBytes = Math.max(maxSessionFileBytes, 0L);
        maxSessionWorkspaceFiles = Math.max(maxSessionWorkspaceFiles, 0);
        failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
        ociRuntime = normalize(ociRuntime, "");
    }

    public SandboxRuntimeHealth(Instant checkedAt, String runtime, String engine, String nodeId,
                                boolean admissionEnabled, String status, boolean engineAvailable,
                                boolean workspaceAvailable, long workspaceFreeBytes, long workspaceMinFreeBytes,
                                boolean workspaceDiskAvailable, String workspaceDiskStatus, int activeSessionCount,
                                int activeSessionLimit, int activeSessionRemaining,
                                boolean activeSessionCapacityAvailable, String capacityStatus,
                                int inspectedContainerCount, int activeContainerCount, int orphanContainerCount,
                                int failedContainerInspectionCount, List<String> activeContainerNames,
                                List<String> orphanContainerNames, List<String> browserPrivateNetworkAllowedHosts,
                                boolean dropAllCapabilities, boolean noNewPrivileges, boolean readOnlyRootFilesystem,
                                long maxSessionFileBytes, int maxSessionWorkspaceFiles, List<String> failureMessages) {
        this(checkedAt, runtime, engine, nodeId, admissionEnabled, status, engineAvailable, workspaceAvailable,
                workspaceFreeBytes, workspaceMinFreeBytes, workspaceDiskAvailable, workspaceDiskStatus,
                activeSessionCount, activeSessionLimit, activeSessionRemaining, activeSessionCapacityAvailable,
                capacityStatus, inspectedContainerCount, activeContainerCount, orphanContainerCount,
                failedContainerInspectionCount, activeContainerNames, orphanContainerNames,
                browserPrivateNetworkAllowedHosts, dropAllCapabilities, noNewPrivileges, readOnlyRootFilesystem,
                maxSessionFileBytes, maxSessionWorkspaceFiles, failureMessages, "");
    }

    public SandboxRuntimeHealth(Instant checkedAt, String runtime, String engine, String status,
                                boolean engineAvailable, boolean workspaceAvailable, long workspaceFreeBytes,
                                long workspaceMinFreeBytes, boolean workspaceDiskAvailable, String workspaceDiskStatus,
                                int activeSessionCount, int activeSessionLimit, int activeSessionRemaining,
                                boolean activeSessionCapacityAvailable, String capacityStatus, int inspectedContainerCount,
                                int activeContainerCount, int orphanContainerCount, int failedContainerInspectionCount,
                                List<String> activeContainerNames, List<String> orphanContainerNames,
                                List<String> browserPrivateNetworkAllowedHosts, boolean dropAllCapabilities,
                                boolean noNewPrivileges, boolean readOnlyRootFilesystem, long maxSessionFileBytes,
                                int maxSessionWorkspaceFiles, List<String> failureMessages) {
        this(checkedAt, runtime, engine, "", true, status, engineAvailable, workspaceAvailable, workspaceFreeBytes,
                workspaceMinFreeBytes, workspaceDiskAvailable, workspaceDiskStatus, activeSessionCount,
                activeSessionLimit, activeSessionRemaining, activeSessionCapacityAvailable, capacityStatus,
                inspectedContainerCount, activeContainerCount, orphanContainerCount, failedContainerInspectionCount,
                activeContainerNames, orphanContainerNames, browserPrivateNetworkAllowedHosts, dropAllCapabilities,
                noNewPrivileges, readOnlyRootFilesystem, maxSessionFileBytes, maxSessionWorkspaceFiles, failureMessages,
                "");
    }

    public SandboxRuntimeHealth(Instant checkedAt, String runtime, String engine, String nodeId, String status,
                                boolean engineAvailable, boolean workspaceAvailable, long workspaceFreeBytes,
                                long workspaceMinFreeBytes, boolean workspaceDiskAvailable, String workspaceDiskStatus,
                                int activeSessionCount, int activeSessionLimit, int activeSessionRemaining,
                                boolean activeSessionCapacityAvailable, String capacityStatus, int inspectedContainerCount,
                                int activeContainerCount, int orphanContainerCount, int failedContainerInspectionCount,
                                List<String> activeContainerNames, List<String> orphanContainerNames,
                                List<String> browserPrivateNetworkAllowedHosts, boolean dropAllCapabilities,
                                boolean noNewPrivileges, boolean readOnlyRootFilesystem, long maxSessionFileBytes,
                                int maxSessionWorkspaceFiles, List<String> failureMessages) {
        this(checkedAt, runtime, engine, nodeId, true, status, engineAvailable, workspaceAvailable, workspaceFreeBytes,
                workspaceMinFreeBytes, workspaceDiskAvailable, workspaceDiskStatus, activeSessionCount,
                activeSessionLimit, activeSessionRemaining, activeSessionCapacityAvailable, capacityStatus,
                inspectedContainerCount, activeContainerCount, orphanContainerCount, failedContainerInspectionCount,
                activeContainerNames, orphanContainerNames, browserPrivateNetworkAllowedHosts, dropAllCapabilities,
                noNewPrivileges, readOnlyRootFilesystem, maxSessionFileBytes, maxSessionWorkspaceFiles, failureMessages);
    }

    public SandboxRuntimeHealth(Instant checkedAt, String runtime, String engine, String status,
                                boolean engineAvailable, boolean workspaceAvailable, long workspaceFreeBytes,
                                long workspaceMinFreeBytes, boolean workspaceDiskAvailable, String workspaceDiskStatus,
                                int activeSessionCount, int activeSessionLimit, int activeSessionRemaining,
                                boolean activeSessionCapacityAvailable, String capacityStatus, int inspectedContainerCount,
                                int activeContainerCount, int orphanContainerCount, int failedContainerInspectionCount,
                                List<String> activeContainerNames, List<String> orphanContainerNames,
                                List<String> browserPrivateNetworkAllowedHosts, List<String> failureMessages) {
        this(checkedAt, runtime, engine, "", true, status, engineAvailable, workspaceAvailable, workspaceFreeBytes,
                workspaceMinFreeBytes, workspaceDiskAvailable, workspaceDiskStatus, activeSessionCount,
                activeSessionLimit, activeSessionRemaining, activeSessionCapacityAvailable, capacityStatus,
                inspectedContainerCount, activeContainerCount, orphanContainerCount, failedContainerInspectionCount,
                activeContainerNames, orphanContainerNames, browserPrivateNetworkAllowedHosts,
                false, false, false, 0L, 0, failureMessages, "");
    }

    public static SandboxRuntimeHealth unsupported(Instant checkedAt, int activeSessionCount) {
        return new SandboxRuntimeHealth(
                checkedAt,
                "unsupported",
                "",
                "",
                true,
                STATUS_UNSUPPORTED,
                false,
                false,
                -1L,
                0L,
                false,
                DISK_UNKNOWN,
                Math.max(activeSessionCount, 0),
                0,
                0,
                true,
                CAPACITY_UNBOUNDED,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                false,
                0L,
                0,
                List.of("sandbox runtime adapter is unsupported"));
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
