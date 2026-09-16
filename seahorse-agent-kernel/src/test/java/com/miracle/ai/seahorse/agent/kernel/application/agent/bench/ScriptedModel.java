/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.bench;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 脚本化模型：对任意请求按预设剧本应答，并自己记账。
 *
 * <p><b>它刻意不理解提示词。</b>这是本 harness 的优点（确定性）也是它的边界——
 * LLM 规划器的语义收益、并行的真实延迟收益，用这个桩都测不出来。
 *
 * <p>指标来源说明：脚本桩下 {@code AgentRun.tokenInput/tokenOutput} 不在内核可达范围，
 * 因此 token 用量用"请求数 + 输入输出字符数"近似。这个近似<b>只能用于同桩横向比较</b>，
 * 不能和真实模型的 token 消耗相提并论。
 */
final class ScriptedModel implements StreamingChatModelPort {

    static final String WORK_TOOL_ID = "bench_search";
    private static final String WORKING_CONTENT = "正在推进当前步骤";
    /**
     * 规划请求固定返回的编号分解。
     *
     * <p>第二条刻意带"同时"，命中确定性规划器的并行标记，从而让第二、三条落到同一个波次
     * ——否则所有步骤都是串行依赖，并行臂根本无从触发，测出来"没有并发"是假象。
     */
    private static final String PLANNER_DECOMPOSITION = "1. 收集资料 2. 同时检索补充资料 3. 产出结论";
    /** 让并发分支有时间重叠，用于观测"是否真的并行"；仅测试桩使用。 */
    private static final long CONCURRENCY_WINDOW_MILLIS = 60L;

    private final int workTurns;
    private final String finalAnswer;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();

    private int turnIndex;
    private int requestCount;
    private int plannerCalls;
    private long inputChars;
    private long outputChars;
    private int toolCallCount;

    ScriptedModel(int workTurns, String finalAnswer) {
        this.workTurns = Math.max(0, workTurns);
        this.finalAnswer = finalAnswer == null ? "" : finalAnswer;
    }

    /**
     * 无工具的同步调用在本仓库里只有规划器会走，因此这里固定返回编号分解。
     *
     * <p>若让它抛异常，LLM 规划臂就只会验证"回退路径"，等于没测到规划本身。
     */
    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        plannerCalls++;
        inputChars += requestChars(request);
        outputChars += PLANNER_DECOMPOSITION.length();
        callback.onContent(PLANNER_DECOMPOSITION);
        callback.onComplete();
        return () -> {
        };
    }

    @Override
    public StreamCancellationHandle streamChatWithTools(ChatRequest request,
                                                        StreamCallback callback,
                                                        ToolCallCollector toolCallCollector) {
        requestCount++;
        inputChars += requestChars(request);
        overlapWindow();

        boolean working = turnIndex < workTurns;
        turnIndex++;
        String content = working ? WORKING_CONTENT : finalAnswer;
        List<AgentToolCall> toolCalls = working
                ? List.of(AgentToolCall.of("call-" + turnIndex, WORK_TOOL_ID, Map.of("q", content)))
                : List.of();

        outputChars += content.length();
        toolCallCount += toolCalls.size();
        callback.onContent(content);
        toolCallCollector.onToolCalls(toolCalls);
        callback.onComplete();
        return () -> {
        };
    }

    /**
     * 在飞行计数里停留一小段时间，让并发分支能够重叠。
     *
     * <p>没有这个窗口，模型轮次可能快到根本不发生时间上的重叠，
     * 于是"是否真的并行"就观测不到了。
     */
    private void overlapWindow() {
        int current = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(current, Math::max);
        try {
            Thread.sleep(CONCURRENCY_WINDOW_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private static long requestChars(ChatRequest request) {
        long total = 0L;
        if (request == null || request.getMessages() == null) {
            return total;
        }
        for (ChatMessage message : request.getMessages()) {
            if (message != null && message.getContent() != null) {
                total += message.getContent().length();
            }
        }
        return total;
    }

    int requestCount() {
        return requestCount;
    }

    int plannerCalls() {
        return plannerCalls;
    }

    long inputChars() {
        return inputChars;
    }

    long outputChars() {
        return outputChars;
    }

    int toolCallCount() {
        return toolCallCount;
    }

    /**
     * @return 观测到的最大并发模型轮次数；并行臂应 &gt;= 2，串行臂恒为 1
     */
    int maxConcurrentTurns() {
        return maxInFlight.get();
    }

    List<String> snapshot() {
        return new ArrayList<>(List.of(
                "requests=" + requestCount,
                "toolCalls=" + toolCallCount,
                "inputChars=" + inputChars));
    }
}
