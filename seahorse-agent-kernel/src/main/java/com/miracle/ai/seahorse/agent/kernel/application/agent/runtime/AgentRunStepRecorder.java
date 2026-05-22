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

import java.util.List;

public interface AgentRunStepRecorder {

    void recordModelTurn(String runId, String inputJson, String outputJson, Throwable error);

    void recordToolCall(String runId, AgentToolCall toolCall, AgentObservation observation);

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
