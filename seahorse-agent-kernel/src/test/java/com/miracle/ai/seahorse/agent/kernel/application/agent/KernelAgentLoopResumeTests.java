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

import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 长时程续跑（P2-4 剩余）：单次调用的步数上限不再是任务的天花板。
 */
class KernelAgentLoopResumeTests {

    private static final String RUN_ID = "run-long-horizon";

    @Test
    void shouldContinueFromTheLastCheckpointInTheNextInvocation() {
        MemoryCheckpoints checkpoints = new MemoryCheckpoints();
        AgentLoopResult first = run(checkpoints, true, new ScriptedModel(
                turn("继续调研", call("call-1")),
                turn("还没结束", call("call-2"))));

        assertTrue(first.truncated(), "单次调用的步数上限仍然生效——它约束的是一次调用的推理预算");
        assertEquals(1, checkpoints.saved.size());
        assertEquals(AgentCheckpointType.STEP_BOUNDARY, checkpoints.saved.get(0).checkpointType());
        assertTrue(checkpoints.saved.get(0).stateJson().contains("\"completedSteps\":1"));

        ScriptedModel secondModel = new ScriptedModel(turn("调研完成", (AgentToolCall) null));
        AgentLoopResult second = run(checkpoints, true, secondModel);

        assertFalse(second.truncated());
        assertEquals("调研完成", second.finalAnswer());
        List<ChatMessage> resumedMessages = secondModel.firstRequestMessages();
        assertTrue(resumedMessages.stream().anyMatch(message -> message.getRole() == ChatRole.TOOL),
                "续跑必须带上一次调用累积的工具观察结果，而不是从空历史重新开始");
        assertTrue(resumedMessages.stream().anyMatch(message ->
                        message.getRole() == ChatRole.ASSISTANT && !message.getToolCalls().isEmpty()),
                "续跑必须恢复上一轮的 assistant tool-call 消息");
        assertEquals(1, resumedMessages.stream()
                .filter(message -> message.getRole() == ChatRole.USER && "长时程目标".equals(message.getContent()))
                .count(), "恢复时不应重复注入用户问题");
    }

    @Test
    void shouldNotTouchTheCheckpointStoreWhenResumeIsDisabled() {
        MemoryCheckpoints checkpoints = new MemoryCheckpoints();

        run(checkpoints, false, new ScriptedModel(turn("继续调研", call("call-1"))));

        assertTrue(checkpoints.saved.isEmpty(), "开关关闭时行为必须与引入续跑能力前完全一致");
    }

    private static AgentLoopResult run(MemoryCheckpoints checkpoints,
                                       boolean checkpointResume,
                                       StreamingChatModelPort model) {
        KernelAgentLoopOptions options = KernelAgentLoopOptions.builder()
                .maxSteps(1)
                .checkpointResume(checkpointResume)
                .contextEnvelope(ModelContextEnvelopeOptions.defaults()
                        .withMode(ModelContextEnvelopeOptions.Mode.DISABLED))
                .build();
        KernelAgentLoop loop = new KernelAgentLoop(new AgentLoopDependencies(
                model,
                ToolRegistryPort.empty(),
                null,
                options,
                null,
                null,
                AgentRunStepRecorder.noop(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                checkpoints));

        return loop.execute(AgentLoopRequest.builder()
                .question("长时程目标")
                .runId(RUN_ID)
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());
    }

    private static Turn turn(String content, AgentToolCall... toolCalls) {
        List<AgentToolCall> calls = new ArrayList<>();
        for (AgentToolCall toolCall : toolCalls) {
            if (toolCall != null) {
                calls.add(toolCall);
            }
        }
        return new Turn(content, calls);
    }

    private static AgentToolCall call(String id) {
        return AgentToolCall.of(id, "web_search", Map.of("q", id));
    }

    private record Turn(String content, List<AgentToolCall> toolCalls) {
    }

    private static final class ScriptedModel implements StreamingChatModelPort {

        private final List<Turn> turns;
        private final List<ChatRequest> requests = new ArrayList<>();
        private int index;

        private ScriptedModel(Turn... turns) {
            this.turns = List.of(turns);
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
            requests.add(request);
            Turn turn = turns.get(Math.min(index++, turns.size() - 1));
            callback.onContent(turn.content());
            toolCallCollector.onToolCalls(turn.toolCalls());
            callback.onComplete();
            return () -> {
            };
        }

        private List<ChatMessage> firstRequestMessages() {
            return requests.isEmpty() ? List.of() : requests.get(0).getMessages();
        }
    }

    private static final class MemoryCheckpoints implements AgentCheckpointRepositoryPort {

        private final List<AgentCheckpoint> saved = new ArrayList<>();

        @Override
        public void save(AgentCheckpoint checkpoint) {
            saved.add(checkpoint);
        }

        @Override
        public Optional<AgentCheckpoint> findLatestByRunId(String runId) {
            return saved.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .max(Comparator.comparingLong(AgentCheckpoint::sequenceNo));
        }

        @Override
        public List<AgentCheckpoint> listByRunId(String runId) {
            return saved.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .sorted(Comparator.comparingLong(AgentCheckpoint::sequenceNo))
                    .toList();
        }
    }
}
