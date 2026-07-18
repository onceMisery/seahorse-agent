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
import java.time.Duration;
import java.util.Objects;

public record SandboxRuntimeNodeMaintenanceStatus(String nodeId,
                                                   boolean operatorDraining,
                                                   int persistedActiveSessionCount,
                                                   int pendingReservationCount,
                                                   boolean createOperationTrackingAvailable,
                                                   int inFlightCreateOperationCount,
                                                   Instant drainRequestedAt,
                                                  Instant stabilizationDeadline,
                                                  boolean stabilizationElapsed,
                                                  boolean maintenanceReady,
                                                  Instant checkedAt) {

    public static final Duration DRAIN_STABILIZATION_WINDOW = Duration.ofMinutes(10);

    public SandboxRuntimeNodeMaintenanceStatus {
        nodeId = requireText(nodeId, "nodeId must not be blank");
        persistedActiveSessionCount = Math.max(persistedActiveSessionCount, 0);
        pendingReservationCount = Math.max(pendingReservationCount, 0);
        inFlightCreateOperationCount = Math.max(inFlightCreateOperationCount, 0);
        checkedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        if (operatorDraining) {
            drainRequestedAt = Objects.requireNonNull(
                    drainRequestedAt,
                    "drainRequestedAt must not be null while node is draining");
            stabilizationDeadline = drainRequestedAt.plus(DRAIN_STABILIZATION_WINDOW);
            stabilizationElapsed = !checkedAt.isBefore(stabilizationDeadline);
        } else {
            drainRequestedAt = null;
            stabilizationDeadline = null;
            stabilizationElapsed = false;
        }
        maintenanceReady = operatorDraining
                && persistedActiveSessionCount == 0
                && pendingReservationCount == 0
                && createOperationTrackingAvailable
                && inFlightCreateOperationCount == 0
                && stabilizationElapsed;
    }

    public SandboxRuntimeNodeMaintenanceStatus(String nodeId,
                                               boolean operatorDraining,
                                               int persistedActiveSessionCount,
                                               int pendingReservationCount,
                                               boolean createOperationTrackingAvailable,
                                               int inFlightCreateOperationCount,
                                               Instant drainRequestedAt,
                                               Instant checkedAt) {
        this(nodeId,
                operatorDraining,
                persistedActiveSessionCount,
                pendingReservationCount,
                createOperationTrackingAvailable,
                inFlightCreateOperationCount,
                drainRequestedAt,
                null,
                false,
                false,
                checkedAt);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
