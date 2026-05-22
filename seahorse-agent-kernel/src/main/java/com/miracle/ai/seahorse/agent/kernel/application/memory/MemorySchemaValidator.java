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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;

import java.util.Locale;
import java.util.Map;

class MemorySchemaValidator {

    private static final String METADATA_TARGET_LAYER = "targetLayer";
    private static final String METADATA_TARGET_MEMORY_ID = "targetMemoryId";

    private final MemorySanitizer sanitizer;

    MemorySchemaValidator(MemorySanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    MemorySchemaValidationResult validate(MemoryClassificationResult classification) {
        if (classification == null) {
            return MemorySchemaValidationResult.invalid("empty_classification");
        }
        MemorySchemaValidationResult refinedDeltaValidation = validateRefinedDelta(classification.refinedDelta());
        if (!refinedDeltaValidation.valid()) {
            return refinedDeltaValidation;
        }
        if (classification.action() == MemoryIngestionAction.ADD) {
            MemoryCaptureDecision decision = classification.decision();
            if (decision == null || decision.content().isBlank() || decision.type().isBlank()) {
                return MemorySchemaValidationResult.invalid("invalid_add_operation");
            }
            if (sanitizer.containsSensitiveCredential(decision.content())) {
                return MemorySchemaValidationResult.invalid("sensitive_credential");
            }
        }
        if (classification.action() == MemoryIngestionAction.UPDATE) {
            OccupationCorrection correction = classification.correction();
            if (correction == null || correction.incorrectValue().isBlank() || correction.correctValue().isBlank()) {
                return MemorySchemaValidationResult.invalid("invalid_update_operation");
            }
            if (sanitizer.containsSensitiveCredential(correction.incorrectValue())
                    || sanitizer.containsSensitiveCredential(correction.correctValue())) {
                return MemorySchemaValidationResult.invalid("sensitive_credential");
            }
        }
        return MemorySchemaValidationResult.ok();
    }

    private MemorySchemaValidationResult validateRefinedDelta(RefinedMemoryDelta delta) {
        if (delta == null) {
            return MemorySchemaValidationResult.ok();
        }
        if (requiresMutationTarget(delta.action()) && invalidMutationTarget(delta)) {
            return MemorySchemaValidationResult.invalid("invalid_refiner_target");
        }
        Map<String, Object> metadata = delta.metadata();
        Object targetLayer = metadata.get(METADATA_TARGET_LAYER);
        if (targetLayer == null || targetLayer.toString().isBlank()) {
            return MemorySchemaValidationResult.ok();
        }
        try {
            MemoryLayer layer = MemoryLayer.valueOf(targetLayer.toString().trim().toUpperCase(Locale.ROOT));
            if (layer == MemoryLayer.WORKING) {
                return MemorySchemaValidationResult.invalid("invalid_refiner_target_layer");
            }
            return MemorySchemaValidationResult.ok();
        } catch (IllegalArgumentException ex) {
            return MemorySchemaValidationResult.invalid("invalid_refiner_target_layer");
        }
    }

    private boolean requiresMutationTarget(MemoryIngestionAction action) {
        return action == MemoryIngestionAction.UPDATE || action == MemoryIngestionAction.DELETE;
    }

    private boolean invalidMutationTarget(RefinedMemoryDelta delta) {
        if (delta.targetKind().isBlank()) {
            return true;
        }
        return delta.targetKey().isBlank() && metadataValue(delta.metadata(), METADATA_TARGET_MEMORY_ID).isBlank();
    }

    private String metadataValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }
}
