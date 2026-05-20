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
import java.util.Objects;

public record MemoryIngestionResult(
        MemoryIngestionStatus status,
        List<String> operations,
        String reason
) {

    public MemoryIngestionResult {
        status = Objects.requireNonNullElse(status, MemoryIngestionStatus.IGNORED);
        operations = List.copyOf(Objects.requireNonNullElse(operations, List.of()));
        reason = Objects.requireNonNullElse(reason, "");
    }

    public static MemoryIngestionResult accepted(List<String> operations) {
        return new MemoryIngestionResult(MemoryIngestionStatus.ACCEPTED, operations, "");
    }

    public static MemoryIngestionResult ignored(String reason) {
        return new MemoryIngestionResult(MemoryIngestionStatus.IGNORED, List.of(), reason);
    }

    public static MemoryIngestionResult rejected(String reason) {
        return new MemoryIngestionResult(MemoryIngestionStatus.REJECTED, List.of(), reason);
    }

    public static MemoryIngestionResult failed(String reason) {
        return new MemoryIngestionResult(MemoryIngestionStatus.FAILED, List.of(), reason);
    }
}
