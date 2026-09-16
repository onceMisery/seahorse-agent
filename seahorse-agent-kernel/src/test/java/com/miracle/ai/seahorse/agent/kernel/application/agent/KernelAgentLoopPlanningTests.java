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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.PlanStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlan;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.policy.TaskPlanningPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务规划（P3-1）与主循环的集成：默认路径必须完全不变，开启后才产生规划。
 */
class KernelAgentLoopPlanningTests {

    private static final String GOAL = "1. 收集 Redis 资料 2. 撰写对比报告";
    /** 第 2 步带"同时"，规划器会让它与第 1 步落在同一个波次。 */
    private static final String PARALLEL_GOAL = "1. 收集资料 2. 同时整理社区 benchmark 3. 汇总";

    @Test
    void shouldNotPlanWhenPolicyIsDisabledByDefault() {
        RecordingModel model = new RecordingModel("done");
        RecordingRunStepRecorder recorder = new RecordingRunStepRecorder();

        AgentLoopResult result = run(model, recorder, TaskPlanningPolicy.defaults());

        assertEquals("done", result.finalAnswer());
        assertTrue(recorder.plans.isEmpty(), "默认关闭时不应产生任何规划记录");
        assertFalse(model.allContents().stream().anyMatch(c -> c.contains("# Task plan")));
    }

    @Test
    void shouldInjectPlanOutlineWhenPlanningIsEnabled() {
        RecordingModel model = new RecordingModel("done");
        RecordingRunStepRecorder recorder = new RecordingRunStepRecorder();

        AgentLoopResult result = run(model, recorder, TaskPlanningPolicy.builder().enabled(true).build());

        assertEquals("done", result.finalAnswer());
        assertFalse(recorder.plans.isEmpty());
        TaskPlan first = recorder.plans.get(0);
        assertEquals(2, first.stepCount());
        String systemContent = model.systemContents();
        assertTrue(systemContent.contains("# Task plan (2 steps)"), systemContent);
        assertTrue(systemContent.contains("收集 Redis 资料"), systemContent);
        assertTrue(systemContent.contains("撰写对比报告"), systemContent);
    }

    @Test
    void shouldKeepOutlineOutOfThePromptWhenInjectionIsDisabled() {
        RecordingModel model = new RecordingModel("done");
        RecordingRunStepRecorder recorder = new RecordingRunStepRecorder();

        run(model, recorder, TaskPlanningPolicy.builder()
                .enabled(true)
                .includeOutlineInPrompt(false)
                .build());

        assertFalse(recorder.plans.isEmpty(), "不注入提示词不代表不规划");
        assertFalse(model.allContents().stream().anyMatch(c -> c.contains("# Task plan")));
    }

    @Test
    void shouldAdvancePlanStepStatusAsTheLoopProgresses() {
        RecordingModel model = new RecordingModel("done");
        RecordingRunStepRecorder recorder = new RecordingRunStepRecorder();

        run(model, recorder, TaskPlanningPolicy.builder().enabled(true).build());

        TaskPlan last = recorder.lastPlan();
        assertNotNull(last);
        assertEquals(PlanStepStatus.COMPLETED, last.stepAt(0).status(),
                "循环产出最终答案后，对应步骤应被标记为完成");
    }

    /**
     * 并行步骤（P3-3）：波次内互不依赖的步骤应各自发起一次带聚焦消息的模型轮次。
     *
     * <p>目标里第 2 步带"同时"，确定性规划器会让它继承第 1 步的依赖（此处为空），
     * 于是第 1、2 步落在同一个波次里。
     */
    @Test
    void shouldIssueOneFocusedModelTurnPerStepInAParallelWave() {
        RecordingModel model = new RecordingModel("done");
        RecordingRunStepRecorder recorder = new RecordingRunStepRecorder();

        runParallel(model, recorder, true, 2);

        assertEquals(2, model.requestCount(), "同一波次的两个步骤应各自拿到一次模型轮次");
        assertTrue(model.systemContents().contains("收集资料"));
        assertTrue(model.systemContents().contains("同时整理社区 benchmark"));
        assertTrue(model.systemContents().contains("现在只推进规划中的这一步"));
    }

