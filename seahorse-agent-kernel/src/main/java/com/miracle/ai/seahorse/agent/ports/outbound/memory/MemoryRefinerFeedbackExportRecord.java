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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MemoryRefinerFeedbackExportRecord(
        String sampleId,
        String candidateId,
        String feedbackType,
        Map<String, Object> promptInput,
        Map<String, Object> rejectedOutput,
        Map<String, Object> chosenOutput,
        Map<String, Object> metadata,
        Instant createdAt
) {

    public MemoryRefinerFeedbackExportRecord {
        sampleId = normalize(sampleId);
        candidateId = normalize(candidateId);
        feedbackType = normalize(feedbackType);
        promptInput = Map.copyOf(Objects.requireNonNullElse(promptInput, Map.of()));
        rejectedOutput = Map.copyOf(Objects.requireNonNullElse(rejectedOutput, Map.of()));
        chosenOutput = Map.copyOf(Objects.requireNonNullElse(chosenOutput, Map.of()));
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
