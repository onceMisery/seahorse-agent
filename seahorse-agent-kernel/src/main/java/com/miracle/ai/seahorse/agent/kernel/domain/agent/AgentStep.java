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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Agent 单步事件。两类形态：
 * <ul>
 *     <li>Thought：包含 0..n 个工具调用 + 其对应观察值；非最终。</li>
 *     <li>FinalAnswer：模型给出最终文本回答，循环终止。</li>
 * </ul>
 */
public record AgentStep(String thought,
                        List<AgentToolCall> toolCalls,
                        List<AgentObservation> observations,
                        String finalAnswer) {

    public AgentStep {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    public boolean isFinal() {
        return finalAnswer != null;
    }

    public static AgentStep thought(String thought,
                                    List<AgentToolCall> toolCalls,
                                    List<AgentObservation> observations) {
        return new AgentStep(Objects.requireNonNullElse(thought, ""),
                Objects.requireNonNullElse(toolCalls, List.of()),
                Objects.requireNonNullElse(observations, List.of()),
                null);
    }

    public static AgentStep finalAnswer(String finalAnswer) {
        return new AgentStep(null, Collections.emptyList(), Collections.emptyList(),
                Objects.requireNonNull(finalAnswer, "finalAnswer 不能为空"));
    }
}
