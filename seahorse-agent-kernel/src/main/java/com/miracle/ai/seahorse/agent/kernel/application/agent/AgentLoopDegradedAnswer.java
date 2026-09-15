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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;

import java.util.List;
import java.util.Objects;

/**
 * 模型超时等场景下的降级答案渲染：把已完成的工具结果拼成一份可直接返回的 Markdown。
 *
 * <p>从 {@code KernelAgentLoop} 里搬出来是为了让主循环守住它的单构造函数与 700 行约束——
 * 这里是纯文本渲染（步骤列表 → Markdown），没有循环状态，独立成类后也更好测。
 */
final class AgentLoopDegradedAnswer {

    private static final int CONTENT_LIMIT = 1200;

    private AgentLoopDegradedAnswer() {
    }

    static String render(List<AgentStep> steps, RuntimeException timeout) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Agent Run Result\n\n");
        markdown.append("The model timed out while preparing the next turn, ");
        markdown.append("so Seahorse returned the completed tool results instead of leaving the task running.\n\n");
        markdown.append("Timeout: ").append(redact(Objects.requireNonNullElse(timeout.getMessage(), "unknown")))
                .append("\n\n");
        markdown.append("## Completed Tool Results\n\n");
        int count = 0;
        for (AgentStep step : steps) {
            for (int i = 0; i < step.observations().size(); i++) {
                AgentObservation observation = step.observations().get(i);
                if (observation == null || !observation.success()) {
                    continue;
                }
                String toolId = i < step.toolCalls().size()
                        ? step.toolCalls().get(i).toolId()
                        : observation.toolCallId();
                markdown.append("### ").append(++count).append(". ")
                        .append(redact(toolId)).append("\n\n");
                markdown.append(trim(observation.content())).append("\n\n");
            }
        }
        if (count == 0) {
            markdown.append("No successful tool observations were available.\n");
        }
        return markdown.toString().trim();
    }

    private static String trim(String value) {
        String safe = redact(Objects.requireNonNullElse(value, "")).trim();
        return safe.length() <= CONTENT_LIMIT
                ? safe
                : safe.substring(0, CONTENT_LIMIT) + "\n\n...(truncated)";
    }

    /**
     * 工具 id 与工具输出都可能夹带凭证，返回前统一脱敏。
     */
    private static String redact(String value) {
        return CredentialTextRedactor.redact(value);
    }
}
