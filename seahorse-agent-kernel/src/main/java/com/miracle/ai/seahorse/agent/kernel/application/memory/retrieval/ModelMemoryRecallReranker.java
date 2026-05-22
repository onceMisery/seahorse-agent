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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRerankerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModelMemoryRecallReranker implements MemoryRecallRerankerPort {

    public static final int DEFAULT_INPUT_TOP_K = 8;
    public static final int DEFAULT_MAX_TEXT_CHARS = 4000;

    private final RerankModelPort rerankModelPort;
    private final String modelId;
    private final int inputTopK;
    private final int maxTextChars;

    public ModelMemoryRecallReranker(RerankModelPort rerankModelPort, String modelId) {
        this(rerankModelPort, modelId, DEFAULT_INPUT_TOP_K, DEFAULT_MAX_TEXT_CHARS);
    }

    public ModelMemoryRecallReranker(RerankModelPort rerankModelPort,
                                     String modelId,
                                     int inputTopK,
                                     int maxTextChars) {
        this.rerankModelPort = Objects.requireNonNullElseGet(rerankModelPort, RerankModelPort::noop);
        this.modelId = Objects.requireNonNullElse(modelId, "").trim();
        this.inputTopK = Math.max(1, inputTopK);
        this.maxTextChars = Math.max(1, maxTextChars);
    }

    @Override
    public List<MemoryRecallCandidate> rerank(MemoryRecallRequest request, List<MemoryRecallCandidate> candidates) {
        List<MemoryRecallCandidate> safeCandidates = candidates == null ? List.of() : candidates.stream()
                .filter(Objects::nonNull)
                .toList();
        if (safeCandidates.isEmpty() || modelId.isBlank()) {
            return safeCandidates;
        }
        List<MemoryRecallCandidate> inputCandidates = safeCandidates.stream()
                .limit(inputTopK)
                .toList();
        List<RetrievedChunk> chunks = inputCandidates.stream()
                .map(this::toChunk)
                .toList();
        List<RetrievedChunk> reranked = rerankModelPort.rerank(
                modelId,
                request == null ? "" : request.query(),
                chunks);
        List<MemoryRecallCandidate> normalized = normalize(inputCandidates, reranked);
        return normalized.isEmpty() ? safeCandidates : normalized;
    }

    private RetrievedChunk toChunk(MemoryRecallCandidate candidate) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setId(candidate.memoryId());
        chunk.setText(truncate(candidate.content()));
        chunk.setScore((float) candidate.rawScore());
        chunk.setRerankScore((float) candidate.rawScore());
        chunk.setTenantId(candidate.tenantId());
        chunk.getMetadata().put("memoryId", candidate.memoryId());
        chunk.getMetadata().put("channel", candidate.channel());
        chunk.getMetadata().put("layer", candidate.layer());
        chunk.getMetadata().put("type", candidate.type());
        chunk.getMetadata().putAll(candidate.metadata());
        chunk.getChannelScores().put(candidate.channel(), (float) candidate.rawScore());
        chunk.getChannelRanks().put(candidate.channel(), candidate.rank());
        return chunk;
    }

    private List<MemoryRecallCandidate> normalize(List<MemoryRecallCandidate> inputCandidates,
                                                  List<RetrievedChunk> reranked) {
        if (reranked == null || reranked.isEmpty()) {
            return List.of();
        }
        Map<String, MemoryRecallCandidate> candidatesById = new LinkedHashMap<>();
        for (MemoryRecallCandidate candidate : inputCandidates) {
            candidatesById.putIfAbsent(candidate.memoryId(), candidate);
        }
        List<MemoryRecallCandidate> normalized = new ArrayList<>();
        for (RetrievedChunk chunk : reranked) {
            MemoryRecallCandidate candidate = merge(candidatesById.get(chunkId(chunk)), chunk, normalized.size() + 1);
            if (candidate != null) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private MemoryRecallCandidate merge(MemoryRecallCandidate original, RetrievedChunk reranked, int newRank) {
        if (original == null) {
            return null;
        }
        Float score = firstNonNull(reranked.getRerankScore(), reranked.getScore());
        return original.withRankAndScore(newRank, score == null ? original.rawScore() : score.doubleValue());
    }

    private String chunkId(RetrievedChunk chunk) {
        if (chunk == null) {
            return "";
        }
        String id = Objects.requireNonNullElse(chunk.getId(), "").trim();
        if (!id.isBlank()) {
            return id;
        }
        Object memoryId = chunk.getMetadata().get("memoryId");
        return Objects.requireNonNullElse(memoryId, "").toString();
    }

    private Float firstNonNull(Float first, Float second) {
        return first != null ? first : second;
    }

    private String truncate(String value) {
        String normalized = Objects.requireNonNullElse(value, "");
        return normalized.length() <= maxTextChars ? normalized : normalized.substring(0, maxTextChars);
    }
}
