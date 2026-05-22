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

package com.miracle.ai.seahorse.agent.ports.inbound.memory;

import java.util.Objects;

public record MemoryMaintenanceTaskOutcome(
        String task,
        String status,
        String reason
) {

    public static final String TASK_COMPACTION = "compaction";
    public static final String TASK_ALIAS = "alias";
    public static final String TASK_GARBAGE_COLLECTION = "garbageCollection";

    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_NOT_REQUESTED = "NOT_REQUESTED";

    public MemoryMaintenanceTaskOutcome {
        task = normalize(task, "");
        status = normalize(status, STATUS_NOT_REQUESTED).toUpperCase();
        reason = normalize(reason, "");
    }

    public static MemoryMaintenanceTaskOutcome succeeded(String task) {
        return new MemoryMaintenanceTaskOutcome(task, STATUS_SUCCEEDED, "");
    }

    public static MemoryMaintenanceTaskOutcome skipped(String task, String reason) {
        return new MemoryMaintenanceTaskOutcome(task, STATUS_SKIPPED, reason);
    }

    public static MemoryMaintenanceTaskOutcome failed(String task, String reason) {
        return new MemoryMaintenanceTaskOutcome(task, STATUS_FAILED, reason);
    }

    public static MemoryMaintenanceTaskOutcome notRequested(String task) {
        return new MemoryMaintenanceTaskOutcome(task, STATUS_NOT_REQUESTED, "");
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }
}
