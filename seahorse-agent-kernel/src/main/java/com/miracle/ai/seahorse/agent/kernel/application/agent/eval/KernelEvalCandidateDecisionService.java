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

package com.miracle.ai.seahorse.agent.kernel.application.agent.eval;

import java.time.Instant;
import java.util.Objects;

/**
 * 评测候选决策服务。
 *
 * <p>用户差评经人审后进入 eval dataset，用于后续模型或 prompt 调整的回归评测。
 */
public class KernelEvalCandidateDecisionService {

    private final EvalCandidateRepositoryPort candidateRepository;
    private final EvalDatasetRepositoryPort datasetRepository;

    public KernelEvalCandidateDecisionService(EvalCandidateRepositoryPort candidateRepository,
                                              EvalDatasetRepositoryPort datasetRepository) {
        this.candidateRepository = Objects.requireNonNull(candidateRepository);
        this.datasetRepository = Objects.requireNonNull(datasetRepository);
    }

    /**
     * 接受候选，写入 eval dataset。
     */
    public void acceptCandidate(String candidateId, String reviewerNote) {
        EvalCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("candidate not found: " + candidateId));
        candidate = candidate.accept(reviewerNote, Instant.now());
        candidateRepository.save(candidate);
        datasetRepository.addSample(EvalSample.from(candidate));
    }

    /**
     * 拒绝候选。
     */
    public void rejectCandidate(String candidateId, String reason) {
        EvalCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("candidate not found: " + candidateId));
        candidate = candidate.reject(reason, Instant.now());
        candidateRepository.save(candidate);
    }
}
