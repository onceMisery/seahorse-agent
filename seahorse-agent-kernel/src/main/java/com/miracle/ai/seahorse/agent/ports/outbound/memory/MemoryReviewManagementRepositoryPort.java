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
import java.util.Optional;

public interface MemoryReviewManagementRepositoryPort extends MemoryReviewCandidatePort {

    MemoryReviewPage pageReviewCandidates(MemoryReviewQuery query);

    Optional<MemoryReviewRecord> findReviewItem(String candidateId);

    MemoryReviewRecord applyReviewDecision(MemoryReviewDecision decision);

    static MemoryReviewManagementRepositoryPort empty() {
        return new MemoryReviewManagementRepositoryPort() {
            @Override
            public void save(MemoryReviewCandidate candidate) {
            }

            @Override
            public MemoryReviewPage pageReviewCandidates(MemoryReviewQuery query) {
                return MemoryReviewPage.empty(query.current(), query.size());
            }

            @Override
            public Optional<MemoryReviewRecord> findReviewItem(String candidateId) {
                return Optional.empty();
            }

            @Override
            public MemoryReviewRecord applyReviewDecision(MemoryReviewDecision decision) {
                throw new IllegalArgumentException("memory review candidate not found: " + decision.candidateId());
            }
        };
    }

    default List<MemoryReviewRecord> listPending(String tenantId, String userId, int limit) {
        return pageReviewCandidates(new MemoryReviewQuery(tenantId, userId, MemoryReviewStatus.PENDING, 1, limit))
                .records();
    }
}
