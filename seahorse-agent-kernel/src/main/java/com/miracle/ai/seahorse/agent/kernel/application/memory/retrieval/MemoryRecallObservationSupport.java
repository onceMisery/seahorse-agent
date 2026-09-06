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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFusionPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 召回观测协作者：通道/融合/重排的 trace 事件与 best-effort 指标上报。
 * 只依赖 traceRecorder/observationPort/fusionPolicy；候选去重与别名解析留在主流水线。
 */
final class MemoryRecallObservationSupport {

    static final String TRACE_KEY_ALIAS_CANONICAL_ENTITY_ID = "aliasCanonicalEntityId";
    static final String TRACE_KEY_ALIAS_CONFIDENCE_LEVEL = "aliasConfidenceLevel";
    static final String TRACE_KEY_ALIAS_ENTITY_TYPE = "aliasEntityType";
    static final String FUSION_METADATA_CHANNEL_CONTRIBUTIONS = "channelContributions";
    static final String FUSION_METADATA_CHANNEL_RANKS = "channelRanks";
    static final String FUSION_METADATA_CHANNEL_SCORES = "channelScores";
    static final String FUSION_METADATA_STRATEGY = "fusionStrategy";
    static final String FUSION_METADATA_SOURCE_CHANNELS = "sourceChannels";
    static final String TRACE_COMPONENT_MEMORY_RECALL = "memory-recall";
    static final String TRACE_EVENT_CHANNEL = "channel";
    static final String TRACE_EVENT_FUSION = "fusion";
    static final String TRACE_EVENT_RERANK = "rerank";
    static final String TRACE_KEY_ACTIVE_TRACKS = "activeTracks";
    static final String TRACE_KEY_ALIAS_MATCHED = "aliasMatched";
    static final String TRACE_KEY_CANDIDATE_COUNT = "candidateCount";
    static final String TRACE_KEY_CANDIDATE_IDS = "candidateIds";
    static final String TRACE_KEY_CHANNEL = "channel";
    static final String TRACE_KEY_CHANNEL_COUNT = "channelCount";
    static final String TRACE_KEY_ERROR = "error";
    static final String TRACE_KEY_FINAL_TOP_K = "finalTopK";
    static final String TRACE_KEY_FUSED_CANDIDATE_IDS = "fusedCandidateIds";
    static final String TRACE_KEY_FUSED_COUNT = "fusedCount";
    static final String TRACE_KEY_FUSION_EXPLANATIONS = "fusionExplanations";
    static final String TRACE_KEY_INPUT_CANDIDATE_IDS = "inputCandidateIds";
    static final String TRACE_KEY_INPUT_COUNT = "inputCount";
    static final String TRACE_KEY_LATENCY_MS = "latencyMs";
    static final String TRACE_KEY_ORIGINAL_QUERY_HASH = "originalQueryHash";
    static final String TRACE_KEY_OUTPUT_CANDIDATE_IDS = "outputCandidateIds";
    static final String TRACE_KEY_OUTPUT_COUNT = "outputCount";
    static final String TRACE_KEY_QUERY_CHANGED_BY_ALIAS = "queryChangedByAlias";
    static final String TRACE_KEY_REQUEST_TOP_K = "requestTopK";
    static final String TRACE_KEY_RESOLVED_QUERY_HASH = "resolvedQueryHash";
    static final String TRACE_KEY_TIMEOUT_MS = "timeoutMs";
    static final String TRACE_SUBJECT_RECALL_CHANNEL = "recall_channel";
    static final String TRACE_SUBJECT_RECALL_FUSION = "recall_fusion";
    static final String TRACE_SUBJECT_RECALL_RERANK = "recall_rerank";
    static final String OBSERVATION_CHANNEL_EVENT = "memory-recall-channel";
    static final String OBSERVATION_FUSION_EVENT = "memory-recall-fusion";
    static final String OBSERVATION_RERANK_EVENT = "memory-recall-rerank";
    static final String OBSERVATION_ATTR_CHANNEL = "channel";
    static final String OBSERVATION_ATTR_OUTCOME = "outcome";
    static final String OBSERVATION_OUTCOME_SUCCESS = "success";
    static final String OBSERVATION_OUTCOME_TIMEOUT = "timeout";
    static final String OBSERVATION_OUTCOME_ERROR = "error";

    private final MemoryTraceRecorder traceRecorder;
    private final ObservationPort observationPort;
    private final MemoryFusionPolicy fusionPolicy;

