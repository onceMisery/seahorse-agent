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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelContextWindow;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelContextWindowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ModelContextEnvelopeBuilder {

    static final String CONTEXT_BUDGET_EXCEEDED = "CONTEXT_BUDGET_EXCEEDED";
    static final String CONTEXT_WINDOW_UNKNOWN = "CONTEXT_WINDOW_UNKNOWN";
    static final String INVALID_TOOL_PAIR = "INVALID_TOOL_PAIR";

    private final TokenCounterPort tokenCounter;
    private final ModelContextWindowPort contextWindowPort;
    private final ContextWeaverPort contextWeaver;
    private final ModelContextEnvelopeOptions options;
    private final String toolProtocol;
    private final ModelContextEnvelopeCalibrator calibrator;

    ModelContextEnvelopeBuilder(TokenCounterPort tokenCounter,
                                ModelContextWindowPort contextWindowPort,
                               ContextWeaverPort contextWeaver,
                               ModelContextEnvelopeOptions options,
                               String toolProtocol) {
        this(tokenCounter, contextWindowPort, contextWeaver, options, toolProtocol,
                new ModelContextEnvelopeCalibrator());
    }

    ModelContextEnvelopeBuilder(TokenCounterPort tokenCounter,
                                ModelContextWindowPort contextWindowPort,
                                ContextWeaverPort contextWeaver,
                                ModelContextEnvelopeOptions options,
                                String toolProtocol,
                                ModelContextEnvelopeCalibrator calibrator) {
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter must not be null");
        this.contextWindowPort = Objects.requireNonNull(contextWindowPort, "contextWindowPort must not be null");
        this.contextWeaver = Objects.requireNonNull(contextWeaver, "contextWeaver must not be null");
        this.options = Objects.requireNonNullElseGet(options, ModelContextEnvelopeOptions::defaults);
        this.toolProtocol = Objects.requireNonNullElse(toolProtocol, "").trim();
        this.calibrator = Objects.requireNonNull(calibrator, "calibrator must not be null");
    }

    ModelContextEnvelope build(AgentLoopRequest request,
                               List<ChatMessage> messages,
                               List<ToolDescriptor> tools,
                               String toolChoice) {
        TokenCounterPort fallback = TokenCounterPort.approximate();
        CountingSession counter = new CountingSession(tokenCounter, fallback);
        try {
            ModelContextEnvelope envelope = buildOnce(request, messages, tools, toolChoice, counter);
            return counter.fallbackActivatedByFailure()
                    ? buildOnce(request, messages, tools, toolChoice,
                            new CountingSession(fallback, fallback))
                    : envelope;
        } catch (RuntimeException ex) {
            if (counter.fallbackActivatedByFailure()) {
                return buildOnce(request, messages, tools, toolChoice,
                        new CountingSession(fallback, fallback));
            }
            throw ex;
        }
    }

    private ModelContextEnvelope buildOnce(
            AgentLoopRequest request,
            List<ChatMessage> messages,
            List<ToolDescriptor> tools,
            String toolChoice,
            CountingSession counter) {
        Objects.requireNonNull(request, "request must not be null");
        List<ChatMessage> rawMessages = new ArrayList<>(Objects.requireNonNullElse(messages, List.of()));
        List<ChatMessage> safeMessages = rawMessages.stream().filter(Objects::nonNull).toList();
        List<ToolDescriptor> safeTools = List.copyOf(Objects.requireNonNullElse(tools, List.of()));
        String modelId = contextWindowPort.resolveModelId(request.modelId());
        ModelContextWindow window = Objects.requireNonNull(
                contextWindowPort.resolve(modelId), "resolved context window must not be null");
        int outputReserve = outputReserve(request);
        ChatSamplingOptions samplingOptions = enforcedSamplingOptions(request, outputReserve);

        ToolPairValidation validation = validateToolPairs(rawMessages);
        String runtimeContext = resolvedRuntimeContext(request);
        String skillBody = Objects.requireNonNullElse(request.skillRuntimeContext(), "").trim();
        String effectiveToolProtocol = safeTools.isEmpty() ? "" : toolProtocol;

        List<ChatMessage> systemMessages = safeMessages.stream()
                .filter(message -> message.getRole() == ChatRole.SYSTEM)
                .toList();
        List<ChatMessage> conversationalMessages = safeMessages.stream()
                .filter(message -> message.getRole() != ChatRole.SYSTEM)
                .toList();
        int currentStart = latestUserIndex(conversationalMessages);
        List<ChatMessage> historical = List.copyOf(conversationalMessages.subList(0, currentStart));
        List<ChatMessage> current = List.copyOf(
                conversationalMessages.subList(currentStart, conversationalMessages.size()));
        List<MessageUnit> historyUnits = completeUnits(counter, modelId, historical);

        String originalSystem = systemMessages.stream()
                .map(message -> Objects.requireNonNullElse(message.getContent(), ""))
                .filter(text -> !text.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        String assembledSystem = joinSections(originalSystem, effectiveToolProtocol, runtimeContext, skillBody);
        List<ChatMessage> fixedMessages = new ArrayList<>();
        if (!assembledSystem.isBlank()) {
            fixedMessages.add(ChatMessage.system(assembledSystem));
        }
        fixedMessages.addAll(current);

        long fixedCost = saturatedAdd(
                countMessages(counter, modelId, fixedMessages),
                countTools(counter, modelId, safeTools));
        int safetyBuffer = safetyBuffer(
                modelId, counter.estimatorMode(), counter.estimatorVersion());
        long effectiveWindow = (long) window.tokens() - outputReserve - safetyBuffer;
        long historyBudget = Math.max(0L, effectiveWindow - fixedCost);
        List<ModelContextEnvelopeEvidence.Decision> decisions = new ArrayList<>();

        if (!window.resolved()) {
            ModelContextEnvelopeEvidence evidence = evidence(
                    request, modelId, counter, window, outputReserve, safetyBuffer, effectiveWindow,
                    fixedCost, historyBudget, 0L, safeMessages.size(), safeTools.size(), Map.of(),
                    List.of(), List.of(new ModelContextEnvelopeEvidence.Decision(
                            "FAIL_CLOSED", CONTEXT_WINDOW_UNKNOWN, safeMessages.size())),
                    CONTEXT_WINDOW_UNKNOWN,
                    payloadHash(modelId, samplingOptions, safeMessages, safeTools, toolChoice));
            if (options.mode() == ModelContextEnvelopeOptions.Mode.ENFORCE) {
                throw new ModelContextEnvelopeException(
                        CONTEXT_WINDOW_UNKNOWN,
                        "the selected model has no explicit context-window capability",
                        evidence);
            }
            decisions.add(new ModelContextEnvelopeEvidence.Decision(
                    "OBSERVED", CONTEXT_WINDOW_UNKNOWN, safeMessages.size()));
        }
        if (!validation.valid() && options.mode() == ModelContextEnvelopeOptions.Mode.ENFORCE) {
            ModelContextEnvelopeEvidence evidence = evidence(
                    request, modelId, counter, window, outputReserve, safetyBuffer, effectiveWindow,
                    fixedCost, historyBudget, 0L, safeMessages.size(), safeTools.size(), Map.of(),
                    List.of(), List.of(new ModelContextEnvelopeEvidence.Decision(
                            "FAIL_CLOSED", INVALID_TOOL_PAIR, validation.affectedMessages())),
                    INVALID_TOOL_PAIR,
                    payloadHash(modelId, samplingOptions, safeMessages, safeTools, toolChoice));
            throw new ModelContextEnvelopeException(
                    INVALID_TOOL_PAIR, "structured tool-call history is incomplete or orphaned", evidence);
        }
        if (!validation.valid()) {
            decisions.add(new ModelContextEnvelopeEvidence.Decision(
                    "OBSERVED", INVALID_TOOL_PAIR, validation.affectedMessages()));
        }

        List<MessageUnit> budgetedUnits = selectHistory(historyUnits, historyBudget);
        boolean recentTurnExceedsBudget = options.mode() == ModelContextEnvelopeOptions.Mode.ENFORCE
                && !historyUnits.isEmpty()
                && budgetedUnits.isEmpty();
        List<MessageUnit> selectedUnits;
        if (recentTurnExceedsBudget) {
            selectedUnits = List.of(historyUnits.getLast());
        } else if (options.mode() == ModelContextEnvelopeOptions.Mode.ENFORCE) {
            selectedUnits = budgetedUnits;
        } else {
            selectedUnits = historyUnits;
        }
        if (selectedUnits.size() < historyUnits.size()) {
            decisions.add(new ModelContextEnvelopeEvidence.Decision(
                    "HISTORY_TRUNCATED",
                    "HISTORY_BUDGET",
                    messageCount(historyUnits) - messageCount(selectedUnits),
                    excludedRefs(historyUnits, selectedUnits)));
        } else if (options.mode() == ModelContextEnvelopeOptions.Mode.OBSERVE
                && budgetedUnits.size() < historyUnits.size()) {
            decisions.add(new ModelContextEnvelopeEvidence.Decision(
                    "HISTORY_TRUNCATION_OBSERVED",
                    "HISTORY_BUDGET",
                    messageCount(historyUnits) - messageCount(budgetedUnits),
                    excludedRefs(historyUnits, budgetedUnits)));
        }

        List<ChatMessage> finalMessages = new ArrayList<>();
        if (!assembledSystem.isBlank()) {
            finalMessages.add(ChatMessage.system(assembledSystem));
        }
        selectedUnits.forEach(unit -> finalMessages.addAll(unit.messages()));
        finalMessages.addAll(current);
        long selectedInputTokens = saturatedAdd(
                countMessages(counter, modelId, finalMessages),
                countTools(counter, modelId, safeTools));

        Map<String, ModelContextEnvelopeEvidence.PartitionUsage> partitions = partitions(
                counter, modelId, originalSystem, effectiveToolProtocol, runtimeContext, skillBody,
                current, safeTools, selectedUnits);
        String hash = payloadHash(modelId, samplingOptions, finalMessages, safeTools, toolChoice);
        String reasonCode = "OK";

        if (effectiveWindow <= 0 || fixedCost > effectiveWindow || selectedInputTokens > effectiveWindow) {
            reasonCode = CONTEXT_BUDGET_EXCEEDED;
            if (options.mode() == ModelContextEnvelopeOptions.Mode.ENFORCE) {
                decisions.add(new ModelContextEnvelopeEvidence.Decision(
                        "FAIL_CLOSED", CONTEXT_BUDGET_EXCEEDED, finalMessages.size()));
                ModelContextEnvelopeEvidence evidence = evidence(
                        request, modelId, counter, window, outputReserve, safetyBuffer, effectiveWindow,
                        fixedCost, historyBudget, selectedInputTokens, finalMessages.size(), safeTools.size(),
                        partitions, messageRefs(selectedUnits), decisions, reasonCode, hash);
                throw new ModelContextEnvelopeException(
                        CONTEXT_BUDGET_EXCEEDED,
                        "fixed request cost or final payload exceeds the effective model window",
                        evidence);
            }
            decisions.add(new ModelContextEnvelopeEvidence.Decision(
                    "OVER_BUDGET_OBSERVED", CONTEXT_BUDGET_EXCEEDED, finalMessages.size()));
        }

        ModelContextEnvelopeEvidence evidence = evidence(
                request, modelId, counter, window, outputReserve, safetyBuffer, effectiveWindow,
                fixedCost, historyBudget, selectedInputTokens, finalMessages.size(), safeTools.size(),
                partitions, messageRefs(selectedUnits), decisions, reasonCode, hash);
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.copyOf(finalMessages))
                .modelId(modelId)
                .samplingOptions(samplingOptions)
                .tools(safeTools)
                .toolChoice(toolChoice)
                .build();
        return new ModelContextEnvelope(chatRequest, evidence);
    }

    ModelContextEnvelope observe(AgentLoopRequest request, ChatRequest actualRequest) {
        Objects.requireNonNull(request, "request must not be null");
        ChatRequest safeRequest = Objects.requireNonNull(actualRequest, "actualRequest must not be null");
        List<ChatMessage> rawMessages = new ArrayList<>(
                Objects.requireNonNullElse(safeRequest.getMessages(), List.of()));
        List<ChatMessage> messages = rawMessages.stream().filter(Objects::nonNull).toList();
        List<ToolDescriptor> tools = List.copyOf(Objects.requireNonNullElse(safeRequest.getTools(), List.of()));
        CountingSession counter = new CountingSession(tokenCounter, TokenCounterPort.approximate());
        String modelId = contextWindowPort.resolveModelId(safeRequest.getModelId());
        ModelContextWindow window = Objects.requireNonNull(
                contextWindowPort.resolve(modelId), "resolved context window must not be null");
        int outputReserve = outputReserve(request);
        ToolPairValidation validation = validateToolPairs(rawMessages);

        List<ChatMessage> systemMessages = messages.stream()
                .filter(message -> message.getRole() == ChatRole.SYSTEM)
                .toList();
        List<ChatMessage> conversationalMessages = messages.stream()
                .filter(message -> message.getRole() != ChatRole.SYSTEM)
                .toList();
        int currentStart = latestUserIndex(conversationalMessages);
        List<ChatMessage> historical = List.copyOf(conversationalMessages.subList(0, currentStart));
        List<ChatMessage> current = List.copyOf(
                conversationalMessages.subList(currentStart, conversationalMessages.size()));
        List<MessageUnit> historyUnits = completeUnits(counter, modelId, historical);

        List<ChatMessage> fixedMessages = new ArrayList<>(systemMessages);
        fixedMessages.addAll(current);
        long fixedCost = saturatedAdd(
                countMessages(counter, modelId, fixedMessages),
                countTools(counter, modelId, tools));
        int safetyBuffer = safetyBuffer(
                modelId, counter.estimatorMode(), counter.estimatorVersion());
        long effectiveWindow = (long) window.tokens() - outputReserve - safetyBuffer;
        long historyBudget = Math.max(0L, effectiveWindow - fixedCost);
        List<MessageUnit> budgetedUnits = selectHistory(historyUnits, historyBudget);
        List<ModelContextEnvelopeEvidence.Decision> decisions = new ArrayList<>();
        String reasonCode = "OK";

        if (!window.resolved()) {
            reasonCode = CONTEXT_WINDOW_UNKNOWN;
            decisions.add(new ModelContextEnvelopeEvidence.Decision(
                    "OBSERVED", CONTEXT_WINDOW_UNKNOWN, messages.size()));
        }
        if (!validation.valid()) {
            if ("OK".equals(reasonCode)) {
                reasonCode = INVALID_TOOL_PAIR;
            }
            decisions.add(new ModelContextEnvelopeEvidence.Decision(
                    "OBSERVED", INVALID_TOOL_PAIR, validation.affectedMessages()));
        }
        if (budgetedUnits.size() < historyUnits.size()) {
            decisions.add(new ModelContextEnvelopeEvidence.Decision(
                    "HISTORY_TRUNCATION_OBSERVED",
                    "HISTORY_BUDGET",
                    messageCount(historyUnits) - messageCount(budgetedUnits),
                    excludedRefs(historyUnits, budgetedUnits)));
        }

        long selectedInputTokens = saturatedAdd(
                countMessages(counter, modelId, messages),
                countTools(counter, modelId, tools));
        if (effectiveWindow <= 0 || fixedCost > effectiveWindow || selectedInputTokens > effectiveWindow) {
            if ("OK".equals(reasonCode)) {
                reasonCode = CONTEXT_BUDGET_EXCEEDED;
            }
            decisions.add(new ModelContextEnvelopeEvidence.Decision(
                    "OVER_BUDGET_OBSERVED", CONTEXT_BUDGET_EXCEEDED, messages.size()));
        }

        String system = systemMessages.stream()
                .map(message -> Objects.requireNonNullElse(message.getContent(), ""))
                .filter(text -> !text.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        Map<String, ModelContextEnvelopeEvidence.PartitionUsage> partitions = partitions(
                counter, modelId, system, "", "", "", current, tools, historyUnits);
        ModelContextEnvelopeEvidence evidence = evidence(
                request, modelId, counter, window, outputReserve, safetyBuffer, effectiveWindow,
                fixedCost, historyBudget, selectedInputTokens, messages.size(), tools.size(),
                partitions, messageRefs(historyUnits), decisions, reasonCode,
                payloadHash(modelId, safeRequest.getSamplingOptions(), messages, tools, safeRequest.getToolChoice()));
        return new ModelContextEnvelope(safeRequest, evidence);
    }

    private List<MessageUnit> selectHistory(List<MessageUnit> units, long budget) {
        List<MessageUnit> selected = new ArrayList<>();
        long remaining = Math.max(0L, budget);
        for (int i = units.size() - 1; i >= 0; i--) {
            MessageUnit unit = units.get(i);
            if (unit.tokens() > remaining) {
                break;
            }
            selected.add(0, unit);
            remaining -= unit.tokens();
        }
        return List.copyOf(selected);
    }

    private List<MessageUnit> completeUnits(CountingSession counter, String modelId, List<ChatMessage> messages) {
        List<MessageUnit> units = new ArrayList<>();
        List<ChatMessage> currentUnit = new ArrayList<>();
        List<String> currentRefs = new ArrayList<>();
        for (int index = 0; index < messages.size();) {
            ChatMessage message = messages.get(index);
            if (message.getRole() == ChatRole.USER && !currentUnit.isEmpty()) {
                units.add(messageUnit(counter, modelId, currentUnit, currentRefs));
                currentUnit = new ArrayList<>();
                currentRefs = new ArrayList<>();
            }
            currentUnit.add(message);
            currentRefs.add("history-message-" + index);
            index++;
            if (hasToolCalls(message)) {
                int expectedResults = message.getToolCalls().size();
                for (int count = 0; count < expectedResults && index < messages.size(); count++, index++) {
                    currentUnit.add(messages.get(index));
                    currentRefs.add("history-message-" + index);
                }
            }
        }
        if (!currentUnit.isEmpty()) {
            units.add(messageUnit(counter, modelId, currentUnit, currentRefs));
        }
        return List.copyOf(units);
    }

    private MessageUnit messageUnit(
            CountingSession counter, String modelId, List<ChatMessage> messages, List<String> refs) {
        return new MessageUnit(
                List.copyOf(messages), countMessages(counter, modelId, messages), List.copyOf(refs));
    }

    private ToolPairValidation validateToolPairs(List<ChatMessage> messages) {
        Set<String> pending = new LinkedHashSet<>();
        int affected = 0;
        for (ChatMessage message : messages) {
            if (message == null || message.getRole() == null) {
                return new ToolPairValidation(false, Math.max(1, affected));
            }
            if (!pending.isEmpty()) {
                if (message.getRole() != ChatRole.TOOL
                        || message.getToolCallId() == null
                        || !pending.remove(message.getToolCallId())) {
                    return new ToolPairValidation(false, Math.max(1, affected));
                }
                affected++;
                continue;
            }
            if (message.getRole() == ChatRole.TOOL) {
                return new ToolPairValidation(false, 1);
            }
            if (hasToolCalls(message)) {
                for (AgentToolCall toolCall : message.getToolCalls()) {
                    if (toolCall == null || !pending.add(toolCall.id())) {
                        return new ToolPairValidation(false, Math.max(1, affected));
                    }
                }
                affected++;
            }
        }
        return new ToolPairValidation(pending.isEmpty(), Math.max(affected, pending.size()));
    }

    private Map<String, ModelContextEnvelopeEvidence.PartitionUsage> partitions(
            CountingSession counter,
            String modelId,
            String system,
            String protocol,
            String runtimeContext,
            String skillBody,
            List<ChatMessage> current,
            List<ToolDescriptor> tools,
            List<MessageUnit> history) {
        Map<String, ModelContextEnvelopeEvidence.PartitionUsage> result = new LinkedHashMap<>();
        result.put("system", textUsage(counter, modelId, system));
        result.put("toolProtocol", textUsage(counter, modelId, protocol));
        result.put("runtimeContext", textUsage(counter, modelId, runtimeContext));
        result.put("skillBody", textUsage(counter, modelId, skillBody));
        List<ChatMessage> activePairs = current.stream()
                .filter(message -> message.getRole() == ChatRole.TOOL || hasToolCalls(message))
                .toList();
        List<ChatMessage> currentInput = current.stream()
                .filter(message -> message.getRole() != ChatRole.TOOL && !hasToolCalls(message))
                .toList();
        result.put("currentInput", messageUsage(counter, modelId, currentInput));
        result.put("toolSchemas", toolUsage(counter, modelId, tools));
        result.put("activePairs", messageUsage(counter, modelId, activePairs));
        result.put("readySummaries", textUsage(counter, modelId, ""));
        List<ChatMessage> historicalMessages = history.stream()
                .flatMap(unit -> unit.messages().stream())
                .toList();
        result.put("historicalMessages", messageUsage(counter, modelId, historicalMessages));
        return result;
    }

    private ModelContextEnvelopeEvidence evidence(
            AgentLoopRequest request,
            String modelId,
            CountingSession counter,
            ModelContextWindow window,
            int outputReserve,
            int safetyBuffer,
            long effectiveWindow,
            long fixedCost,
            long historyBudget,
            long selectedInputTokens,
            int messageCount,
            int toolCount,
            Map<String, ModelContextEnvelopeEvidence.PartitionUsage> partitions,
            List<String> selectedMessageRefs,
            List<ModelContextEnvelopeEvidence.Decision> decisions,
            String reasonCode,
            String hash) {
        return new ModelContextEnvelopeEvidence(
                modelId, hash, "KERNEL_CANONICAL", options.mode(), counter.estimatorMode(),
                counter.estimatorVersion(), confidence(counter.estimatorMode()),
                window.tokens(), window.source(), outputReserve, safetyBuffer, effectiveWindow,
                fixedCost, historyBudget, selectedInputTokens,
                saturatedSubtract(effectiveWindow, selectedInputTokens),
                messageCount, toolCount, partitions, selectedMessageRefs, decisions, reasonCode,
                null, null, null);
    }

    private int outputReserve(AgentLoopRequest request) {
        Integer configured = request.samplingOptions() == null ? null : request.samplingOptions().getMaxTokens();
        return configured != null && configured > 0 ? configured : options.defaultOutputReserveTokens();
    }

    private ChatSamplingOptions enforcedSamplingOptions(AgentLoopRequest request, int outputReserve) {
        ChatSamplingOptions configured = request.samplingOptions();
        if (configured != null && configured.getMaxTokens() != null && configured.getMaxTokens() > 0) {
            return configured;
        }
        return ChatSamplingOptions.builder()
                .temperature(configured == null ? null : configured.getTemperature())
                .topP(configured == null ? null : configured.getTopP())
                .topK(configured == null ? null : configured.getTopK())
                .thinking(configured == null ? null : configured.getThinking())
                .maxTokens(outputReserve)
                .build();
    }

    private int safetyBuffer(
            String modelId,
            TokenCounterPort.EstimatorMode estimatorMode,
            String estimatorVersion) {
        int base = estimatorMode == TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK
                ? options.conservativeSafetyBufferTokens()
                : options.safetyBufferTokens();
        long calibrated = (long) base + calibrator.additionalSafetyTokens(
                modelId, estimatorMode, estimatorVersion);
        return (int) Math.min(Integer.MAX_VALUE, calibrated);
    }

    private ContextBudget runtimeContextBudget() {
        long maxChars = (long) options.maxRuntimeContextTokens() * options.estimatedCharsPerToken();
        return new ContextBudget(options.maxRuntimeContextItems(), (int) Math.min(Integer.MAX_VALUE, maxChars));
    }

    String runtimeContextSnapshot(AgentLoopRequest request) {
        AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return contextWeaver.weave(
                safeRequest.contextPack(), safeRequest.memoryContext(), runtimeContextBudget());
    }

    private String resolvedRuntimeContext(AgentLoopRequest request) {
        String snapshot = request.runtimeContextSnapshot();
        return snapshot == null ? runtimeContextSnapshot(request) : snapshot;
    }

    private int latestUserIndex(List<ChatMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index).getRole() == ChatRole.USER) {
                return index;
            }
        }
        return 0;
    }

    private long countMessages(CountingSession counter, String modelId, List<ChatMessage> messages) {
        long total = 0L;
        for (ChatMessage message : messages) {
            total = saturatedAdd(total, counter.countTextTokens(modelId, messageText(message)));
            total = saturatedAdd(total, options.messageOverheadTokens());
        }
        return total;
    }

    private long countTools(CountingSession counter, String modelId, List<ToolDescriptor> tools) {
        long total = 0L;
        for (ToolDescriptor tool : tools) {
            total = saturatedAdd(total, counter.countTextTokens(modelId, toolText(tool)));
            total = saturatedAdd(total, options.toolOverheadTokens());
        }
        return total;
    }

    private long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private long saturatedSubtract(long left, long right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException ignored) {
            return right >= 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    private ModelContextEnvelopeEvidence.PartitionUsage textUsage(
            CountingSession counter, String modelId, String text) {
        String safe = Objects.requireNonNullElse(text, "");
        return new ModelContextEnvelopeEvidence.PartitionUsage(
                counter.countTextTokens(modelId, safe),
                safe.codePointCount(0, safe.length()),
                safe.getBytes(StandardCharsets.UTF_8).length);
    }

    private ModelContextEnvelopeEvidence.PartitionUsage messageUsage(
            CountingSession counter, String modelId, List<ChatMessage> messages) {
        String text = messages.stream().map(this::messageText)
                .collect(java.util.stream.Collectors.joining("\n"));
        return new ModelContextEnvelopeEvidence.PartitionUsage(
                countMessages(counter, modelId, messages),
                text.codePointCount(0, text.length()),
                text.getBytes(StandardCharsets.UTF_8).length);
    }

    private ModelContextEnvelopeEvidence.PartitionUsage toolUsage(
            CountingSession counter, String modelId, List<ToolDescriptor> tools) {
        String text = tools.stream().map(this::toolText)
                .collect(java.util.stream.Collectors.joining("\n"));
        return new ModelContextEnvelopeEvidence.PartitionUsage(
                countTools(counter, modelId, tools),
                text.codePointCount(0, text.length()),
                text.getBytes(StandardCharsets.UTF_8).length);
    }

    private String payloadHash(String modelId,
                               ChatSamplingOptions samplingOptions,
                               List<ChatMessage> messages,
                               List<ToolDescriptor> tools,
                               String toolChoice) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, "model", modelId);
            updateDigest(digest, "toolChoice", toolChoice);
            updateDigest(digest, "sampling.present", samplingOptions != null);
            if (samplingOptions != null) {
                updateDigest(digest, "sampling.temperature", samplingOptions.getTemperature());
                updateDigest(digest, "sampling.topP", samplingOptions.getTopP());
                updateDigest(digest, "sampling.topK", samplingOptions.getTopK());
                updateDigest(digest, "sampling.maxTokens", samplingOptions.getMaxTokens());
                updateDigest(digest, "sampling.thinking", samplingOptions.getThinking());
            }
            updateDigest(digest, "messages.count", messages.size());
            for (int index = 0; index < messages.size(); index++) {
                updateMessageDigest(digest, "messages[" + index + "]", messages.get(index));
            }
            updateDigest(digest, "tools.count", tools.size());
            for (int index = 0; index < tools.size(); index++) {
                ToolDescriptor tool = tools.get(index);
                String prefix = "tools[" + index + "]";
                updateDigest(digest, prefix + ".id", tool.toolId());
                updateDigest(digest, prefix + ".name", tool.name());
                updateDigest(digest, prefix + ".description", tool.description());
                updateDigest(digest, prefix + ".schema", tool.jsonSchema());
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private void updateMessageDigest(MessageDigest digest, String prefix, ChatMessage message) {
        updateDigest(digest, prefix + ".role", message.getRole());
        updateDigest(digest, prefix + ".content", message.getContent());
        updateDigest(digest, prefix + ".thinking", message.getThinkingContent());
        updateDigest(digest, prefix + ".thinkingDuration", message.getThinkingDuration());
        updateDigest(digest, prefix + ".toolCallId", message.getToolCallId());
        List<AgentToolCall> toolCalls = Objects.requireNonNullElse(message.getToolCalls(), List.of());
        updateDigest(digest, prefix + ".toolCalls.count", toolCalls.size());
        for (int index = 0; index < toolCalls.size(); index++) {
            AgentToolCall toolCall = toolCalls.get(index);
            String callPrefix = prefix + ".toolCalls[" + index + "]";
            updateDigest(digest, callPrefix + ".id", toolCall.id());
            updateDigest(digest, callPrefix + ".toolId", toolCall.toolId());
            updateValueDigest(digest, callPrefix + ".arguments", toolCall.arguments());
        }
    }

    private void updateValueDigest(MessageDigest digest, String prefix, Object value) {
        if (value == null) {
            updateDigest(digest, prefix + ".kind", "null");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            updateDigest(digest, prefix + ".kind", "map");
            List<? extends Map.Entry<?, ?>> entries = map.entrySet().stream()
                    .sorted(java.util.Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .toList();
            updateDigest(digest, prefix + ".count", entries.size());
            for (int index = 0; index < entries.size(); index++) {
                Map.Entry<?, ?> entry = entries.get(index);
                updateDigest(digest, prefix + "[" + index + "].key", entry.getKey());
                updateValueDigest(digest, prefix + "[" + index + "].value", entry.getValue());
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            updateDigest(digest, prefix + ".kind", "list");
            List<Object> items = new ArrayList<>();
            iterable.forEach(items::add);
            updateDigest(digest, prefix + ".count", items.size());
            for (int index = 0; index < items.size(); index++) {
                updateValueDigest(digest, prefix + "[" + index + "]", items.get(index));
            }
            return;
        }
        updateDigest(digest, prefix + ".kind", value.getClass().getName());
        updateDigest(digest, prefix + ".value", value);
    }

    private void updateDigest(MessageDigest digest, String name, Object value) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = Objects.toString(value, "").getBytes(StandardCharsets.UTF_8);
        updateDigestLength(digest, nameBytes.length);
        digest.update(nameBytes);
        updateDigestLength(digest, valueBytes.length);
        digest.update(valueBytes);
    }

    private void updateDigestLength(MessageDigest digest, int length) {
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
    }

    private String messageText(ChatMessage message) {
        StringBuilder value = new StringBuilder();
        value.append(message.getRole()).append('|')
                .append(Objects.requireNonNullElse(message.getContent(), "")).append('|')
                .append(Objects.requireNonNullElse(message.getThinkingContent(), "")).append('|')
                .append(Objects.requireNonNullElse(message.getThinkingDuration(), 0)).append('|')
                .append(Objects.requireNonNullElse(message.getToolCallId(), ""));
        for (AgentToolCall toolCall : Objects.requireNonNullElse(message.getToolCalls(), List.<AgentToolCall>of())) {
            value.append('|').append(toolCall.id()).append(':').append(toolCall.toolId())
                    .append(':').append(canonicalValue(toolCall.arguments()));
        }
        return value.toString();
    }

    private String toolText(ToolDescriptor tool) {
        return tool.toolId() + '|' + tool.name() + '|' + tool.description() + '|' + tool.jsonSchema();
    }

    private String canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(java.util.Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> String.valueOf(entry.getKey()) + '=' + canonicalValue(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            iterable.forEach(item -> items.add(canonicalValue(item)));
            return String.join(",", items);
        }
        return Objects.toString(value, "null");
    }

    private String joinSections(String... sections) {
        return java.util.Arrays.stream(sections)
                .map(section -> Objects.requireNonNullElse(section, "").trim())
                .filter(section -> !section.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private boolean hasToolCalls(ChatMessage message) {
        return message != null && message.getRole() == ChatRole.ASSISTANT
                && message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }

    private int messageCount(List<MessageUnit> units) {
        return units.stream().mapToInt(unit -> unit.messages().size()).sum();
    }

    private List<String> messageRefs(List<MessageUnit> units) {
        return units.stream().flatMap(unit -> unit.messageRefs().stream()).toList();
    }

    private List<String> excludedRefs(List<MessageUnit> all, List<MessageUnit> selected) {
        Set<String> selectedRefs = new LinkedHashSet<>(messageRefs(selected));
        return messageRefs(all).stream().filter(ref -> !selectedRefs.contains(ref)).toList();
    }

    private static String confidence(TokenCounterPort.EstimatorMode mode) {
        return switch (mode) {
            case EXACT_PROVIDER -> "HIGH";
            case CALIBRATED_APPROXIMATION -> "MEDIUM";
            case CONSERVATIVE_FALLBACK -> "LOW";
        };
    }

    private record MessageUnit(List<ChatMessage> messages, long tokens, List<String> messageRefs) {
    }

    private record ToolPairValidation(boolean valid, int affectedMessages) {
    }

    private static final class CountingSession {
        private final TokenCounterPort primary;
        private final TokenCounterPort fallback;
        private boolean fallbackActive;
        private boolean fallbackActivatedByFailure;

        private CountingSession(TokenCounterPort primary, TokenCounterPort fallback) {
            this.primary = Objects.requireNonNull(primary, "primary counter must not be null");
            this.fallback = Objects.requireNonNull(fallback, "fallback counter must not be null");
            this.fallbackActive = primary.estimatorMode() == TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK;
        }

        private int countTextTokens(String modelId, String text) {
            if (fallbackActive) {
                return fallback.countTextTokens(modelId, text);
            }
            try {
                int count = primary.countTextTokens(modelId, text);
                if (count < 0) {
                    throw new IllegalStateException("token counter returned a negative value");
                }
                return count;
            } catch (RuntimeException ex) {
                fallbackActive = true;
                fallbackActivatedByFailure = true;
                return fallback.countTextTokens(modelId, text);
            }
        }

        private boolean fallbackActivatedByFailure() {
            return fallbackActivatedByFailure;
        }

        private TokenCounterPort.EstimatorMode estimatorMode() {
            return fallbackActive ? TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK : primary.estimatorMode();
        }

        private String estimatorVersion() {
            return fallbackActive ? fallback.estimatorVersion() : primary.estimatorVersion();
        }
    }
}
