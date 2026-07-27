/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelContextWindowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelContextEnvelopeBuilderTests {

    private static final String MODEL_ID = "test-model";
    private static final ContextWeaverPort NO_CONTEXT = (context, budget) -> "";

    @Test
    void largerFixedSkillCostShrinksHistoricalSelection() {
        List<ChatMessage> messages = List.of(
                ChatMessage.user("oldest-history"),
                ChatMessage.assistant("oldest-answer"),
                ChatMessage.user("recent-history"),
                ChatMessage.assistant("recent-answer"),
                ChatMessage.user("current-question"));
        ModelContextEnvelope baseline = builder(exactCounter(), 10_000, options()).build(
                request(null), messages, List.of(), "none");
        int tightWindow = Math.toIntExact(options().defaultOutputReserveTokens()
                + options().safetyBufferTokens()
                + baseline.evidence().fixedCost()
                + baseline.evidence().partitions().get("historicalMessages").tokens());

        ModelContextEnvelope withoutSkill = builder(exactCounter(), tightWindow, options()).build(
                request(null), messages, List.of(), "none");
        ModelContextEnvelope withSkill = builder(exactCounter(), tightWindow, options()).build(
                request("skill"),
                messages, List.of(), "none");

        assertTrue(withSkill.request().getMessages().size() < withoutSkill.request().getMessages().size());
        assertEquals("current-question", withSkill.request().getMessages().getLast().getContent());
        assertTrue(withSkill.evidence().decisions().stream()
                .anyMatch(decision -> "HISTORY_TRUNCATED".equals(decision.kind())));
    }

    @Test
    void truncatesCompletedToolPairAsOneHistoricalUnit() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "lookup", Map.of("query", "old"));
        List<ChatMessage> messages = List.of(
                ChatMessage.assistantToolCalls("calling", List.of(toolCall)),
                ChatMessage.tool("call-1", "tool-result"),
                ChatMessage.user("recent-history"),
                ChatMessage.assistant("recent-answer"),
                ChatMessage.user("current-question"));
        ModelContextEnvelope baseline = builder(exactCounter(), 10_000, options()).build(
                request(null), messages, List.of(), "none");
        ModelContextEnvelope recentOnly = builder(exactCounter(), 10_000, options()).build(
                request(null), messages.subList(2, messages.size()), List.of(), "none");
        long recentTokens = recentOnly.evidence().partitions().get("historicalMessages").tokens();
        int tightWindow = Math.toIntExact(options().defaultOutputReserveTokens()
                + options().safetyBufferTokens()
                + baseline.evidence().fixedCost()
                + recentTokens);

        ModelContextEnvelope envelope = builder(exactCounter(), tightWindow, options()).build(
                request(null), messages, List.of(), "none");

        assertFalse(envelope.request().getMessages().stream().anyMatch(
                message -> message.getRole() == ChatRole.TOOL || message.getToolCalls() != null));
        assertEquals(2, envelope.evidence().decisions().getFirst().affectedMessages());
        assertEquals(List.of("history-message-0", "history-message-1"),
                envelope.evidence().decisions().getFirst().messageRefs());
    }

    @Test
    void failsClosedRatherThanDroppingTheMostRecentCompletedTurn() {
        List<ChatMessage> messages = List.of(
                ChatMessage.user("recent-history-that-does-not-fit"),
                ChatMessage.assistant("recent-answer-that-does-not-fit"),
                ChatMessage.user("current-question"));
        ModelContextEnvelope baseline = builder(exactCounter(), 10_000, options()).build(
                request(null), messages, List.of(), "none");
        int tightWindow = Math.toIntExact(options().defaultOutputReserveTokens()
                + options().safetyBufferTokens()
                + baseline.evidence().fixedCost()
                + baseline.evidence().partitions().get("historicalMessages").tokens() - 1);

        ModelContextEnvelopeException error = assertThrows(
                ModelContextEnvelopeException.class,
                () -> builder(exactCounter(), tightWindow, options()).build(
                        request(null), messages, List.of(), "none"));

        assertEquals(ModelContextEnvelopeBuilder.CONTEXT_BUDGET_EXCEEDED, error.reasonCode());
        assertTrue(error.evidence().partitions().get("historicalMessages").tokens() > 0);
        assertTrue(error.evidence().selectedInputTokens() > error.evidence().effectiveWindow());
    }

    @Test
    void observeModeRecordsCandidateTruncationWithoutChangingHistory() {
        List<ChatMessage> messages = List.of(
                ChatMessage.user("old-history"),
                ChatMessage.assistant("old-answer"),
                ChatMessage.user("recent-history"),
                ChatMessage.assistant("recent-answer"),
                ChatMessage.user("current-question"));
        ModelContextEnvelope baseline = builder(exactCounter(), 10_000, options()).build(
                request(null), messages, List.of(), "none");
        ModelContextEnvelopeOptions observe = new ModelContextEnvelopeOptions(
                ModelContextEnvelopeOptions.Mode.OBSERVE,
                10, 5, 20, 100, 100, 1, 1, 1);
        int tightWindow = Math.toIntExact(observe.defaultOutputReserveTokens()
                + observe.safetyBufferTokens()
                + baseline.evidence().fixedCost()
                + 1);

        ModelContextEnvelope envelope = builder(exactCounter(), tightWindow, observe).build(
                request(null), messages, List.of(), "none");

        assertEquals(messages, envelope.request().getMessages());
        assertTrue(envelope.evidence().decisions().stream()
                .anyMatch(decision -> "HISTORY_TRUNCATION_OBSERVED".equals(decision.kind())));
    }

    @Test
    void payloadHashUsesUnambiguousMessageBoundaries() {
        ModelContextEnvelope oneMessage = builder(exactCounter(), 10_000, options()).build(
                request(null),
                List.of(ChatMessage.user("a|0|\nmessage=ASSISTANT|b|0|")),
                List.of(),
                "none");
        ModelContextEnvelope twoMessages = builder(exactCounter(), 10_000, options()).build(
                request(null),
                List.of(ChatMessage.user("a|0|"), ChatMessage.assistant("b|0|")),
                List.of(),
                "none");

        assertFalse(oneMessage.evidence().payloadHash().equals(twoMessages.evidence().payloadHash()));
    }

    @Test
    void failsClosedWhenFixedCostExceedsEffectiveWindow() {
        ModelContextEnvelopeBuilder builder = builder(exactCounter(), 40, options());

        ModelContextEnvelopeException error = assertThrows(
                ModelContextEnvelopeException.class,
                () -> builder.build(
                        request("large-fixed-skill-body"),
                        List.of(ChatMessage.system("large-system-policy"),
                                ChatMessage.user("current-question")),
                        List.of(new ToolDescriptor("lookup", "Lookup", "description", "{schema}")),
                        "auto"));

        assertEquals(ModelContextEnvelopeBuilder.CONTEXT_BUDGET_EXCEEDED, error.reasonCode());
        assertEquals(ModelContextEnvelopeBuilder.CONTEXT_BUDGET_EXCEEDED, error.evidence().reasonCode());
    }

    @Test
    void failsClosedForDanglingStructuredToolCall() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "lookup", Map.of());

        ModelContextEnvelopeException error = assertThrows(
                ModelContextEnvelopeException.class,
                () -> builder(exactCounter(), 1_000, options()).build(
                        request(null),
                        List.of(ChatMessage.assistantToolCalls("calling", List.of(toolCall)),
                                ChatMessage.user("current-question")),
                        List.of(),
                        "none"));

        assertEquals(ModelContextEnvelopeBuilder.INVALID_TOOL_PAIR, error.reasonCode());
    }

    @Test
    void evidenceContainsOnlySafeMetadataAndFallbackUsesLargerBuffer() {
        String secret = "raw-secret-must-never-be-persisted";
        ToolDescriptor tool = new ToolDescriptor("lookup", "Lookup", secret, "{\"secret\":\"" + secret + "\"}");
        ModelContextEnvelope fallback = builder(fallbackCounter(), 2_000, options()).build(
                request(secret),
                List.of(ChatMessage.system(secret), ChatMessage.user(secret)),
                List.of(tool),
                "auto");
        ModelContextEnvelope exact = builder(exactCounter(), 2_000, options()).build(
                request(secret),
                List.of(ChatMessage.system(secret), ChatMessage.user(secret)),
                List.of(tool),
                "auto");

        String evidenceJson = fallback.evidence().toJson();
        assertFalse(evidenceJson.contains(secret));
        assertTrue(evidenceJson.contains("sha256:"));
        assertTrue(fallback.evidence().safetyBuffer() > exact.evidence().safetyBuffer());
        assertEquals("LOW", fallback.evidence().estimatorConfidence());
    }

    @Test
    void failsClosedWhenSelectedModelHasNoConfiguredWindow() {
        ModelContextEnvelopeBuilder strict = new ModelContextEnvelopeBuilder(
                exactCounter(),
                ModelContextWindowPort.strictConfigured(Map.of(), "test"),
                NO_CONTEXT,
                options(),
                "structured-tool-protocol");

        ModelContextEnvelopeException error = assertThrows(
                ModelContextEnvelopeException.class,
                () -> strict.build(request(null), List.of(ChatMessage.user("question")), List.of(), "none"));

        assertEquals(ModelContextEnvelopeBuilder.CONTEXT_WINDOW_UNKNOWN, error.reasonCode());
        assertEquals(0, error.evidence().contextWindow());
    }

    @Test
    void conservativeFallbackCountsUtf8BytesIncludingWhitespace() {
        TokenCounterPort fallback = TokenCounterPort.approximate();

        assertEquals(6, fallback.countTextTokens(MODEL_ID, "中文"));
        assertEquals(3, fallback.countTextTokens(MODEL_ID, "   "));
        assertEquals(0, fallback.countTextTokens(MODEL_ID, ""));
    }

    @Test
    void restartsTheWholeBudgetDecisionWhenPrimaryCounterFailsMidway() {
        AtomicInteger calls = new AtomicInteger();
        TokenCounterPort failsOnThirdCount = new TokenCounterPort() {
            @Override
            public int countTextTokens(String modelId, String text) {
                if (calls.incrementAndGet() == 3) {
                    throw new IllegalStateException("provider tokenizer unavailable");
                }
                return Math.max(1, text.length() / 8);
            }

            @Override
            public EstimatorMode estimatorMode() {
                return EstimatorMode.EXACT_PROVIDER;
            }

            @Override
            public String estimatorVersion() {
                return "test-provider-v1";
            }
        };
        List<ChatMessage> messages = List.of(
                ChatMessage.user("history-user-" + "x".repeat(80)),
                ChatMessage.assistant("history-assistant-" + "y".repeat(80)),
                ChatMessage.user("current-question"));

        ModelContextEnvelope actual = builder(failsOnThirdCount, 10_000, options()).build(
                request(null), messages, List.of(), "none");
        ModelContextEnvelope conservative = builder(TokenCounterPort.approximate(), 10_000, options()).build(
                request(null), messages, List.of(), "none");

        assertEquals(TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK,
                actual.evidence().estimatorMode());
        assertEquals(conservative.evidence().safetyBuffer(), actual.evidence().safetyBuffer());
        assertEquals(conservative.evidence().fixedCost(), actual.evidence().fixedCost());
        assertEquals(conservative.evidence().historyBudget(), actual.evidence().historyBudget());
        assertEquals(conservative.evidence().selectedInputTokens(), actual.evidence().selectedInputTokens());
        assertEquals(conservative.request().getMessages(), actual.request().getMessages());
    }

    @Test
    void maximumOutputReserveCannotOverflowEffectiveWindow() {
        AgentLoopRequest maximumReserve = AgentLoopRequest.builder()
                .question("current-question")
                .modelId(MODEL_ID)
                .samplingOptions(ChatSamplingOptions.builder().maxTokens(Integer.MAX_VALUE).build())
                .build();

        ModelContextEnvelopeException error = assertThrows(
                ModelContextEnvelopeException.class,
                () -> builder(exactCounter(), Integer.MAX_VALUE, options()).build(
                        maximumReserve, List.of(ChatMessage.user("question")), List.of(), "none"));

        assertEquals(ModelContextEnvelopeBuilder.CONTEXT_BUDGET_EXCEEDED, error.reasonCode());
        assertEquals(-options().safetyBufferTokens(), error.evidence().effectiveWindow());
    }

    @Test
    void defaultOutputReserveIsEnforcedOnTheOutboundRequest() {
        AgentLoopRequest missingLimit = AgentLoopRequest.builder()
                .question("current-question")
                .modelId(MODEL_ID)
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.25D).build())
                .build();

        ModelContextEnvelope envelope = builder(exactCounter(), 10_000, options()).build(
                missingLimit, List.of(ChatMessage.user("question")), List.of(), "none");
        AgentLoopRequest explicitLimit = AgentLoopRequest.builder()
                .question("current-question")
                .modelId(MODEL_ID)
                .samplingOptions(ChatSamplingOptions.builder()
                        .temperature(0.25D)
                        .maxTokens(options().defaultOutputReserveTokens())
                        .build())
                .build();
        ModelContextEnvelope explicitEnvelope = builder(exactCounter(), 10_000, options()).build(
                explicitLimit, List.of(ChatMessage.user("question")), List.of(), "none");

        assertEquals(options().defaultOutputReserveTokens(), envelope.evidence().outputReserve());
        assertEquals(options().defaultOutputReserveTokens(), envelope.request().getMaxTokens());
        assertEquals(0.25D, envelope.request().getTemperature());
        assertEquals(explicitEnvelope.evidence().payloadHash(), envelope.evidence().payloadHash());
    }

    @Test
    void absentRequestedModelResolvesToConfiguredAdapterDefault() {
        AgentLoopRequest defaultModelRequest = AgentLoopRequest.builder()
                .question("current-question")
                .samplingOptions(ChatSamplingOptions.builder().maxTokens(10).build())
                .build();
        ModelContextEnvelopeBuilder defaultModelBuilder = new ModelContextEnvelopeBuilder(
                exactCounter(),
                ModelContextWindowPort.strictConfigured(
                        Map.of("provider-default", 10_000), "test", "provider-default"),
                NO_CONTEXT,
                options(),
                "structured-tool-protocol");

        ModelContextEnvelope envelope = defaultModelBuilder.build(
                defaultModelRequest, List.of(ChatMessage.user("question")), List.of(), "none");

        assertEquals("provider-default", envelope.request().getModelId());
        assertEquals("provider-default", envelope.evidence().modelId());
    }

    private ModelContextEnvelopeBuilder builder(
            TokenCounterPort counter, int contextWindow, ModelContextEnvelopeOptions options) {
        return new ModelContextEnvelopeBuilder(
                counter,
                ModelContextWindowPort.fixed(contextWindow, "test"),
                NO_CONTEXT,
                options,
                "structured-tool-protocol");
    }

    private AgentLoopRequest request(String skillBody) {
        return AgentLoopRequest.builder()
                .question("current-question")
                .modelId(MODEL_ID)
                .samplingOptions(ChatSamplingOptions.builder()
                        .maxTokens(options().defaultOutputReserveTokens())
                        .build())
                .skillRuntimeContext(skillBody)
                .build();
    }

    private ModelContextEnvelopeOptions options() {
        return new ModelContextEnvelopeOptions(
                ModelContextEnvelopeOptions.Mode.ENFORCE,
                10,
                5,
                20,
                100,
                100,
                1,
                1,
                1);
    }

    private TokenCounterPort exactCounter() {
        return new CharacterCounter(TokenCounterPort.EstimatorMode.EXACT_PROVIDER);
    }

    private TokenCounterPort fallbackCounter() {
        return new CharacterCounter(TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK);
    }

    private record CharacterCounter(EstimatorMode estimatorMode) implements TokenCounterPort {
        @Override
        public int countTextTokens(String modelId, String text) {
            return text == null ? 0 : text.codePointCount(0, text.length());
        }

        @Override
        public String estimatorVersion() {
            return "character-counter-test";
        }
    }
}