    MemoryRecallObservationSupport(MemoryTraceRecorder traceRecorder,
                                   ObservationPort observationPort,
                                   MemoryFusionPolicy fusionPolicy) {
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
        this.fusionPolicy = Objects.requireNonNullElseGet(fusionPolicy, MemoryFusionPolicy::defaults);
    }

    private Map<String, Object> fusionExplanation(MemoryRecallCandidate candidate) {
        Map<String, Object> metadata = candidate.metadata();
        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("memoryId", candidate.memoryId());
        HybridMemoryRecallPipeline.putIfPresent(explanation, FUSION_METADATA_STRATEGY, metadata.get(FUSION_METADATA_STRATEGY));
        HybridMemoryRecallPipeline.putIfPresent(explanation, FUSION_METADATA_SOURCE_CHANNELS, metadata.get(FUSION_METADATA_SOURCE_CHANNELS));
        HybridMemoryRecallPipeline.putIfPresent(explanation, FUSION_METADATA_CHANNEL_RANKS, metadata.get(FUSION_METADATA_CHANNEL_RANKS));
        HybridMemoryRecallPipeline.putIfPresent(explanation, FUSION_METADATA_CHANNEL_SCORES, metadata.get(FUSION_METADATA_CHANNEL_SCORES));
        HybridMemoryRecallPipeline.putIfPresent(explanation, FUSION_METADATA_CHANNEL_CONTRIBUTIONS,
                metadata.get(FUSION_METADATA_CHANNEL_CONTRIBUTIONS));
        return explanation;
    }

    private List<Map<String, Object>> fusionExplanations(List<MemoryRecallCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> explanations = new ArrayList<>();
        for (MemoryRecallCandidate candidate : candidates) {
            if (candidate == null || candidate.memoryId().isBlank()) {
                continue;
            }
            explanations.add(fusionExplanation(candidate));
        }
        return explanations;
    }

