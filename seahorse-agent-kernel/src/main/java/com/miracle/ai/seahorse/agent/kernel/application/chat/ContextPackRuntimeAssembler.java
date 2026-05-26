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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextBuildItemCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextBuildRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class ContextPackRuntimeAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(ContextPackRuntimeAssembler.class);

    private static final String GENERATED_RUN_PREFIX = "ctx-run-";
    private static final String USER_INPUT_SOURCE_PREFIX = "user-input-";
    private static final String MEMORY_SOURCE_PREFIX = "memory-";
    private static final String RAG_CHUNK_SOURCE_PREFIX = "rag-chunk-";
    private static final String UNKNOWN_RESOURCE_ID = "unknown";
    private static final String EMPTY_JSON_OBJECT = "{}";
    private static final String CITATION_SOURCE_FIELD = "source";
    private static final String CITATION_USER_INPUT = "user_input";
    private static final String CITATION_MEMORY = "memory";
    private static final String CITATION_RAG_CHUNK = "rag_chunk";
    private static final int DEFAULT_BUDGET_TOKENS = 2000;
    private static final int TOKEN_CHAR_RATIO = 4;
    private static final double USER_INPUT_SCORE = 1.0D;
    private static final double DEFAULT_CONFIDENCE = 1.0D;
    private static final double DEFAULT_MEMORY_SCORE = 0.6D;
    private static final double DEFAULT_RAG_SCORE = 0.5D;

    private final Optional<ContextPackBuilderInboundPort> contextPackBuilder;
    private final ConversationAttachmentContextAssembler attachmentContextAssembler;
    private final ObjectMapper objectMapper;

    ContextPackRuntimeAssembler() {
        this(Optional.empty(), new ObjectMapper());
    }

    ContextPackRuntimeAssembler(Optional<ContextPackBuilderInboundPort> contextPackBuilder) {
        this(contextPackBuilder, new ObjectMapper());
    }

    ContextPackRuntimeAssembler(Optional<ContextPackBuilderInboundPort> contextPackBuilder,
                                ConversationAttachmentContextAssembler attachmentContextAssembler) {
        this(contextPackBuilder, attachmentContextAssembler, new ObjectMapper());
    }

    ContextPackRuntimeAssembler(Optional<ContextPackBuilderInboundPort> contextPackBuilder,
                                ObjectMapper objectMapper) {
        this(contextPackBuilder, ConversationAttachmentContextAssembler.noop(), objectMapper);
    }

    ContextPackRuntimeAssembler(Optional<ContextPackBuilderInboundPort> contextPackBuilder,
                                ConversationAttachmentContextAssembler attachmentContextAssembler,
                                ObjectMapper objectMapper) {
        this.contextPackBuilder = contextPackBuilder == null ? Optional.empty() : contextPackBuilder;
        this.attachmentContextAssembler = Objects.requireNonNullElseGet(
                attachmentContextAssembler, ConversationAttachmentContextAssembler::noop);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    ContextPack assembleForRag(StreamChatContext context,
                               RetrievalContext retrievalContext,
                               RewriteResult rewriteResult) {
        StreamChatContext safeContext = Objects.requireNonNull(context, "context must not be null");
        String userId = trimToNull(safeContext.getUserId());
        if (userId == null) {
            return null;
        }
        String runId = runIdForRag(safeContext);
        String taskGoal = firstText(rewriteResult == null ? null : rewriteResult.rewrittenQuestion(),
                safeContext.getQuestion());
        List<ContextBuildItemCandidate> candidates = new ArrayList<>();
        addUserInputCandidate(candidates, safeContext.getQuestion(), runId, userId,
                AgentDefinition.DEFAULT_TENANT_ID);
        addAttachmentCandidates(candidates, safeContext.getConversationId(), userId, safeContext.getAttachmentIds());
        addMemoryCandidates(candidates, safeContext.getMemoryContext(), userId, AgentDefinition.DEFAULT_TENANT_ID);
        addRagCandidates(candidates, retrievalContext, userId);
        return build(new ContextBuildRequest(
                runId,
                AgentRuntimeConstants.LEGACY_REACT_AGENT_ID,
                null,
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                taskGoal,
                DEFAULT_BUDGET_TOKENS,
                candidates));
    }

    ContextPack assembleForAgent(String question,
                                 String runId,
                                 String taskId,
                                 String agentId,
                                 String versionId,
                                 String tenantId,
                                 String userId,
                                 MemoryContext memoryContext) {
        return assembleForAgent(question, runId, taskId, agentId, versionId, tenantId, userId, memoryContext,
                null, List.of());
    }

    ContextPack assembleForAgent(String question,
                                 String runId,
                                 String taskId,
                                 String agentId,
                                 String versionId,
                                 String tenantId,
                                 String userId,
                                 MemoryContext memoryContext,
                                 String conversationId,
                                 List<String> attachmentIds) {
        String safeUserId = trimToNull(userId);
        if (safeUserId == null) {
            return null;
        }
        String safeTenantId = firstText(tenantId, AgentDefinition.DEFAULT_TENANT_ID);
        String safeRunId = firstText(runId, generatedRunId(taskId));
        List<ContextBuildItemCandidate> candidates = new ArrayList<>();
        addUserInputCandidate(candidates, question, safeRunId, safeUserId, safeTenantId);
        addAttachmentCandidates(candidates, conversationId, safeUserId, attachmentIds);
        addMemoryCandidates(candidates, memoryContext, safeUserId, safeTenantId);
        return build(new ContextBuildRequest(
                safeRunId,
                firstText(agentId, AgentRuntimeConstants.LEGACY_REACT_AGENT_ID),
                versionId,
                safeTenantId,
                safeUserId,
                firstText(question, "agent task"),
                DEFAULT_BUDGET_TOKENS,
                candidates));
    }

    private void addAttachmentCandidates(List<ContextBuildItemCandidate> candidates,
                                         String conversationId,
                                         String userId,
                                         List<String> attachmentIds) {
        candidates.addAll(attachmentContextAssembler.assemble(conversationId, userId, attachmentIds));
    }

    private ContextPack build(ContextBuildRequest request) {
        if (contextPackBuilder.isEmpty()) {
            return null;
        }
        try {
            return contextPackBuilder.get().build(request);
        } catch (RuntimeException ex) {
            LOG.warn("ContextPack assembly failed, fallback to legacy runtime context: runId={}, errorType={}, message={}",
                    request.runId(), ex.getClass().getSimpleName(), ex.getMessage());
            LOG.debug("ContextPack assembly failure details: runId={}", request.runId(), ex);
            return null;
        }
    }

    private void addUserInputCandidate(List<ContextBuildItemCandidate> candidates,
                                       String question,
                                       String runId,
                                       String userId,
                                       String tenantId) {
        String content = trimToNull(question);
        if (content == null) {
            return;
        }
        String sourceId = USER_INPUT_SOURCE_PREFIX + stableIdPart(runId);
        candidates.add(new ContextBuildItemCandidate(
                ContextItemSourceType.USER_INPUT,
                sourceId,
                content,
                null,
                USER_INPUT_SCORE,
                DEFAULT_CONFIDENCE,
                ContextSensitivity.INTERNAL,
                new ResourceRef(ContextResourceType.USER_INPUT, sourceId, tenantId, userId, EMPTY_JSON_OBJECT),
                json(Map.of(CITATION_SOURCE_FIELD, CITATION_USER_INPUT, "runId", runId)),
                estimateTokens(content),
                null));
    }

    private void addMemoryCandidates(List<ContextBuildItemCandidate> candidates,
                                     MemoryContext memoryContext,
                                     String userId,
                                     String tenantId) {
        if (memoryContext == null) {
            return;
        }
        int sequence = 1;
        sequence = addMemoryList(candidates, memoryContext.getCorrectionMemories(), userId, tenantId, sequence);
        sequence = addMemoryList(candidates, memoryContext.getProfileMemories(), userId, tenantId, sequence);
        sequence = addMemoryList(candidates, memoryContext.getShortTermMemories(), userId, tenantId, sequence);
        sequence = addMemoryList(candidates, memoryContext.getBusinessDocumentMemories(), userId, tenantId, sequence);
        sequence = addMemoryList(candidates, memoryContext.getLongTermMemories(), userId, tenantId, sequence);
        addMemoryList(candidates, memoryContext.getSemanticMemories(), userId, tenantId, sequence);
    }

    private int addMemoryList(List<ContextBuildItemCandidate> candidates,
                              List<MemoryItem> memories,
                              String userId,
                              String tenantId,
                              int sequence) {
        if (memories == null || memories.isEmpty()) {
            return sequence;
        }
        int next = sequence;
        for (MemoryItem memory : memories) {
            String content = memory == null ? null : trimToNull(memory.getContent());
            if (content == null) {
                continue;
            }
            String sourceId = firstText(memory.getId(), MEMORY_SOURCE_PREFIX + next);
            String ownerUserId = firstText(memory.getUserId(), userId);
            candidates.add(new ContextBuildItemCandidate(
                    ContextItemSourceType.MEMORY,
                    sourceId,
                    content,
                    null,
                    ratio(memory.getRelevanceScore(), ratio(memory.getImportanceScore(), DEFAULT_MEMORY_SCORE)),
                    ratio(memory.getConfidenceLevel(), DEFAULT_CONFIDENCE),
                    ContextSensitivity.CONFIDENTIAL,
                    new ResourceRef(ContextResourceType.MEMORY, sourceId, tenantId, ownerUserId,
                            firstText(memory.getMetadataJson(), EMPTY_JSON_OBJECT)),
                    json(Map.of(
                            CITATION_SOURCE_FIELD, CITATION_MEMORY,
                            "memoryId", sourceId,
                            "layer", memory.getLayer() == null ? "" : memory.getLayer().name())),
                    estimateTokens(content),
                    null));
            next++;
        }
        return next;
    }

    private void addRagCandidates(List<ContextBuildItemCandidate> candidates,
                                  RetrievalContext retrievalContext,
                                  String userId) {
        if (retrievalContext == null || retrievalContext.getIntentChunks() == null) {
            return;
        }
        int sequence = 1;
        for (Map.Entry<String, List<RetrievedChunk>> entry : retrievalContext.getIntentChunks().entrySet()) {
            List<RetrievedChunk> chunks = entry.getValue();
            if (chunks == null) {
                continue;
            }
            for (RetrievedChunk chunk : chunks) {
                String content = chunk == null ? null : trimToNull(chunk.getText());
                if (content == null) {
                    continue;
                }
                String sourceId = firstText(chunk.getId(), RAG_CHUNK_SOURCE_PREFIX + sequence);
                String tenantId = firstText(chunk.getTenantId(), AgentDefinition.DEFAULT_TENANT_ID);
                String resourceId = firstText(chunk.getDocId(), sourceId);
                String kbId = firstText(chunk.getKbId(), UNKNOWN_RESOURCE_ID);
                candidates.add(new ContextBuildItemCandidate(
                        ContextItemSourceType.RAG_CHUNK,
                        sourceId,
                        content,
                        null,
                        ratio(chunk.getScore() == null ? null : chunk.getScore().doubleValue(), DEFAULT_RAG_SCORE),
                        DEFAULT_CONFIDENCE,
                        ContextSensitivity.INTERNAL,
                        new ResourceRef(ContextResourceType.DOCUMENT, resourceId, tenantId, userId,
                                json(Map.of("kbId", kbId))),
                        json(Map.of(
                                CITATION_SOURCE_FIELD, CITATION_RAG_CHUNK,
                                "intent", firstText(entry.getKey(), UNKNOWN_RESOURCE_ID),
                                "chunkId", sourceId,
                                "docId", resourceId,
                                "kbId", kbId)),
                        estimateTokens(content),
                        null));
                sequence++;
            }
        }
    }

    private String runIdForRag(StreamChatContext context) {
        TraceRunScope scope = context.getTraceRunScope();
        if (scope != null && scope.active()) {
            String traceId = trimToNull(scope.traceId());
            if (traceId != null) {
                return traceId;
            }
        }
        return generatedRunId(context.getTaskId());
    }

    private String generatedRunId(String taskId) {
        return GENERATED_RUN_PREFIX + stableIdPart(firstText(taskId, UNKNOWN_RESOURCE_ID));
    }

    private int estimateTokens(String content) {
        int length = Objects.requireNonNullElse(content, "").length();
        return Math.max(1, (int) Math.ceil(length / (double) TOKEN_CHAR_RATIO));
    }

    private double ratio(Double value, double fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return EMPTY_JSON_OBJECT;
        }
    }

    private String stableIdPart(String value) {
        return Objects.requireNonNullElse(value, UNKNOWN_RESOURCE_ID)
                .replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String firstText(String first, String fallback) {
        String value = trimToNull(first);
        return value == null ? trimToNull(fallback) : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
