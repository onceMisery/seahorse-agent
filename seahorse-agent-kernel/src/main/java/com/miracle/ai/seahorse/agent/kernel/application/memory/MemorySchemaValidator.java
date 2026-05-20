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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;

class MemorySchemaValidator {

    private final MemorySanitizer sanitizer;

    MemorySchemaValidator(MemorySanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    MemorySchemaValidationResult validate(MemoryClassificationResult classification) {
        if (classification == null) {
            return MemorySchemaValidationResult.invalid("empty_classification");
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
}
