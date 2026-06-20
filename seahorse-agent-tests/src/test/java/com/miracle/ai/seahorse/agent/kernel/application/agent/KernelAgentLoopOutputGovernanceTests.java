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

import com.miracle.ai.seahorse.agent.kernel.application.agent.output.JsonSchemaOutputValidator;
import com.miracle.ai.seahorse.agent.kernel.application.agent.output.OutputGovernanceService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidationRecordPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 1a 集成测试：KernelAgentLoop 在配置 OutputGovernanceService 时的端到端行为。
 */
class KernelAgentLoopOutputGovernanceTests {

    private static final String JSON_SCHEMA_TITLE_STEPS =
            "{\"type\":\"object\",\"required\":[\"title\",\"steps\"]}";

    @Test
    void blocksFinalAnswerWhenJsonMissingRequiredField() {
        String modelOutput = "{\"title\":\"only title\"}";
        ScriptedSingleAnswerModel model = new ScriptedSingleAnswerModel(modelOutput);
        RecordingObservation observation = new RecordingObservation();
        OutputGovernanceService governance = new OutputGovernanceService(
                List.of(new JsonSchemaOutputValidator()),
                OutputValidationRecordPort.noop(),
                observation);
        KernelAgentLoop loop = newLoopWithGovernance(model, governance);

        AgentLoopResult result = loop.execute(jsonRequest("生成计划", JSON_SCHEMA_TITLE_STEPS));

        assertThat(result.finalAnswer())
                .as("BLOCK 路径应当替换原始内容为治理 fallback 文案")
                .isNotEqualTo(modelOutput);
        assertThat(result.finalAnswer()).contains("blocked by governance");
        assertThat(observation.events).hasSize(2);
        assertThat(observation.events.get(0).name())
                .isEqualTo(OutputGovernanceService.OBSERVATION_VALIDATION_EVENT);
        assertThat(observation.events.get(1).name())
                .isEqualTo(OutputGovernanceService.OBSERVATION_VALIDATION_FAILED_EVENT);
    }

    @Test
    void passesFinalAnswerThroughWhenJsonValid() {
        String modelOutput = "{\"title\":\"plan\",\"steps\":[\"a\"]}";
        ScriptedSingleAnswerModel model = new ScriptedSingleAnswerModel(modelOutput);
        RecordingObservation observation = new RecordingObservation();
        OutputGovernanceService governance = new OutputGovernanceService(
                List.of(new JsonSchemaOutputValidator()),
                OutputValidationRecordPort.noop(),
                observation);
        KernelAgentLoop loop = newLoopWithGovernance(model, governance);

        AgentLoopResult result = loop.execute(jsonRequest("生成计划", JSON_SCHEMA_TITLE_STEPS));

        assertThat(result.finalAnswer()).isEqualTo(modelOutput);
        assertThat(observation.events)
                .extracting(ObservationEvent::name)
                .containsExactly(OutputGovernanceService.OBSERVATION_VALIDATION_EVENT);
        assertThat(observation.events.get(0).attributes())
                .containsEntry(OutputGovernanceService.OBSERVATION_ATTR_DECISION, "PASS");
    }

    @Test
    void preservesOriginalBehaviorWhenGovernanceServiceNotConfigured() {
        String modelOutput = "{\"title\":\"only title\"}";
        ScriptedSingleAnswerModel model = new ScriptedSingleAnswerModel(modelOutput);
        KernelAgentLoop loop = new KernelAgentLoop(new AgentLoopDependencies(
                model,
                ToolRegistryPort.empty(),
                null,
                KernelAgentLoopOptions.defaults(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        AgentLoopResult result = loop.execute(jsonRequest("生成计划", JSON_SCHEMA_TITLE_STEPS));

        assertThat(result.finalAnswer())
                .as("无 governance 配置时应当保持原始内容不变")
                .isEqualTo(modelOutput);
    }

    @Test
    void doesNotApplyValidatorWhenArtifactTypeIsPlainText() {
        String modelOutput = "随便一句中文";
        ScriptedSingleAnswerModel model = new ScriptedSingleAnswerModel(modelOutput);
        RecordingObservation observation = new RecordingObservation();
        OutputGovernanceService governance = new OutputGovernanceService(
                List.of(new JsonSchemaOutputValidator()),
                OutputValidationRecordPort.noop(),
                observation);
        KernelAgentLoop loop = newLoopWithGovernance(model, governance);

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("说一句话")
                .history(List.of(ChatMessage.system("你是助手")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .build());

        assertThat(result.finalAnswer()).isEqualTo(modelOutput);
        assertThat(observation.events).hasSize(1);
        assertThat(observation.events.get(0).attributes())
                .containsEntry(OutputGovernanceService.OBSERVATION_ATTR_DECISION, "PASS")
                .containsEntry(OutputGovernanceService.OBSERVATION_ATTR_ARTIFACT_TYPE, "PLAIN_TEXT");
    }

    private static KernelAgentLoop newLoopWithGovernance(StreamingChatModelPort model,
                                                          OutputGovernanceService governance) {
        return new KernelAgentLoop(new AgentLoopDependencies(
                model,
                ToolRegistryPort.empty(),
                null,
                KernelAgentLoopOptions.defaults(),
                KernelRagTraceRecorder.noop(),
                new DefaultContextWeaver(),
                AgentRunStepRecorder.noop(),
                AgentApprovalWaitHandler.noop(),
                governance,
                null,
                null,
                null));
    }

    private static AgentLoopRequest jsonRequest(String question, String schema) {
        return AgentLoopRequest.builder()
                .question(question)
                .history(List.of(ChatMessage.system("你是助手")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .expectedOutputArtifactType(OutputArtifactType.JSON)
                .expectedOutputSchemaJson(schema)
                .build();
    }

    /**
     * 仅返回一次 final answer 的 ScriptedModel。
     */
    private static final class ScriptedSingleAnswerModel implements StreamingChatModelPort {
        private final String content;

        private ScriptedSingleAnswerModel(String content) {
            this.content = content;
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            callback.onContent(content);
            toolCallCollector.onToolCalls(List.<AgentToolCall>of());
            callback.onComplete();
            return () -> { };
        }
    }

    private static final class RecordingObservation implements ObservationPort {
        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    events.add(event);
                }

                @Override
                public void close() {
                    // no-op
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }
}
