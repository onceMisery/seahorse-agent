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

/**
 * 评测数据集样本，从已接受的候选转换而来。
 */
public record EvalSample(
    String sampleId,
    String datasetId,
    String userQuery,
    String expectedResponse,
    String feedbackReason,
    String sourceRunId
) {

    public static EvalSample from(EvalCandidate candidate) {
        return new EvalSample(
                candidate.candidateId(),
                "default",
                candidate.userQuery(),
                candidate.assistantResponse(),
                candidate.feedbackReason(),
                candidate.runId());
    }
}
