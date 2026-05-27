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

/**
 * 评测候选记录。来源于用户差评反馈，经人审后可进入 eval dataset。
 */
public record EvalCandidate(
    String candidateId,
    String runId,
    String messageId,
    String userQuery,
    String assistantResponse,
    String feedbackReason,
    EvalCandidateStatus status,
    String reviewerNote,
    Instant createdAt,
    Instant decidedAt
) {

    public EvalCandidate accept(String note, Instant now) {
        return new EvalCandidate(candidateId, runId, messageId, userQuery, assistantResponse,
                feedbackReason, EvalCandidateStatus.ACCEPTED, note, createdAt, now);
    }

    public EvalCandidate reject(String reason, Instant now) {
        return new EvalCandidate(candidateId, runId, messageId, userQuery, assistantResponse,
                feedbackReason, EvalCandidateStatus.REJECTED, reason, createdAt, now);
    }
}
