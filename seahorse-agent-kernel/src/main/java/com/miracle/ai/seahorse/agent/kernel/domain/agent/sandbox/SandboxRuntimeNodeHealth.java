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
import java.util.Locale;
import java.util.Objects;

public record SandboxRuntimeNodeHealth(Instant checkedAt,
                                       String nodeId,
                                       String runtime,
                                       String engine,
                                       String status,
                                       boolean admissionAvailable,
                                       String admissionStatus,
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
                                       int orphanContainerCount,
                                       int failedContainerInspectionCount,
                                       List<String> failureMessages,
                                       String ociRuntime) {

    public static final String ADMISSION_AVAILABLE = "AVAILABLE";
    public static final String ADMISSION_DEGRADED = "DEGRADED";
    public static final String ADMISSION_DISK_LOW = "DISK_LOW";
    public static final String ADMISSION_SATURATED = "SATURATED";
    public static final String ADMISSION_DRAINING = "DRAINING";
    public static final String ADMISSION_UNAVAILABLE = "UNAVAILABLE";

    public SandboxRuntimeNodeHealth {
        nodeId = normalize(nodeId, "local-unsupported");
        runtime = normalize(runtime, "unsupported");
        engine = normalize(engine, "");
        status = normalize(status, SandboxRuntimeHealth.STATUS_UNAVAILABLE);
        admissionStatus = normalize(admissionStatus, ADMISSION_UNAVAILABLE);
        workspaceFreeBytes = workspaceFreeBytes < 0 ? -1L : workspaceFreeBytes;
        workspaceMinFreeBytes = Math.max(workspaceMinFreeBytes, 0L);
        workspaceDiskStatus = normalize(workspaceDiskStatus, SandboxRuntimeHealth.DISK_UNKNOWN);
        activeSessionLimit = Math.max(activeSessionLimit, 0);
        activeSessionRemaining = Math.max(activeSessionRemaining, 0);
        capacityStatus = normalize(capacityStatus, SandboxRuntimeHealth.CAPACITY_UNBOUNDED);
        failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
        ociRuntime = ociRuntime == null ? "" : ociRuntime.trim();
    }

    public SandboxRuntimeNodeHealth(Instant checkedAt, String nodeId, String runtime, String engine,
                                    String status, boolean admissionAvailable, String admissionStatus,
                                    boolean engineAvailable, boolean workspaceAvailable, long workspaceFreeBytes,
                                    long workspaceMinFreeBytes, boolean workspaceDiskAvailable,
                                    String workspaceDiskStatus, int activeSessionCount, int activeSessionLimit,
                                    int activeSessionRemaining, boolean activeSessionCapacityAvailable,
                                    String capacityStatus, int inspectedContainerCount, int orphanContainerCount,
                                    int failedContainerInspectionCount, List<String> failureMessages) {
        this(checkedAt, nodeId, runtime, engine, status, admissionAvailable, admissionStatus, engineAvailable,
                workspaceAvailable, workspaceFreeBytes, workspaceMinFreeBytes, workspaceDiskAvailable,
                workspaceDiskStatus, activeSessionCount, activeSessionLimit, activeSessionRemaining,
                activeSessionCapacityAvailable, capacityStatus, inspectedContainerCount, orphanContainerCount,
                failedContainerInspectionCount, failureMessages, "");
    }

    public static SandboxRuntimeNodeHealth fromHealth(SandboxRuntimeHealth health) {
        SandboxRuntimeHealth safeHealth = Objects.requireNonNull(health, "health must not be null");
        String admissionStatus = admissionStatus(safeHealth);
        boolean admissionAvailable = ADMISSION_AVAILABLE.equals(admissionStatus)
                || ADMISSION_DEGRADED.equals(admissionStatus);
        return new SandboxRuntimeNodeHealth(
                safeHealth.checkedAt(),
                nodeId(safeHealth.nodeId(), safeHealth.runtime(), safeHealth.engine()),
                safeHealth.runtime(),
                safeHealth.engine(),
                safeHealth.status(),
                admissionAvailable,
                admissionStatus,
                safeHealth.engineAvailable(),
                safeHealth.workspaceAvailable(),
                safeHealth.workspaceFreeBytes(),
                safeHealth.workspaceMinFreeBytes(),
                safeHealth.workspaceDiskAvailable(),
                safeHealth.workspaceDiskStatus(),
                safeHealth.activeSessionCount(),
                safeHealth.activeSessionLimit(),
                safeHealth.activeSessionRemaining(),
                safeHealth.activeSessionCapacityAvailable(),
                safeHealth.capacityStatus(),
                safeHealth.inspectedContainerCount(),
                safeHealth.orphanContainerCount(),
                safeHealth.failedContainerInspectionCount(),
                safeHealth.failureMessages(),
                safeHealth.ociRuntime());
    }

    private static String admissionStatus(SandboxRuntimeHealth health) {
        if (SandboxRuntimeHealth.STATUS_UNAVAILABLE.equals(health.status())
                || SandboxRuntimeHealth.STATUS_UNSUPPORTED.equals(health.status())
                || !health.engineAvailable()
                || !health.workspaceAvailable()) {
            return ADMISSION_UNAVAILABLE;
        }
        if (!health.admissionEnabled()) {
            return ADMISSION_DRAINING;
        }
        if (!health.workspaceDiskAvailable()) {
            return ADMISSION_DISK_LOW;
        }
        if (!health.activeSessionCapacityAvailable()) {
            return ADMISSION_SATURATED;
        }
        if (SandboxRuntimeHealth.STATUS_DEGRADED.equals(health.status())
                || health.failedContainerInspectionCount() > 0
                || health.orphanContainerCount() > 0) {
            return ADMISSION_DEGRADED;
        }
        return ADMISSION_AVAILABLE;
    }

    private static String nodeId(String configuredNodeId, String runtime, String engine) {
        if (configuredNodeId != null && !configuredNodeId.trim().isEmpty()) {
            return configuredNodeId.trim();
        }
        String raw = "local-" + normalize(runtime, "unsupported");
        if (engine != null && !engine.trim().isEmpty()) {
            raw += "-" + engine.trim();
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
