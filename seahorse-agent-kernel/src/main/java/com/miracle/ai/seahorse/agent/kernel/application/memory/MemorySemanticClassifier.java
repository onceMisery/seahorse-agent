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

import java.util.Optional;

class MemorySemanticClassifier {

    private final MemoryCaptureCandidateExtractor captureCandidateExtractor;
    private final MemoryValueAssessor memoryValueAssessor;

    MemorySemanticClassifier(MemoryCaptureCandidateExtractor captureCandidateExtractor,
                             MemoryValueAssessor memoryValueAssessor) {
        this.captureCandidateExtractor = captureCandidateExtractor;
        this.memoryValueAssessor = memoryValueAssessor;
    }

    MemoryClassificationResult classify(String content) {
        OccupationCorrection correction = OccupationCorrection.extract(content);
        if (correction != null) {
            return MemoryClassificationResult.correction(correction);
        }
        Optional<MemoryCaptureCandidate> candidate = captureCandidateExtractor.extract(content);
        if (candidate.isEmpty()) {
            return MemoryClassificationResult.ignored(captureCandidateExtractor.lastRejectionReason());
        }
        MemoryCaptureDecision decision = memoryValueAssessor.assess(candidate.get());
        if (!decision.accepted()) {
            return MemoryClassificationResult.rejected(decision, String.join(",", decision.reasons()));
        }
        return MemoryClassificationResult.add(decision);
    }
}
