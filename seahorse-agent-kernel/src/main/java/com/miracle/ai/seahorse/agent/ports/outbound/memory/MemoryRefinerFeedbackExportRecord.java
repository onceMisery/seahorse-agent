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
import java.util.LinkedHashMap;
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

    private static final String FEEDBACK_TYPE_APPROVE = "APPROVE";
    private static final String FEEDBACK_TYPE_MODIFY = "MODIFY";
    private static final String FEEDBACK_TYPE_REJECT = "REJECT";
    private static final String REFINER_ACTION_ADD = "ADD";
    private static final String REFINER_ACTION_IGNORE = "IGNORE";

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

    public static MemoryRefinerFeedbackExportRecord fromReviewFeedbackSample(MemoryReviewFeedbackSample sample) {
        Objects.requireNonNull(sample, "sample must not be null");
        return new MemoryRefinerFeedbackExportRecord(
                sample.sampleId(),
                sample.candidateId(),
                feedbackType(sample),
                promptInput(sample),
                rejectedOutput(sample),
                chosenOutput(sample),
                feedbackMetadata(sample),
                sample.createdAt());
    }

    private static String feedbackType(MemoryReviewFeedbackSample sample) {
        if (sample.reviewStatus() == MemoryReviewStatus.REJECTED) {
            return FEEDBACK_TYPE_REJECT;
        }
        if (!Objects.equals(sample.rejectedContent(), sample.chosenContent())
                || !sample.chosenMetadata().isEmpty()) {
            return FEEDBACK_TYPE_MODIFY;
        }
        return FEEDBACK_TYPE_APPROVE;
    }

    private static Map<String, Object> promptInput(MemoryReviewFeedbackSample sample) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("tenantId", sample.tenantId());
        input.put("userId", sample.userId());
        input.put("operationId", sample.operationId());
        input.put("candidateId", sample.candidateId());
        input.put("targetLayer", sample.targetLayer());
        input.put("targetKind", sample.targetKind());
        input.put("targetKey", sample.targetKey());
        input.put("sourceMessageIds", sample.sourceMessageIds());
        input.put("reviewComment", sample.reviewComment());
        return input;
    }

    private static Map<String, Object> rejectedOutput(MemoryReviewFeedbackSample sample) {
        return refinerOutput(sample.requestedAction(), sample.rejectedContent(), sample.rejectedMetadata());
    }

    private static Map<String, Object> chosenOutput(MemoryReviewFeedbackSample sample) {
        if (sample.reviewStatus() == MemoryReviewStatus.REJECTED) {
            return refinerOutput(REFINER_ACTION_IGNORE, "", Map.of());
        }
        return refinerOutput(REFINER_ACTION_ADD, sample.chosenContent(), sample.chosenMetadata());
    }

    private static Map<String, Object> refinerOutput(String action, String content, Map<String, Object> metadata) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("action", action);
        output.put("content", Objects.requireNonNullElse(content, ""));
        output.put("metadata", Objects.requireNonNullElse(metadata, Map.of()));
        return output;
    }

    private static Map<String, Object> feedbackMetadata(MemoryReviewFeedbackSample sample) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestedAction", sample.requestedAction());
        metadata.put("reviewStatus", sample.reviewStatus().name());
        metadata.put("reviewerId", sample.reviewerId());
        metadata.put("targetLayer", sample.targetLayer());
        metadata.put("reviewedMemoryId", sample.reviewedMemoryId());
        metadata.put("reviewedLayer", sample.reviewedLayer());
        return metadata;
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
