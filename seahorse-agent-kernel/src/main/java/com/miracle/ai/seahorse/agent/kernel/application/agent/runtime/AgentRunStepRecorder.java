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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlan;

import java.util.List;

public interface AgentRunStepRecorder {

    void recordModelTurn(String runId, String inputJson, String outputJson, Throwable error);

    void recordToolCall(String runId, AgentToolCall toolCall, AgentObservation observation);

    /**
     * 记录一次任务规划结果或规划失败。
     *
     * <p>默认是空实现：既有的 {@link #noop()} 与所有已存在的实现都不需要为此改动，
     * 只有真正关心规划轨迹的实现才覆写它。规划是可选能力，记录失败不得影响循环本身。
     *
     * @param runId 运行标识
     * @param plan  规划结果，规划失败时为 {@code null}
     * @param error 规划失败原因，成功时为 {@code null}
     */
    default void recordTaskPlan(String runId, TaskPlan plan, Throwable error) {
        // 默认不落库：规划轨迹先由 trace 与日志承载，接入方按需覆写。
    }

    static AgentRunStepRecorder noop() {
        return new AgentRunStepRecorder() {
            @Override
            public void recordModelTurn(String runId, String inputJson, String outputJson, Throwable error) {
            }

            @Override
            public void recordToolCall(String runId, AgentToolCall toolCall, AgentObservation observation) {
            }
        };
    }

    static String modelTurnInput(List<?> messages, List<?> tools) {
        int messageCount = messages == null ? 0 : messages.size();
        int toolCount = tools == null ? 0 : tools.size();
        return "{\"messageCount\":" + messageCount + ",\"toolCount\":" + toolCount + "}";
    }
}