    private Map<String, Object> traceDetails(HybridMemoryRecallPipeline.RecallTraceContext traceContext) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (traceContext == null) {
            return details;
        }
        details.put(TRACE_KEY_ORIGINAL_QUERY_HASH, traceContext.originalQueryHash());
        details.put(TRACE_KEY_RESOLVED_QUERY_HASH, traceContext.resolvedQueryHash());
        details.put(TRACE_KEY_QUERY_CHANGED_BY_ALIAS, traceContext.queryChangedByAlias());
        details.put(TRACE_KEY_ACTIVE_TRACKS, traceContext.activeTracks());
        details.put(TRACE_KEY_REQUEST_TOP_K, traceContext.requestTopK());
        details.put(TRACE_KEY_ALIAS_MATCHED, !traceContext.aliasDetails().isEmpty());
        details.putAll(traceContext.aliasDetails());
        return details;
    }

    private void emitStageMetric(String eventName, String outcome) {
        try {
            observationPort.recordEvent(new ObservationEvent(
                    eventName,
                    Instant.now(),
                    ObservationEvent.DEFAULT_AMOUNT,
                    Map.of(OBSERVATION_ATTR_OUTCOME, outcome)));
        } catch (RuntimeException ignored) {
            // Observation emission is best-effort and must not change recall execution semantics.
        }
    }

    void recordRecallRerank(String userId,
                                    String tenantId,
                                    List<MemoryRecallCandidate> inputCandidates,
                                    List<MemoryRecallCandidate> outputCandidates,
                                    HybridMemoryRecallPipeline.RecallTraceContext traceContext) {
        Map<String, Object> details = traceDetails(traceContext);
        details.put(TRACE_KEY_INPUT_COUNT, HybridMemoryRecallPipeline.safeCandidates(inputCandidates).size());
        details.put(TRACE_KEY_OUTPUT_COUNT, HybridMemoryRecallPipeline.safeCandidates(outputCandidates).size());
        details.put(TRACE_KEY_INPUT_CANDIDATE_IDS, HybridMemoryRecallPipeline.candidateIds(inputCandidates));
        details.put(TRACE_KEY_OUTPUT_CANDIDATE_IDS, HybridMemoryRecallPipeline.candidateIds(outputCandidates));
        traceRecorder.record(new MemoryTraceEvent(
                "",
                tenantId,
                userId,
                "",
                "",
                TRACE_COMPONENT_MEMORY_RECALL,
                TRACE_EVENT_RERANK,
                MemoryTraceEvent.STATUS_SUCCESS,
                "",
                TRACE_SUBJECT_RECALL_RERANK,
                details,
                Instant.now()));
        emitStageMetric(OBSERVATION_RERANK_EVENT, OBSERVATION_OUTCOME_SUCCESS);
    }

    void recordRecallFusion(String userId,
                                    String tenantId,
                                    List<List<MemoryRecallCandidate>> channelResults,
                                    List<MemoryRecallCandidate> fusedCandidates,
                                    int finalTopK,
                                    HybridMemoryRecallPipeline.RecallTraceContext traceContext) {
        Map<String, Object> details = traceDetails(traceContext);
        details.put(TRACE_KEY_CHANNEL_COUNT, HybridMemoryRecallPipeline.safeChannelResults(channelResults).size());
        details.put(TRACE_KEY_FUSED_COUNT, HybridMemoryRecallPipeline.safeCandidates(fusedCandidates).size());
        details.put(TRACE_KEY_FINAL_TOP_K, finalTopK);
        details.put(TRACE_KEY_INPUT_CANDIDATE_IDS, HybridMemoryRecallPipeline.candidateIdsFromChannelResults(channelResults));
        details.put(TRACE_KEY_FUSED_CANDIDATE_IDS, HybridMemoryRecallPipeline.candidateIds(fusedCandidates));
        details.put(TRACE_KEY_FUSION_EXPLANATIONS, fusionExplanations(fusedCandidates));
        traceRecorder.record(new MemoryTraceEvent(
                "",
                tenantId,
                userId,
                "",
                "",
                TRACE_COMPONENT_MEMORY_RECALL,
                TRACE_EVENT_FUSION,
                MemoryTraceEvent.STATUS_SUCCESS,
                "",
                TRACE_SUBJECT_RECALL_FUSION,
                details,
                Instant.now()));
        emitStageMetric(OBSERVATION_FUSION_EVENT, OBSERVATION_OUTCOME_SUCCESS);
    }

    private static String channelOutcome(String status, String error) {
        if (MemoryTraceEvent.STATUS_SUCCESS.equals(status)) {
            return OBSERVATION_OUTCOME_SUCCESS;
        }
        if ("timeout".equals(error)) {
            return OBSERVATION_OUTCOME_TIMEOUT;
        }
        return OBSERVATION_OUTCOME_ERROR;
    }

    private void emitChannelMetric(MemoryRecallChannelPort channel, String status, String error) {
        try {
            observationPort.recordEvent(new ObservationEvent(
                    OBSERVATION_CHANNEL_EVENT,
                    Instant.now(),
                    ObservationEvent.DEFAULT_AMOUNT,
                    Map.of(
                            OBSERVATION_ATTR_CHANNEL, Objects.requireNonNullElse(channel.channelName(), ""),
                            OBSERVATION_ATTR_OUTCOME, channelOutcome(status, error))));
        } catch (RuntimeException ignored) {
            // Observation emission is best-effort and must not change recall execution semantics.
        }
    }

    void recordRecallChannel(MemoryRecallChannelPort channel,
                                     String userId,
                                     String tenantId,
                                     List<MemoryRecallCandidate> candidates,
                                     long latencyMs,
                                     String status,
                                     String error,
                                     HybridMemoryRecallPipeline.RecallTraceContext traceContext) {
        Map<String, Object> details = traceDetails(traceContext);
        details.put(TRACE_KEY_CHANNEL, channel.channelName());
        details.put(TRACE_KEY_CANDIDATE_COUNT, HybridMemoryRecallPipeline.safeCandidates(candidates).size());
        details.put(TRACE_KEY_CANDIDATE_IDS, HybridMemoryRecallPipeline.candidateIds(candidates));
        details.put(TRACE_KEY_LATENCY_MS, latencyMs);
        details.put(TRACE_KEY_TIMEOUT_MS, fusionPolicy.channelTimeoutMillis());
        details.put(TRACE_KEY_ERROR, Objects.requireNonNullElse(error, ""));
        traceRecorder.record(new MemoryTraceEvent(
                "",
                tenantId,
                userId,
                "",
                "",
                TRACE_COMPONENT_MEMORY_RECALL,
                TRACE_EVENT_CHANNEL,
                status,
                channel.channelName(),
                TRACE_SUBJECT_RECALL_CHANNEL,
                details,
                Instant.now()));
        emitChannelMetric(channel, status, error);
    }
}
