/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Versioned, deterministic context required to resume after tool approval. */
public record AgentResumeDescriptor(
        String schemaVersion,
        String modelId,
        SamplingSnapshot sampling,
        RuntimeContextMode runtimeContextMode,
        String runtimeContextSnapshot,
        String skillRuntimeContext,
        String contextPackId,
        List<SkillRevisionRef> skillRevisions) {

    public static final String SCHEMA_VERSION = "agent-resume-descriptor-v1";

    public AgentResumeDescriptor {
        schemaVersion = requireText(schemaVersion, "schemaVersion must not be blank");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported resume descriptor schemaVersion");
        }
        modelId = trimToNull(modelId);
        sampling = Objects.requireNonNullElseGet(sampling, SamplingSnapshot::empty);
        runtimeContextMode = Objects.requireNonNull(
                runtimeContextMode, "runtimeContextMode must not be null");
        runtimeContextSnapshot = trimToNull(runtimeContextSnapshot);
        skillRuntimeContext = trimToNull(skillRuntimeContext);
        contextPackId = trimToNull(contextPackId);
        skillRevisions = List.copyOf(Objects.requireNonNullElse(skillRevisions, List.of()));
        if (runtimeContextMode == RuntimeContextMode.MATERIALIZED_IN_HISTORY
                && (runtimeContextSnapshot != null || skillRuntimeContext != null)) {
            throw new IllegalArgumentException("Materialized resume context must not contain duplicate snapshots");
        }
    }

    public static AgentResumeDescriptor capture(
            AgentLoopRequest request,
            String effectiveModelId,
            String runtimeContextSnapshot,
            boolean materializedInHistory) {
        AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        RuntimeContextMode mode = materializedInHistory
                ? RuntimeContextMode.MATERIALIZED_IN_HISTORY
                : RuntimeContextMode.SNAPSHOT;
        String contextPackId = safeRequest.contextPack() == null
                ? null
                : safeRequest.contextPack().contextPackId();
        List<SkillRevisionRef> revisions = safeRequest.skillRuntimeBlocks().stream()
                .map(AgentResumeDescriptor::skillRef)
                .toList();
        return new AgentResumeDescriptor(
                SCHEMA_VERSION,
                firstText(effectiveModelId, safeRequest.modelId()),
                SamplingSnapshot.from(safeRequest.samplingOptions()),
                mode,
                materializedInHistory ? null : runtimeContextSnapshot,
                materializedInHistory ? null : safeRequest.skillRuntimeContext(),
                contextPackId,
                revisions);
    }

    public static AgentResumeDescriptor from(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalStateException("Resume descriptor is missing");
        }
        String schemaVersion = text(node, "schemaVersion");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalStateException("Unsupported resume descriptor schemaVersion");
        }
        JsonNode samplingNode = node.path("sampling");
        List<SkillRevisionRef> revisions = new ArrayList<>();
        JsonNode revisionsNode = node.path("skillRevisions");
        if (revisionsNode.isArray()) {
            revisionsNode.forEach(item -> revisions.add(new SkillRevisionRef(
                    text(item, "name"),
                    text(item, "revisionId"),
                    text(item, "contentHash"),
                    text(item, "injectMode"))));
        }
        try {
            return new AgentResumeDescriptor(
                    schemaVersion,
                    text(node, "modelId"),
                    new SamplingSnapshot(
                            doubleValue(samplingNode, "temperature"),
                            doubleValue(samplingNode, "topP"),
                            intValue(samplingNode, "topK"),
                            intValue(samplingNode, "maxTokens"),
                            booleanValue(samplingNode, "thinking")),
                    RuntimeContextMode.valueOf(requireText(
                            text(node, "runtimeContextMode"), "runtimeContextMode must not be blank")),
                    text(node, "runtimeContextSnapshot"),
                    text(node, "skillRuntimeContext"),
                    text(node, "contextPackId"),
                    revisions);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Resume descriptor is invalid", ex);
        }
    }

    public ChatSamplingOptions samplingOptions() {
        return ChatSamplingOptions.builder()
                .temperature(sampling.temperature())
                .topP(sampling.topP())
                .topK(sampling.topK())
                .maxTokens(sampling.maxTokens())
                .thinking(sampling.thinking())
                .build();
    }

    private static SkillRevisionRef skillRef(SkillRuntimeBlock block) {
        return new SkillRevisionRef(
                block.name(),
                block.revisionId(),
                block.contentHash(),
                block.injectMode().name());
    }

    private static String firstText(String first, String second) {
        String normalized = trimToNull(first);
        return normalized == null ? trimToNull(second) : normalized;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Double doubleValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.doubleValue();
    }

    private static Integer intValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isIntegralNumber() ? null : value.intValue();
    }

    private static Boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isBoolean() ? null : value.booleanValue();
    }

    private static String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public enum RuntimeContextMode {
        SNAPSHOT,
        MATERIALIZED_IN_HISTORY
    }

    public record SamplingSnapshot(
            Double temperature,
            Double topP,
            Integer topK,
            Integer maxTokens,
            Boolean thinking) {

        static SamplingSnapshot from(ChatSamplingOptions options) {
            if (options == null) {
                return empty();
            }
            return new SamplingSnapshot(
                    options.getTemperature(),
                    options.getTopP(),
                    options.getTopK(),
                    options.getMaxTokens(),
                    options.getThinking());
        }

        static SamplingSnapshot empty() {
            return new SamplingSnapshot(null, null, null, null, null);
        }
    }

    public record SkillRevisionRef(
            String name,
            String revisionId,
            String contentHash,
            String injectMode) {

        public SkillRevisionRef {
            name = Objects.requireNonNullElse(name, "").trim();
            revisionId = Objects.requireNonNullElse(revisionId, "").trim();
            contentHash = Objects.requireNonNullElse(contentHash, "").trim();
            injectMode = Objects.requireNonNullElse(injectMode, "").trim();
        }
    }
}