    @Test
    void shouldKeepSerialTurnsWhenParallelStepsAreDisabled() {
        RecordingModel model = new RecordingModel("done");
        RecordingRunStepRecorder recorder = new RecordingRunStepRecorder();

        runParallel(model, recorder, false, 2);

        assertEquals(1, model.requestCount(), "关闭并行时行为必须与引入前一致：一次轮次就结束");
        assertFalse(model.systemContents().contains("现在只推进规划中的这一步"));
    }

    private static AgentLoopResult run(RecordingModel model,
                                       RecordingRunStepRecorder recorder,
                                       TaskPlanningPolicy policy) {
        KernelAgentLoopOptions options = KernelAgentLoopOptions.builder()
                .maxSteps(3)
                .taskPlanning(policy)
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
                recorder,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        return loop.execute(AgentLoopRequest.builder()
                .question(GOAL)
                .runId("run-planning-1")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(3)
                .build());
    }

    private static AgentLoopResult runParallel(RecordingModel model,
                                               RecordingRunStepRecorder recorder,
                                               boolean parallelSteps,
                                               int maxParallelSteps) {
        KernelAgentLoopOptions options = KernelAgentLoopOptions.builder()
                .maxSteps(4)
                .taskPlanning(TaskPlanningPolicy.builder().enabled(true).maxSteps(3).build())
                .parallelSteps(parallelSteps)
                .maxParallelSteps(maxParallelSteps)
                .contextEnvelope(ModelContextEnvelopeOptions.defaults()
                        .withMode(ModelContextEnvelopeOptions.Mode.DISABLED))
                .build();
        KernelAgentLoop loop = new KernelAgentLoop(new AgentLoopDependencies(
                model, ToolRegistryPort.empty(), null, options, null, null, recorder,
                null, null, null, null, null, null, null, null));

        return loop.execute(AgentLoopRequest.builder()
                .question(PARALLEL_GOAL)
                .runId("run-planning-parallel")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(4)
                .build());
    }

    private static final class RecordingModel implements StreamingChatModelPort {

        private final String answer;
        // 并行波次会从多个线程记录请求，必须是线程安全的容器
        private final List<List<ChatMessage>> requests = new CopyOnWriteArrayList<>();

        private RecordingModel(String answer) {
            this.answer = answer;
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
            requests.add(request.getMessages());
            callback.onContent(answer);
            toolCallCollector.onToolCalls(List.of());
            callback.onComplete();
            return () -> {
            };
        }

        private int requestCount() {
            return requests.size();
        }

        private List<String> allContents() {
            List<String> contents = new ArrayList<>();
            for (List<ChatMessage> messages : requests) {
                for (ChatMessage message : messages) {
                    contents.add(message.getContent());
                }
            }
            return contents;
        }

        private String systemContents() {
            StringBuilder builder = new StringBuilder();
            for (List<ChatMessage> messages : requests) {
                for (ChatMessage message : messages) {
                    if (message.getRole() == ChatRole.SYSTEM) {
                        builder.append(message.getContent()).append('\n');
                    }
                }
            }
            return builder.toString();
        }
    }

    private static final class RecordingRunStepRecorder implements AgentRunStepRecorder {

        private final List<TaskPlan> plans = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void recordModelTurn(String runId, String inputJson, String outputJson, Throwable error) {
        }

        @Override
        public void recordToolCall(String runId, AgentToolCall toolCall, AgentObservation observation) {
        }

        @Override
        public void recordTaskPlan(String runId, TaskPlan plan, Throwable error) {
            if (error != null) {
                errors.add(error);
                return;
            }
            if (plan != null) {
                plans.add(plan);
            }
        }

        private TaskPlan lastPlan() {
            return plans.isEmpty() ? null : plans.get(plans.size() - 1);
        }
    }
}
