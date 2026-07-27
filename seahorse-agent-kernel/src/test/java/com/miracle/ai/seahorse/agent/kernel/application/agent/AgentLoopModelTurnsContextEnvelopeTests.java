/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelContextWindowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelRequestFingerprint;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopModelTurnsContextEnvelopeTests {

    private static final ContextWeaverPort NO_CONTEXT = (context, budget) -> "";

    @Test
    void disabledModeDoesNotInvokeEnvelopeDependencies() {
        AtomicInteger dependencyCalls = new AtomicInteger();
        TokenCounterPort counter = (modelId, text) -> {
            dependencyCalls.incrementAndGet();
            throw new IllegalStateException("must not be called");
        };
        ModelContextWindowPort window = modelId -> {
            dependencyCalls.incrementAndGet();
            throw new IllegalStateException("must not be called");
        };

        ModelTurn turn = modelTurns(ModelContextEnvelopeOptions.Mode.DISABLED, counter, window)
                .requestFinalModelTurn(request(), List.of(ChatMessage.user("question")), AgentRunControl.direct());

        assertEquals("answer", turn.content());
        assertNull(turn.contextEvidence());
        assertEquals(0, dependencyCalls.get());
    }

    @Test
    void observeModeFallsBackToLegacyRequestWhenObservationFails() {
        ModelContextWindowPort failingWindow = modelId -> {
            throw new IllegalStateException("observation unavailable");
        };

        ModelTurn turn = modelTurns(
                ModelContextEnvelopeOptions.Mode.OBSERVE,
                TokenCounterPort.approximate(),
                failingWindow)
                .requestFinalModelTurn(request(), List.of(ChatMessage.user("question")), AgentRunControl.direct());

        assertEquals("answer", turn.content());
        assertEquals("OBSERVE_FALLBACK", turn.contextEvidence().reasonCode());
        assertEquals("OBSERVE_FALLBACK", turn.contextEvidence().decisions().getFirst().kind());
        assertFalse(turn.contextEvidence().toJson().contains("observation unavailable"));
    }

    @Test
    void observeModeMeasuresButDoesNotChangeLegacyPayload() {
        AtomicInteger weaveCalls = new AtomicInteger();
        ContextWeaverPort budgetSensitiveContext = (context, budget) -> {
            weaveCalls.incrementAndGet();
            return "x".repeat(budget.maxChars());
        };
        UsageModelPort modelPort = new UsageModelPort();
        AgentLoopModelTurns turns = modelTurns(
                ModelContextEnvelopeOptions.Mode.OBSERVE,
                TokenCounterPort.approximate(),
                ModelContextWindowPort.fixed(32_768, "test"),
                budgetSensitiveContext,
                modelPort);

        AgentLoopRequest request = request();
        List<ChatMessage> messages = new ArrayList<>(List.of(ChatMessage.user("question")));
        turns.installLegacyRuntimeContext(request, messages);
        ModelTurn turn = turns.requestFinalModelTurn(request, messages, AgentRunControl.direct());

        ChatRequest actualRequest = modelPort.lastRequest.get();
        assertEquals(1, weaveCalls.get());
        assertEquals(List.of(
                ChatMessage.system("x".repeat(4_000)),
                ChatMessage.user("question")), actualRequest.getMessages());
        assertEquals(ModelContextEnvelopeOptions.Mode.OBSERVE, turn.contextEvidence().mode());
    }

    @Test
    void enforceModeAttachesWireFingerprintAndProviderUsage() {
        ModelTurn turn = modelTurns(
                ModelContextEnvelopeOptions.Mode.ENFORCE,
                TokenCounterPort.approximate(),
                ModelContextWindowPort.fixed(32_768, "test"))
                .requestFinalModelTurn(request(), List.of(ChatMessage.user("question")), AgentRunControl.direct());

        assertEquals("sha256:wire", turn.contextEvidence().payloadHash());
        assertEquals("TEST_WIRE_JSON", turn.contextEvidence().payloadHashSource());
        assertEquals(12L, turn.contextEvidence().providerInputTokens());
        assertEquals(5L, turn.contextEvidence().providerOutputTokens());
    }

    @Test
    void providerUnderestimationRaisesTheNextSafetyBufferForTheSameEstimator() {
        UsageModelPort modelPort = new UsageModelPort();
        modelPort.usage.set(new ChatTokenUsage(2_000, 5));
        AgentLoopModelTurns turns = modelTurns(
                ModelContextEnvelopeOptions.Mode.ENFORCE,
                TokenCounterPort.approximate(),
                ModelContextWindowPort.fixed(32_768, "test"),
                NO_CONTEXT,
                modelPort);

        ModelTurn first = turns.requestFinalModelTurn(
                request(), List.of(ChatMessage.user("question")), AgentRunControl.direct());
        ModelTurn second = turns.requestFinalModelTurn(
                request(), List.of(ChatMessage.user("question")), AgentRunControl.direct());

        assertTrue(second.contextEvidence().safetyBuffer() > first.contextEvidence().safetyBuffer());
        assertTrue(second.contextEvidence().effectiveWindow() < first.contextEvidence().effectiveWindow());
    }

    private AgentLoopModelTurns modelTurns(
            ModelContextEnvelopeOptions.Mode mode,
            TokenCounterPort counter,
            ModelContextWindowPort window) {
        return modelTurns(mode, counter, window, NO_CONTEXT, new UsageModelPort());
    }

    private AgentLoopModelTurns modelTurns(
            ModelContextEnvelopeOptions.Mode mode,
            TokenCounterPort counter,
            ModelContextWindowPort window,
            ContextWeaverPort contextWeaver,
            UsageModelPort modelPort) {
        return new AgentLoopModelTurns(
                modelPort,
                ToolRegistryPort.empty(),
                contextWeaver,
                new ToolCallParser(),
                Duration.ofSeconds(1),
                counter,
                window,
                new ModelContextEnvelopeOptions(mode, 1_024, 256, 512, 1_024, 20, 4, 6, 12));
    }

    private AgentLoopRequest request() {
        return AgentLoopRequest.builder()
                .question("question")
                .modelId("test-model")
                .samplingOptions(ChatSamplingOptions.builder().maxTokens(1_024).build())
                .build();
    }

    private static final class UsageModelPort implements StreamingChatModelPort {

        private final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();
        private final AtomicReference<ChatTokenUsage> usage =
                new AtomicReference<>(new ChatTokenUsage(12, 5));

        @Override
        public ModelRequestFingerprint fingerprint(ChatRequest request) {
            return new ModelRequestFingerprint("sha256:wire", "TEST_WIRE_JSON");
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            lastRequest.set(request);
            callback.onContent("answer");
            callback.onUsage(usage.get());
            callback.onComplete();
            return () -> {
            };
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            toolCallCollector.onToolCalls(List.of());
            return streamChat(request, callback);
        }
    }
}
