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

package com.miracle.ai.seahorse.agent.kernel.domain.agent;

import java.util.List;
import java.util.Objects;

/**
 * Agent ReAct 循环结果。
 *
 * @param finalAnswer 最终回答；触达 maxSteps 截断时为 fallback 文案
 * @param steps       完整步骤列表（不可变）
 * @param truncated   true 表示因 maxSteps 截断
 */
public record AgentLoopResult(String finalAnswer,
                              List<AgentStep> steps,
                              boolean truncated,
                              AgentLoopExitReason exitReason) {

    public AgentLoopResult(String finalAnswer, List<AgentStep> steps, boolean truncated) {
        this(finalAnswer, steps, truncated,
                truncated ? AgentLoopExitReason.TRUNCATED : AgentLoopExitReason.FINAL_ANSWER);
    }

    public AgentLoopResult {
        steps = steps == null ? List.of() : List.copyOf(steps);
        exitReason = Objects.requireNonNull(exitReason, "exitReason must not be null");
    }
}
