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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the classification used when a human review decision is applied.
 */
public final class MemoryReviewApplyClassificationBuilder {

    private static final String DEFAULT_TARGET_KIND = "FACT";
    private static final String DEFAULT_LAYER = "SHORT_TERM";
    private static final String STATUS_REVIEW_APPLIED = "review_applied";
    private static final String REASON_REVIEW_APPLIED = "memory_review_applied";
    private static final String CAPTURE_SIGNAL = "memory_review_applied";
    private static final String CAPTURE_REASON = "human_review";

    private static final String METADATA_REVIEW_REQUESTED_ACTION = "reviewRequestedAction";
    private static final String METADATA_TARGET_LAYER = "targetLayer";
    private static final String METADATA_CONFIDENCE = "confidence";
    private static final String METADATA_IMPORTANCE = "importance";
    private static final String METADATA_VALUE_SCORE = "valueScore";
    private static final String METADATA_RISK_SCORE = "riskScore";
    private static final String METADATA_SOURCE_MESSAGE_IDS = "sourceMessageIds";

    public MemoryClassificationResult build(MemoryReviewApplyDirective directive, String content) {
        if (directive == null) {
            return null;
        }
        String targetKind = isBlank(directive.targetKind()) ? DEFAULT_TARGET_KIND : directive.targetKind();
        Map<String, Object> metadata = new LinkedHashMap<>(directive.metadata());
        metadata.put("status", STATUS_REVIEW_APPLIED);
        metadata.put(METADATA_REVIEW_REQUESTED_ACTION, directive.requestedAction().name());
        metadata.put(METADATA_TARGET_LAYER, targetLayer(directive.targetLayer()).name());
        metadata.put(METADATA_CONFIDENCE, directive.confidence());
        metadata.put(METADATA_IMPORTANCE, directive.importance());
        metadata.put(METADATA_VALUE_SCORE, directive.valueScore());
        metadata.put(METADATA_RISK_SCORE, directive.riskScore());
        metadata.put(METADATA_SOURCE_MESSAGE_IDS, directive.sourceMessageIds());
        MemoryCaptureDecision decision = MemoryCaptureDecision.refinedAdd(
                content,
                targetKind,
                directive.importance(),
                directive.confidence(),
                directive.valueScore(),
                directive.riskScore(),
                List.of(CAPTURE_SIGNAL),
                List.of(CAPTURE_REASON));
        return MemoryClassificationResult.refinedAdd(decision, new RefinedMemoryDelta(
                directive.requestedAction(),
                targetKind,
                directive.targetKey(),
                REASON_REVIEW_APPLIED,
                metadata));
    }

    public MemorySchemaValidationResult validateTargetLayer(MemoryReviewApplyDirective directive) {
        if (directive == null) {
            return MemorySchemaValidationResult.ok();
        }
        try {
            MemoryLayer parsed = MemoryLayer.valueOf(directive.targetLayer().trim().toUpperCase(Locale.ROOT));
            if (parsed == MemoryLayer.WORKING) {
                return MemorySchemaValidationResult.invalid("invalid_review_target_layer");
            }
            return MemorySchemaValidationResult.ok();
        } catch (IllegalArgumentException ex) {
            return MemorySchemaValidationResult.invalid("invalid_review_target_layer");
        }
    }

    private static MemoryLayer targetLayer(String layer) {
        if (isBlank(layer)) {
            return MemoryLayer.SHORT_TERM;
        }
        try {
            MemoryLayer parsed = MemoryLayer.valueOf(layer.trim().toUpperCase(Locale.ROOT));
            return parsed == MemoryLayer.WORKING ? MemoryLayer.SHORT_TERM : parsed;
        } catch (IllegalArgumentException ex) {
            return MemoryLayer.SHORT_TERM;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
