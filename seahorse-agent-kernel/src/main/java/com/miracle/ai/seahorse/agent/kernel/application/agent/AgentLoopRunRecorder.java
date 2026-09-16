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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 运行步骤记录：把模型轮次的输入 / 输出折算成可审计 JSON 并交给 {@link AgentRunStepRecorder}。
 *
 * <p>记账失败一律只记日志。这里写的是观测数据，它出错不应该改变模型执行或工具执行的语义。
 */
final class AgentLoopRunRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(AgentLoopRunRecorder.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentRunStepRecorder runStepRecorder;
    private final AgentLoopModelTurns modelTurns;

    AgentLoopRunRecorder(AgentRunStepRecorder runStepRecorder, AgentLoopModelTurns modelTurns) {
        this.runStepRecorder = Objects.requireNonNull(runStepRecorder, "runStepRecorder must not be null");
        this.modelTurns = Objects.requireNonNull(modelTurns, "modelTurns must not be null");
    }

    void recordModelTurn(AgentLoopRequest request,
                         List<ChatMessage> messages,
                         ModelTurn turn,
                         Set<String> exhaustedToolIds,
                         Throwable error) {
        try {
            ModelContextEnvelopeEvidence evidence = turn == null
                    ? ModelContextEnvelopeTelemetry.from(error)
                    : turn.contextEvidence();
            String inputJson;
            if (evidence != null) {
                inputJson = evidence.toJson();
            } else {
                List<?> tools = List.of();
                try {
                    tools = modelTurns.exposedTools(modelTurns.effectiveAllowedToolIds(request),
                            request.skillRuntimeBlocks(), exhaustedToolIds);
                } catch (RuntimeException registryError) {
                    LOG.warn("Failed to resolve tools while recording model turn: runId={}",
                            request.runId(), registryError);
                }
                inputJson = AgentRunStepRecorder.modelTurnInput(messages, tools);
            }
            runStepRecorder.recordModelTurn(
                    request.runId(),
                    inputJson,
                    turn == null ? null : modelTurnOutputJson(turn),
                    error);
        } catch (RuntimeException recorderError) {
            LOG.warn("Failed to record model turn without changing model execution semantics: runId={}",
                    request.runId(), recorderError);
        }
    }

    private String modelTurnOutputJson(ModelTurn turn) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("content", turn.content());
        output.put("thinkingPresent", !turn.thinking().isBlank());
        output.put("thinkingChars", turn.thinking().codePointCount(0, turn.thinking().length()));
        output.put("toolCallCount", turn.toolCalls().size());
        try {
            return OBJECT_MAPPER.writeValueAsString(output);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("serialize model turn output failed", ex);
        }
    }
}
