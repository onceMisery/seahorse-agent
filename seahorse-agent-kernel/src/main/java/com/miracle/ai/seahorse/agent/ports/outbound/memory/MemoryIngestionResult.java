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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MemoryIngestionResult(
        MemoryIngestionStatus status,
        MemoryIngestionAction action,
        List<String> operations,
        String reason,
        Map<String, Object> details
) {

    public MemoryIngestionResult {
        status = Objects.requireNonNullElse(status, MemoryIngestionStatus.IGNORED);
        action = Objects.requireNonNullElse(action, MemoryIngestionAction.IGNORE);
        operations = List.copyOf(Objects.requireNonNullElse(operations, List.of()));
        reason = Objects.requireNonNullElse(reason, "");
        details = Map.copyOf(Objects.requireNonNullElse(details, Map.of()));
    }

    public static MemoryIngestionResult accepted(List<String> operations) {
        return accepted(MemoryIngestionAction.ADD, operations);
    }

    public static MemoryIngestionResult accepted(MemoryIngestionAction action, List<String> operations) {
        return accepted(action, operations, Map.of());
    }

    public static MemoryIngestionResult accepted(MemoryIngestionAction action,
                                                 List<String> operations,
                                                 Map<String, Object> details) {
        return new MemoryIngestionResult(MemoryIngestionStatus.ACCEPTED, action, operations, "", details);
    }

    public static MemoryIngestionResult ignored(String reason) {
        return ignored(reason, Map.of());
    }

    public static MemoryIngestionResult ignored(String reason, Map<String, Object> details) {
        return new MemoryIngestionResult(MemoryIngestionStatus.IGNORED, MemoryIngestionAction.IGNORE,
                List.of(), reason, details);
    }

    public static MemoryIngestionResult rejected(String reason) {
        return rejected(reason, Map.of());
    }

    public static MemoryIngestionResult rejected(String reason, Map<String, Object> details) {
        return new MemoryIngestionResult(MemoryIngestionStatus.REJECTED, MemoryIngestionAction.IGNORE,
                List.of(), reason, details);
    }

    public static MemoryIngestionResult review(String reason) {
        return new MemoryIngestionResult(MemoryIngestionStatus.REJECTED, MemoryIngestionAction.REVIEW,
                List.of(), reason, Map.of());
    }

    public static MemoryIngestionResult failed(String reason) {
        return new MemoryIngestionResult(MemoryIngestionStatus.FAILED, MemoryIngestionAction.IGNORE,
                List.of(), reason, Map.of());
    }
}
