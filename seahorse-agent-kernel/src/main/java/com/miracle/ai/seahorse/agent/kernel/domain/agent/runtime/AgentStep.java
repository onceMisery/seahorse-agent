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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime;

import java.time.Instant;
import java.util.Objects;

/**
 * AgentRun 内的一条执行步骤。
 *
 * @param stepId       步骤 ID
 * @param runId        所属 AgentRun ID
 * @param stepNo       run 内递增序号，用于还原执行顺序
 * @param stepType     步骤类型，如模型轮次或工具调用
 * @param status       步骤状态
 * @param inputJson    步骤输入快照 JSON
 * @param outputJson   步骤输出快照 JSON
 * @param errorCode    失败原因码
 * @param errorMessage 失败原因说明
 * @param startedAt    开始时间
 * @param finishedAt   结束时间；运行中步骤可为空
 */
public record AgentStep(String stepId,
                        String runId,
                        int stepNo,
                        AgentStepType stepType,
                        AgentStepStatus status,
                        String inputJson,
                        String outputJson,
                        String errorCode,
                        String errorMessage,
                        Instant startedAt,
                        Instant finishedAt) {

    public AgentStep {
        stepId = requireText(stepId, "stepId 不能为空");
        runId = requireText(runId, "runId 不能为空");
        if (stepNo <= 0) {
            throw new IllegalArgumentException("stepNo 必须大于 0");
        }
        stepType = Objects.requireNonNull(stepType, "stepType 不能为空");
        status = Objects.requireNonNull(status, "status 不能为空");
        inputJson = trimToNull(inputJson);
        outputJson = trimToNull(outputJson);
        errorCode = trimToNull(errorCode);
        errorMessage = trimToNull(errorMessage);
        startedAt = Objects.requireNonNull(startedAt, "startedAt 不能为空");
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
