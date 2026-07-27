/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Safe metadata for reproducing a model-context budget decision without storing prompt text. */
record ModelContextEnvelopeEvidence(
        String modelId,
        String payloadHash,
        String payloadHashSource,
        ModelContextEnvelopeOptions.Mode mode,
        TokenCounterPort.EstimatorMode estimatorMode,
        String estimatorVersion,
        String estimatorConfidence,
        int contextWindow,
        String contextWindowSource,
        int outputReserve,
        int safetyBuffer,
        long effectiveWindow,
        long fixedCost,
        long historyBudget,
        long selectedInputTokens,
        long remainingTokens,
        int messageCount,
        int toolCount,
        Map<String, PartitionUsage> partitions,
        List<String> selectedMessageRefs,
        List<Decision> decisions,
        String reasonCode,
        Long providerInputTokens,
        Long providerOutputTokens,
        Long estimatorDeltaTokens) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    ModelContextEnvelopeEvidence {
        modelId = Objects.requireNonNullElse(modelId, "");
        payloadHash = Objects.requireNonNullElse(payloadHash, "");
        payloadHashSource = Objects.requireNonNullElse(payloadHashSource, "KERNEL_CANONICAL");
        mode = Objects.requireNonNullElse(mode, ModelContextEnvelopeOptions.Mode.ENFORCE);
        estimatorMode = Objects.requireNonNullElse(
                estimatorMode, TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK);
        estimatorVersion = Objects.requireNonNullElse(estimatorVersion, "unknown");
        estimatorConfidence = Objects.requireNonNullElse(estimatorConfidence, "LOW");
        contextWindowSource = Objects.requireNonNullElse(contextWindowSource, "unknown");
        partitions = Map.copyOf(Objects.requireNonNullElse(partitions, Map.of()));
        selectedMessageRefs = List.copyOf(Objects.requireNonNullElse(selectedMessageRefs, List.of()));
        decisions = List.copyOf(Objects.requireNonNullElse(decisions, List.of()));
        reasonCode = Objects.requireNonNullElse(reasonCode, "OK");
    }

    ModelContextEnvelopeEvidence withPayloadHash(String hash, String source) {
        return new ModelContextEnvelopeEvidence(
                modelId, hash, source, mode, estimatorMode, estimatorVersion, estimatorConfidence,
                contextWindow, contextWindowSource, outputReserve, safetyBuffer, effectiveWindow,
                fixedCost, historyBudget, selectedInputTokens, remainingTokens, messageCount, toolCount,
                partitions, selectedMessageRefs, decisions, reasonCode,
                providerInputTokens, providerOutputTokens, estimatorDeltaTokens);
    }

    ModelContextEnvelopeEvidence withProviderUsage(long inputTokens, long outputTokens) {
        return new ModelContextEnvelopeEvidence(
                modelId, payloadHash, payloadHashSource, mode, estimatorMode, estimatorVersion,
                estimatorConfidence, contextWindow, contextWindowSource, outputReserve, safetyBuffer,
                effectiveWindow, fixedCost, historyBudget, selectedInputTokens, remainingTokens,
                messageCount, toolCount, partitions, selectedMessageRefs, decisions, reasonCode,
                inputTokens, outputTokens, selectedInputTokens - inputTokens);
    }

    static ModelContextEnvelopeEvidence observeFallback(
            String modelId,
            int outputReserve,
            int safetyBuffer,
            int messageCount,
            int toolCount) {
        return new ModelContextEnvelopeEvidence(
                modelId,
                "",
                "KERNEL_CANONICAL",
                ModelContextEnvelopeOptions.Mode.OBSERVE,
                TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK,
                "unavailable",
                "LOW",
                0,
                "observe-unavailable",
                outputReserve,
                safetyBuffer,
                0,
                0,
                0,
                0,
                0,
                Math.max(0, messageCount),
                Math.max(0, toolCount),
                Map.of(),
                List.of(),
                List.of(new Decision(
                        "OBSERVE_FALLBACK", "OBSERVATION_UNAVAILABLE", Math.max(0, messageCount))),
                "OBSERVE_FALLBACK",
                null,
                null,
                null);
    }

    String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", "model-context-envelope-v1");
        root.put("modelId", modelId);
        root.put("payloadHash", payloadHash);
        root.put("payloadHashSource", payloadHashSource);
        root.put("mode", mode.name());
        root.put("estimatorMode", estimatorMode.name());
        root.put("estimatorVersion", estimatorVersion);
        root.put("estimatorConfidence", estimatorConfidence);
        root.put("contextWindow", contextWindow);
        root.put("contextWindowSource", contextWindowSource);
        root.put("outputReserve", outputReserve);
        root.put("safetyBuffer", safetyBuffer);
        root.put("effectiveWindow", effectiveWindow);
        root.put("fixedCost", fixedCost);
        root.put("historyBudget", historyBudget);
        root.put("selectedInputTokens", selectedInputTokens);
        root.put("remainingTokens", remainingTokens);
        root.put("messageCount", messageCount);
        root.put("toolCount", toolCount);
        root.put("partitions", partitions);
        root.put("selectedMessageRefs", selectedMessageRefs);
        root.put("decisions", decisions);
        root.put("reasonCode", reasonCode);
        root.put("providerUsageAvailable", providerInputTokens != null && providerOutputTokens != null);
        if (providerInputTokens != null && providerOutputTokens != null) {
            root.put("providerInputTokens", providerInputTokens);
            root.put("providerOutputTokens", providerOutputTokens);
            root.put("estimatorDeltaTokens", estimatorDeltaTokens);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("serialize model context envelope evidence failed", ex);
        }
    }

    record PartitionUsage(long tokens, int characters, int bytes) {
    }

    record Decision(String kind, String reason, int affectedMessages, List<String> messageRefs) {
        Decision(String kind, String reason, int affectedMessages) {
            this(kind, reason, affectedMessages, List.of());
        }

        Decision {
            kind = Objects.requireNonNullElse(kind, "UNKNOWN");
            reason = Objects.requireNonNullElse(reason, "UNKNOWN");
            affectedMessages = Math.max(0, affectedMessages);
            messageRefs = List.copyOf(Objects.requireNonNullElse(messageRefs, List.of()));
        }
    }

}
