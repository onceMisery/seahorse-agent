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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.PlanStep;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopParallelTurnsTests {

    private static final List<ChatMessage> BASE = List.of(ChatMessage.user("目标"));

    @Test
    void shouldAppendStepFocusMessageWithoutMutatingTheBaseList() {
        AgentLoopParallelTurns turns = new AgentLoopParallelTurns(2);
        PlanStep step = PlanStep.pending(0, "收集资料", "收集 Redis 官方基准数据", List.of());

        List<ChatMessage> focused = turns.focusedMessages(BASE, step);

        assertEquals(2, focused.size());
        assertEquals(ChatRole.SYSTEM, focused.get(1).getRole());
        assertTrue(focused.get(1).getContent().contains("收集 Redis 官方基准数据"));
        assertEquals(1, BASE.size(), "基础消息必须保持只读，否则并发分支会互相污染");
    }

    @Test
    void shouldRunSingleStepInlineWithoutThreads() {
        AgentLoopParallelTurns turns = new AgentLoopParallelTurns(4);
        AtomicInteger calls = new AtomicInteger();

        List<ModelTurn> result = turns.runTurns(List.of(step(0)), BASE, Set.of(),
                (messages, exhausted) -> {
                    calls.incrementAndGet();
                    return turn("a", List.of());
                });

        assertEquals(1, calls.get());
        assertEquals(1, result.size());
    }

    @Test
    void shouldRunConcurrentlyWhenParallelismIsAvailable() {
        AgentLoopParallelTurns turns = new AgentLoopParallelTurns(2);
        // 两个分支互相等待：如果真的是串行执行，屏障会超时并让用例失败
        CyclicBarrier barrier = new CyclicBarrier(2);

        List<ModelTurn> result = turns.runTurns(List.of(step(0), step(1)), BASE, Set.of(),
                (messages, exhausted) -> {
                    try {
                        barrier.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException | BrokenBarrierException | java.util.concurrent.TimeoutException ex) {
                        throw new IllegalStateException("步骤没有并发执行", ex);
                    }
                    String focus = messages.get(messages.size() - 1).getContent();
                    return turn(focus, List.of());
                });

        assertEquals(2, result.size());
        assertTrue(result.get(0).content().contains("step-0"));
        assertTrue(result.get(1).content().contains("step-1"), "结果必须与输入步骤顺序一一对应");
    }

    @Test
    void shouldStaySequentialWhenParallelismIsOne() {
        AgentLoopParallelTurns turns = new AgentLoopParallelTurns(1);
        CyclicBarrier neverRendezvous = new CyclicBarrier(2);

        List<ModelTurn> result = turns.runTurns(List.of(step(0), step(1)), BASE, Set.of(),
                (messages, exhausted) -> turn("single", List.of()));

        assertEquals(2, result.size());
        assertEquals(2, neverRendezvous.getParties(), "并发度为 1 时不应创建线程");
    }

    @Test
    void shouldPropagateTheOriginalFailureOfAnyStep() {
        AgentLoopParallelTurns turns = new AgentLoopParallelTurns(2);
        IllegalStateException failure = new IllegalStateException("model down");

        RuntimeException thrown = assertThrows(IllegalStateException.class, () ->
                turns.runTurns(List.of(step(0), step(1)), BASE, Set.of(), (messages, exhausted) -> {
                    if (messages.get(messages.size() - 1).getContent().contains("step-1")) {
                        throw failure;
                    }
                    return turn("ok", List.of());
                }));

        assertSame(failure, thrown, "并行不能把失败变成'部分成功'，必须把原始异常抛回主循环");
    }

    @Test
    void shouldMergeToolCallsInWaveOrder() {
        AgentLoopParallelTurns turns = new AgentLoopParallelTurns(2);
        List<ModelTurn> source = List.of(
                turn("first", List.of(call("c1"), call("c2"))),
                turn("second", List.of(call("c3"))));

        ModelTurn merged = turns.merge(source);

        assertEquals(3, merged.toolCalls().size());
        assertEquals("c1", merged.toolCalls().get(0).id());
        assertEquals("c2", merged.toolCalls().get(1).id());
        assertEquals("c3", merged.toolCalls().get(2).id());
        assertEquals("first" + System.lineSeparator() + "second", merged.content());
    }

    @Test
    void shouldReturnTheOnlyTurnUnchangedWhenMergingOne() {
        AgentLoopParallelTurns turns = new AgentLoopParallelTurns(2);
        ModelTurn only = turn("only", List.of());

        assertSame(only, turns.merge(List.of(only)));
    }

    @Test
    void shouldReturnAnEmptyTurnWhenThereIsNothingToMerge() {
        AgentLoopParallelTurns turns = new AgentLoopParallelTurns(2);

        ModelTurn merged = turns.merge(List.of());

        assertEquals(0, merged.toolCalls().size());
        assertTrue(merged.content().isEmpty());
    }

    @Test
    void shouldCapParallelismAtConfiguredValue() {
        assertEquals(1, new AgentLoopParallelTurns(0).maxParallelSteps());
        assertEquals(3, new AgentLoopParallelTurns(3).maxParallelSteps());
    }

    private static PlanStep step(int index) {
        return PlanStep.pending(index, "step-" + index, "instruction-step-" + index, List.of());
    }

    private static ModelTurn turn(String content, List<AgentToolCall> toolCalls) {
        return new ModelTurn(content, "", toolCalls);
    }

    private static AgentToolCall call(String id) {
        return AgentToolCall.of(id, "web_search", Map.of("q", id));
    }
}
