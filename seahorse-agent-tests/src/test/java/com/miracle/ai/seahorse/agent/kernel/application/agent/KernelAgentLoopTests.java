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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task A9 契约测试：KernelAgentLoop 同步 ReAct 核心循环。
 */
class KernelAgentLoopTests {

    private static final ToolDescriptor WEATHER_DESCRIPTOR =
            new ToolDescriptor("weather", "Weather", "查询天气", "{}");
    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void returnsFinalAnswerWhenModelDoesNotRequestTools() {
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("直接回答")));
        KernelAgentLoop loop = new KernelAgentLoop(model, ToolRegistryPort.empty(), KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(defaultRequest());

        assertEquals("直接回答", result.finalAnswer());
        assertFalse(result.truncated());
        assertEquals(1, result.steps().size());
        assertTrue(result.steps().get(0).isFinal());
        assertEquals("直接回答", result.steps().get(0).finalAnswer());
    }

    @Test
    void executesToolCallsAndFeedsObservationsBackInOriginalOrder() {
        AgentToolCall weather = AgentToolCall.of("call-1", "weather", Map.of("city", "Shanghai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("需要查天气", List.of(weather)),
                Turn.finalAnswer("上海 21 度")));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(WEATHER_DESCRIPTOR,
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{\"temp\":21}"));
        KernelAgentLoop loop = new KernelAgentLoop(model, registry, KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(defaultRequest());

        assertEquals("上海 21 度", result.finalAnswer());
        assertFalse(result.truncated());
        assertEquals(2, result.steps().size());
        AgentStep toolStep = result.steps().get(0);
        assertFalse(toolStep.isFinal());
        assertEquals("需要查天气", toolStep.thought());
        assertEquals(List.of(weather), toolStep.toolCalls());
        assertEquals(1, toolStep.observations().size());
        AgentObservation observation = toolStep.observations().get(0);
        assertTrue(observation.success());
        assertEquals("call-1", observation.toolCallId());
        assertEquals("{\"temp\":21}", observation.content());

        ChatRequest secondTurn = model.requests.get(1);
        List<ChatMessage> messages = secondTurn.getMessages();
        assertEquals(ChatRole.ASSISTANT, messages.get(messages.size() - 2).getRole());
        assertEquals(List.of(weather), messages.get(messages.size() - 2).getToolCalls());
        assertEquals(ChatRole.TOOL, messages.get(messages.size() - 1).getRole());
        assertEquals("call-1", messages.get(messages.size() - 1).getToolCallId());
        assertEquals("{\"temp\":21}", messages.get(messages.size() - 1).getContent());
    }

    @Test
    void truncatesWhenModelKeepsRequestingTools() {
        AgentToolCall weather = AgentToolCall.of("call-1", "weather", Map.of());
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("step1", List.of(weather)),
                Turn.toolCalls("step2", List.of(weather)),
                Turn.toolCalls("step3", List.of(weather))));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(WEATHER_DESCRIPTOR, (callId, toolId, arguments) -> ToolInvocationResult.ok("ok"));
        KernelAgentLoop loop = new KernelAgentLoop(model, registry,
                KernelAgentLoopOptions.builder().maxSteps(2).build());

        AgentLoopResult result = loop.execute(defaultRequest(2));

        assertTrue(result.truncated());
        assertTrue(result.finalAnswer().contains("Task step limit reached"));
        assertEquals(2, result.steps().size());
    }

    @Test
    void missingToolBecomesFailedObservationAndLoopContinues() {
        AgentToolCall missing = AgentToolCall.of("call-1", "missing", Map.of());
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("", List.of(missing)),
                Turn.finalAnswer("已降级回答")));
        KernelAgentLoop loop = new KernelAgentLoop(model, ToolRegistryPort.empty(), KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(defaultRequest());

        AgentObservation observation = result.steps().get(0).observations().get(0);
        assertFalse(observation.success());
        assertEquals(ToolPolicyReasonCodes.TOOL_NOT_FOUND, observation.error());
        assertEquals("已降级回答", result.finalAnswer());
    }

    @Test
    void toolExceptionBecomesFailedObservationAndLoopContinues() {
        AgentToolCall weather = AgentToolCall.of("call-1", "weather", Map.of());
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("", List.of(weather)),
                Turn.finalAnswer("已处理失败")));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(WEATHER_DESCRIPTOR, throwingTool("boom"));
        KernelAgentLoop loop = new KernelAgentLoop(model, registry, KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(defaultRequest());

        AgentObservation observation = result.steps().get(0).observations().get(0);
        assertFalse(observation.success());
        assertEquals("boom", observation.error());
        assertEquals("已处理失败", result.finalAnswer());
    }

    @Test
    void largeToolResultIsTruncatedBeforeFeedingBackToModel() {
        AgentToolCall weather = AgentToolCall.of("call-1", "weather", Map.of());
        String largeResult = "x".repeat(9_000);
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("", List.of(weather)),
                Turn.finalAnswer("已处理截断结果")));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(WEATHER_DESCRIPTOR, (callId, toolId, arguments) -> ToolInvocationResult.ok(largeResult));
        KernelAgentLoop loop = new KernelAgentLoop(model, registry, KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(defaultRequest());

        AgentObservation observation = result.steps().get(0).observations().get(0);
        assertTrue(observation.success());
        assertTrue(observation.content().endsWith("...[truncated]"));
        assertTrue(observation.content().length() < largeResult.length());
        List<ChatMessage> secondTurnMessages = model.requests.get(1).getMessages();
        ChatMessage toolMessage = secondTurnMessages.get(secondTurnMessages.size() - 1);
        assertEquals(observation.content(), toolMessage.getContent());
    }

    @Test
    void toolTimeoutBecomesFailedObservationAndLoopContinues() {
        AgentToolCall weather = AgentToolCall.of("call-1", "weather", Map.of());
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("", List.of(weather)),
                Turn.finalAnswer("已按超时处理")));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(WEATHER_DESCRIPTOR, (callId, toolId, arguments) -> {
            sleep(300);
            return ToolInvocationResult.ok("late");
        });
        KernelAgentLoop loop = new KernelAgentLoop(model, registry,
                KernelAgentLoopOptions.builder().perToolTimeout(Duration.ofMillis(50)).build());

        AgentLoopResult result = loop.execute(defaultRequest());

        AgentObservation observation = result.steps().get(0).observations().get(0);
        assertFalse(observation.success());
        assertTrue(observation.error().contains("Tool execution timed out"));
        assertEquals("已按超时处理", result.finalAnswer());
    }

    @Test
    void parallelToolsStillFeedObservationsBackInOriginalOrder() {
        AgentToolCall first = AgentToolCall.of("call-1", "first_tool", Map.of());
        AgentToolCall second = AgentToolCall.of("call-2", "second_tool", Map.of());
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("", List.of(first, second)),
                Turn.finalAnswer("并发完成")));
        CountDownLatch secondStarted = new CountDownLatch(1);
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new ToolDescriptor("first_tool", "First", "第一个工具", "{}"),
                (callId, toolId, arguments) -> await(secondStarted, 200)
                        ? ToolInvocationResult.ok("first")
                        : ToolInvocationResult.failed("second tool did not start concurrently"));
        registry.register(new ToolDescriptor("second_tool", "Second", "第二个工具", "{}"),
                (callId, toolId, arguments) -> {
                    secondStarted.countDown();
                    return ToolInvocationResult.ok("second");
                });
        KernelAgentLoop loop = new KernelAgentLoop(model, registry,
                KernelAgentLoopOptions.builder()
                        .maxParallelTools(2)
                        .perToolTimeout(Duration.ofSeconds(1))
                        .build());

        AgentLoopResult result = loop.execute(defaultRequest());

        List<AgentObservation> observations = result.steps().get(0).observations();
        assertEquals("call-1", observations.get(0).toolCallId());
        assertTrue(observations.get(0).success());
        assertEquals("first", observations.get(0).content());
        assertEquals("call-2", observations.get(1).toolCallId());
        assertTrue(observations.get(1).success());
        assertEquals("second", observations.get(1).content());
    }

    @Test
    void collectorNotInvokedRaisesProtocolException() {
        StreamingChatModelPort badModel = new StreamingChatModelPort() {
            @Override
            public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
                return () -> { };
            }

            @Override
            public StreamCancellationHandle streamChatWithTools(
                    ChatRequest request,
                    StreamCallback callback,
                    ToolCallCollector toolCallCollector) {
                callback.onContent("answer");
                callback.onComplete();
                return () -> { };
            }
        };
        KernelAgentLoop loop = new KernelAgentLoop(badModel, ToolRegistryPort.empty(), KernelAgentLoopOptions.defaults());

        AgentLoopException ex = assertThrows(AgentLoopException.class, () -> loop.execute(defaultRequest()));
        assertTrue(ex.getMessage().contains("collector"));
    }

    @Test
    void collectorInvokedAfterCompleteRaisesProtocolException() {
        StreamingChatModelPort badModel = new StreamingChatModelPort() {
            @Override
            public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
                return () -> { };
            }

            @Override
            public StreamCancellationHandle streamChatWithTools(
                    ChatRequest request,
                    StreamCallback callback,
                    ToolCallCollector toolCallCollector) {
                callback.onContent("answer");
                callback.onComplete();
                toolCallCollector.onToolCalls(List.of());
                return () -> { };
            }
        };
        KernelAgentLoop loop = new KernelAgentLoop(badModel, ToolRegistryPort.empty(), KernelAgentLoopOptions.defaults());

        AgentLoopException ex = assertThrows(AgentLoopException.class, () -> loop.execute(defaultRequest()));
        assertTrue(ex.getMessage().contains("onComplete"));
    }

    @Test
    void streamExecuteEmitsToolPhaseAsThinkingAndFinalAnswerAsContent() {
        AgentToolCall weather = AgentToolCall.of("call-1", "weather", Map.of("city", "Shanghai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("先查天气", List.of(weather)),
                Turn.finalAnswer("上海 21 度")));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(WEATHER_DESCRIPTOR,
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{\"temp\":21}"));
        KernelAgentLoop loop = new KernelAgentLoop(model, registry, KernelAgentLoopOptions.defaults());
        RecordingCallback callback = new RecordingCallback();

        loop.streamExecute(defaultRequest(), callback);

        assertTrue(callback.awaitTerminal(1_000));
        assertEquals(List.of("上海 21 度"), callback.contents);
        assertEquals(1, callback.completeCount);
        assertTrue(callback.thinking.stream().anyMatch(text -> text.contains("先查天气")));
        assertTrue(callback.thinking.stream().anyMatch(text -> text.contains("weather")));
        assertTrue(callback.contents.stream().noneMatch(text -> text.contains("\"arguments\"")));
    }

    @Test
    void streamExecuteCancellationCancelsModelStreamAndSignalsError() {
        BlockingModel model = new BlockingModel();
        KernelAgentLoop loop = new KernelAgentLoop(model, ToolRegistryPort.empty(), KernelAgentLoopOptions.defaults());
        RecordingCallback callback = new RecordingCallback();

        StreamCancellationHandle handle = loop.streamExecute(defaultRequest(), callback);
        assertTrue(await(model.started, 1_000));

        handle.cancel();

        assertTrue(callback.awaitTerminal(1_000));
        assertTrue(model.cancelled.get());
        assertTrue(callback.error instanceof AgentLoopCancelledException);
    }

    @Test
    void rawToolArgumentsBecomeFailedObservationWithoutInvokingTool() {
        AgentToolCall weather = AgentToolCall.of("call-1", "weather", Map.of("_raw", "{bad"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("bad args", List.of(weather)),
                Turn.finalAnswer("handled")));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(WEATHER_DESCRIPTOR, (callId, toolId, arguments) -> {
            throw new AssertionError("tool should not be invoked for raw arguments");
        });
        KernelAgentLoop loop = new KernelAgentLoop(model, registry, KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(defaultRequest());

        AgentObservation observation = result.steps().get(0).observations().get(0);
        assertFalse(observation.success());
        assertTrue(observation.error().contains("arguments"));
    }

    @Test
    void serverInjectedToolScopeOverridesModelSuppliedReservedArguments() {
        AgentToolCall weather = AgentToolCall.of("call-1", "weather", Map.of(
                "_seahorseUserId", "attacker",
                "_seahorseConversationId", "forged",
                "_seahorseQuestion", "forged question"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need scoped tool", List.of(weather)),
                Turn.finalAnswer("handled")));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(WEATHER_DESCRIPTOR, (callId, toolId, arguments) -> {
            assertEquals("admin-user", arguments.get("_seahorseUserId"));
            assertEquals("conversation-a", arguments.get("_seahorseConversationId"));
            assertEquals("真实问题", arguments.get("_seahorseQuestion"));
            return ToolInvocationResult.ok("ok");
        });
        KernelAgentLoop loop = new KernelAgentLoop(model, registry, KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("真实问题")
                .history(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .memoryContext(MemoryContext.builder()
                        .userId("admin-user")
                        .conversationId("conversation-a")
                        .currentQuestion("真实问题")
                        .build())
                .build());

        assertTrue(result.steps().get(0).observations().get(0).success());
    }

    @Test
    void memoryContextIsInjectedIntoFirstModelTurn() {
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("学生")));
        KernelAgentLoop loop = new KernelAgentLoop(model, ToolRegistryPort.empty(), KernelAgentLoopOptions.defaults());

        loop.execute(AgentLoopRequest.builder()
                .question("我的职业是什么")
                .history(List.of(ChatMessage.system("你是助手")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .memoryContext(MemoryContext.builder()
                        .userId("admin-user")
                        .conversationId("conversation-a")
                        .currentQuestion("我的职业是什么")
                        .semanticMemories(List.of(MemoryItem.builder()
                                .content("用户是学生")
                                .build()))
                        .build())
                .build());

        List<ChatMessage> messages = model.requests.get(0).getMessages();
        assertEquals(ChatRole.SYSTEM, messages.get(0).getRole());
        assertTrue(messages.get(0).getContent().contains("用户是学生"));
        assertEquals("我的职业是什么", messages.get(messages.size() - 1).getContent());
    }

    @Test
    void memoryContextIsInjectedThroughConfiguredContextWeaver() {
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("teacher")));
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults(),
                (memoryContext, budget) -> "[woven-memory]\n" + memoryContext.getProfileMemories().get(0).getContent());

        loop.execute(AgentLoopRequest.builder()
                .question("what is my occupation?")
                .history(List.of(ChatMessage.system("system-base")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .memoryContext(MemoryContext.builder()
                        .profileMemories(List.of(MemoryItem.builder().content("teacher").build()))
                        .build())
                .build());

        List<ChatMessage> messages = model.requests.get(0).getMessages();
        assertTrue(messages.get(0).getContent().contains("[woven-memory]"));
        assertTrue(messages.get(0).getContent().contains("teacher"));
    }

    @Test
    void contextPackIsInjectedIntoFirstModelTurnBeforeLegacyMemory() {
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("policy")));
        KernelAgentLoop loop = new KernelAgentLoop(model, ToolRegistryPort.empty(), KernelAgentLoopOptions.defaults());

        loop.execute(AgentLoopRequest.builder()
                .question("what is the refund policy?")
                .history(List.of(ChatMessage.system("system-base")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .contextPack(contextPack("refund approval requires manager signoff"))
                .memoryContext(MemoryContext.builder()
                        .profileMemories(List.of(MemoryItem.builder().content("legacy profile memory").build()))
                        .build())
                .build());

        List<ChatMessage> messages = model.requests.get(0).getMessages();
        assertEquals(ChatRole.SYSTEM, messages.get(0).getRole());
        assertTrue(messages.get(0).getContent().contains("refund approval requires manager signoff"));
        assertTrue(messages.get(0).getContent().contains("aclDecision=decision-1"));
        assertFalse(messages.get(0).getContent().contains("legacy profile memory"));
    }

    private static AgentLoopRequest defaultRequest() {
        return defaultRequest(6);
    }

    private static AgentLoopRequest defaultRequest(int maxSteps) {
        return AgentLoopRequest.builder()
                .question("天气如何")
                .history(List.of(ChatMessage.system("你是助手")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .maxSteps(maxSteps)
                .build();
    }

    private static ContextPack contextPack(String content) {
        return new ContextPack(
                "ctx-1",
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "answer user",
                400,
                List.of(new ContextItem(
                        "ctxi-1",
                        "ctx-1",
                        ContextItemSourceType.RAG_CHUNK,
                        "doc-1",
                        content,
                        null,
                        0.9,
                        0.8,
                        ContextSensitivity.INTERNAL,
                        "decision-1",
                        "{\"docId\":\"policy-1\"}",
                        20,
                        null,
                        NOW)),
                NOW);
    }

    private static ToolPort throwingTool(String message) {
        return (callId, toolId, arguments) -> {
            throw new IllegalStateException(message);
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean await(CountDownLatch latch, long millis) {
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static final class BlockingModel implements StreamingChatModelPort {
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            started.countDown();
            return () -> cancelled.set(true);
        }
    }

    private static final class ScriptedModel implements StreamingChatModelPort {
        private final List<Turn> turns;
        private final List<ChatRequest> requests = new ArrayList<>();
        private int index;

        private ScriptedModel(List<Turn> turns) {
            this.turns = turns;
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
            Turn turn = turns.get(index++);
            if (!turn.content.isBlank()) {
                callback.onContent(turn.content);
            }
            toolCallCollector.onToolCalls(turn.toolCalls);
            callback.onComplete();
            return () -> { };
        }
    }

    private record Turn(String content, List<AgentToolCall> toolCalls) {

        static Turn finalAnswer(String content) {
            return new Turn(content, List.of());
        }

        static Turn toolCalls(String content, List<AgentToolCall> toolCalls) {
            return new Turn(content, toolCalls);
        }
    }

    private static final class RecordingCallback implements StreamCallback {
        private final List<String> contents = new ArrayList<>();
        private final List<String> thinking = new ArrayList<>();
        private final CountDownLatch terminal = new CountDownLatch(1);
        private Throwable error;
        private int completeCount;

        @Override
        public void onContent(String content) {
            contents.add(content);
        }

        @Override
        public void onThinking(String content) {
            thinking.add(content);
        }

        @Override
        public void onComplete() {
            completeCount++;
            terminal.countDown();
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            terminal.countDown();
        }

        private boolean awaitTerminal(long millis) {
            return await(terminal, millis);
        }
    }
}
