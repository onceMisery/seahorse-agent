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
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelContextWindowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 取消与并行度的前置安全约束。
 *
 * <p>工具执行的并行能力一直存在（{@code maxParallelTools} 分批 + 线程池），默认值为 1 而
 * 退化为串行。这些测试锁定的是"默认值调大之前必须先成立"的性质，因此它们与默认值无关——
 * 无论并行度是 1 还是 4，结论都必须一样。
 */
class AgentLoopToolExecutorCancellationTests {

    private static final String TOOL_ID = "bench_tool";
    private static final String RUN_ID = "run-cancel";

    @Test
    void shouldStopStartingNewBatchesAfterCancellation() {
        CancellingGateway gateway = new CancellingGateway();
        AgentRunControl control = AgentRunControl.direct();
        AgentLoopToolExecutor executor = executor(1, gateway, control);

        List<AgentObservation> observations = executor.executeTools(
                toolCalls(3), List.of(), null, request(), control, TraceRunScope.disabled(), null, noEvents());

        assertEquals(3, observations.size(), "取消后也要为每个工具调用补齐观察结果，不能让消息历史缺项");
        assertEquals(1, gateway.invocations.size(),
                "取消发生在第一批之后，剩下的批次不该再起线程池——否则新线程池会被立刻 shutdownNow，"
                        + "invokeAll 抛 RejectedExecutionException，把'用户取消'伪装成'工具执行失败'");
        // 已经在跑的那个工具无法撤销，它照常返回成功；被取消的是还没开始的批次。
        assertTrue(observations.get(0).success(), "取消发生在工具执行中途，已完成的调用不该被改写成失败");
        assertTrue(observations.get(1).error().contains("cancelled"), "剩余工具要给出取消语义，而不是中断或超时");
        assertTrue(observations.get(2).error().contains("cancelled"));
    }

    @Test
    void shouldExecuteEveryToolWhenNotCancelled() {
        CancellingGateway gateway = new CancellingGateway(false);
        AgentRunControl control = AgentRunControl.direct();
        AgentLoopToolExecutor executor = executor(1, gateway, control);

        List<AgentObservation> observations = executor.executeTools(
                toolCalls(3), List.of(), null, request(), control, TraceRunScope.disabled(), null, noEvents());

        assertEquals(3, gateway.invocations.size());
        assertTrue(observations.stream().allMatch(AgentObservation::success));
    }

    @Test
    void shouldRunWholeBatchConcurrentlyAndKeepCallOrder() {
        CancellingGateway gateway = new CancellingGateway(false);
        AgentRunControl control = AgentRunControl.direct();
        AgentLoopToolExecutor executor = executor(4, gateway, control);

        List<AgentObservation> observations = executor.executeTools(
                toolCalls(4), List.of(), null, request(), control, TraceRunScope.disabled(), null, noEvents());

        assertEquals(4, gateway.invocations.size(), "并行度为 4 时四个工具应落在同一批");
        assertEquals(4, observations.size());
        // 观察结果顺序必须与调用顺序一致，否则回灌到模型上下文的工具消息会对不上 toolCallId
        for (int i = 0; i < 4; i++) {
            assertEquals("call-" + i, observations.get(i).toolCallId());
        }
        assertFalse(control.cancelled());
    }

    private static AgentLoopToolExecutor executor(int maxParallelTools,
                                                  CancellingGateway gateway,
                                                  AgentRunControl control) {
        gateway.bind(control);
        KernelAgentLoopOptions options = KernelAgentLoopOptions.builder()
                .maxParallelTools(maxParallelTools)
                .perToolTimeout(Duration.ofSeconds(5))
                .modelTurnTimeout(Duration.ofSeconds(5))
                .contextEnvelope(ModelContextEnvelopeOptions.defaults()
                        .withMode(ModelContextEnvelopeOptions.Mode.DISABLED))
                .build();
        AgentLoopModelTurns modelTurns = new AgentLoopModelTurns(
                StreamingChatModelPort.noop(),
                ToolRegistryPort.empty(),
                (ContextWeaverPort) (context, budget) -> "",
                new ToolCallParser(),
                Duration.ofSeconds(5),
                TokenCounterPort.approximate(),
                ModelContextWindowPort.fixed(8192, "test"),
                ModelContextEnvelopeOptions.defaults());
        return new AgentLoopToolExecutor(
                options, gateway, KernelRagTraceRecorder.noop(), AgentRunStepRecorder.noop(), modelTurns);
    }

    private static List<AgentToolCall> toolCalls(int count) {
        List<AgentToolCall> calls = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            calls.add(AgentToolCall.of("call-" + i, TOOL_ID, Map.of("q", i)));
        }
        return calls;
    }

    private static AgentLoopRequest request() {
        return AgentLoopRequest.builder()
                .question("benchmark")
                .runId(RUN_ID)
                .allowedToolIds(List.of())
                .samplingOptions(com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions
                        .builder().temperature(0.1D).build())
                .maxSteps(1)
                .build();
    }

    private static AgentLoopToolExecutor.ToolEvents noEvents() {
        return new AgentLoopToolExecutor.ToolEvents() {
            @Override
            public void toolCallStarted(AgentLoopRequest request, AgentToolCall toolCall) {
                // 该测试不关心事件
            }

            @Override
            public void toolCallFinished(AgentLoopRequest request,
                                         AgentToolCall toolCall,
                                         AgentObservation observation) {
                // 该测试不关心事件
            }

            @Override
            public void toolCallWaitingUser(AgentLoopRequest request,
                                            AgentToolCall toolCall,
                                            AgentObservation observation) {
                // 该测试不关心事件
            }

            @Override
            public void skillResourceLoaded(AgentLoopRequest request,
                                            AgentToolCall toolCall,
                                            AgentObservation observation) {
                // 该测试不关心事件
            }
        };
    }

    private static final class CancellingGateway implements ToolGatewayPort {

        private final List<ToolInvocationRequest> invocations = new ArrayList<>();
        private final boolean cancel;
        private AgentRunControl control;

        private CancellingGateway() {
            this(true);
        }

        private CancellingGateway(boolean cancel) {
            this.cancel = cancel;
        }

        private CancellingGateway bind(AgentRunControl control) {
            this.control = control;
            return this;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            invocations.add(request);
            if (cancel && control != null && invocations.size() == 1) {
                control.cancel();
            }
            return ToolInvocationResult.ok("{\"ok\":true}");
        }
    }
}
