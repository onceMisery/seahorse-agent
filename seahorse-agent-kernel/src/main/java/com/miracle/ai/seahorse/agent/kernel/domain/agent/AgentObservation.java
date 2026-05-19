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

/**
 * 工具调用观察值：循环把工具结果回填给 LLM 之前的中间形态。
 */
public record AgentObservation(String toolCallId, boolean success, String content, String error) {

    public AgentObservation {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("AgentObservation.toolCallId 不能为空");
        }
    }

    public static AgentObservation ok(String toolCallId, String content) {
        return new AgentObservation(toolCallId, true, content, null);
    }

    public static AgentObservation failed(String toolCallId, String error) {
        return new AgentObservation(toolCallId, false, null, error);
    }
}
