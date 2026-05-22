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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.tool;

import java.time.Instant;
import java.util.Objects;

/**
 * 工具调用完成审计事件。
 *
 * @param invocationId  单次调用审计 ID
 * @param status        最终状态，包含成功、失败、拒绝或审批中断
 * @param resultSummary 工具输出摘要，避免落库完整敏感结果
 * @param errorMessage  失败、拒绝或审批中断原因
 * @param finishedAt    Gateway 完成处理的时间
 */
public record ToolInvocationAuditCompletion(String invocationId,
                                            ToolInvocationStatus status,
                                            String resultSummary,
                                            String errorMessage,
                                            Instant finishedAt) {

    public ToolInvocationAuditCompletion {
        invocationId = requireText(invocationId, "invocationId 不能为空");
        status = Objects.requireNonNull(status, "status 不能为空");
        resultSummary = trimToNull(resultSummary);
        errorMessage = trimToNull(errorMessage);
        finishedAt = Objects.requireNonNull(finishedAt, "finishedAt 不能为空");
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
