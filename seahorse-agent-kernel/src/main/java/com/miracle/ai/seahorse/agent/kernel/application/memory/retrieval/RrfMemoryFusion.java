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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFusionPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallFusionPort;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RrfMemoryFusion implements MemoryRecallFusionPort {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_COLD = "COLD";
    private static final String STATUS_OBSOLETE = "OBSOLETE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_COMPACTED = "COMPACTED";
    private static final String METADATA_LAST_REFERENCED_AT = "lastReferencedAt";
    private static final String METADATA_UPDATED_AT = "updatedAt";

    @Override
    public List<MemoryRecallCandidate> fuse(List<List<MemoryRecallCandidate>> channelResults,
                                            MemoryFusionPolicy policy,
                                            Instant now) {
        MemoryFusionPolicy effectivePolicy = Objects.requireNonNullElseGet(policy, MemoryFusionPolicy::defaults);
        Instant effectiveNow = Objects.requireNonNullElseGet(now, Instant::now);
        Map<String, MemoryRecallCandidate> winners = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> channelRanks = new LinkedHashMap<>();
        Map<String, Map<String, Double>> channelScores = new LinkedHashMap<>();
        Map<String, Map<String, Double>> channelContributions = new LinkedHashMap<>();
        Map<String, Set<String>> sourceChannels = new LinkedHashMap<>();
        if (channelResults == null || channelResults.isEmpty()) {
            return List.of();
        }
        for (List<MemoryRecallCandidate> channelResult : channelResults) {
            if (channelResult == null || channelResult.isEmpty()) {
                continue;
            }
            int fallbackRank = 0;
            for (MemoryRecallCandidate candidate : channelResult) {
                if (candidate == null || candidate.memoryId().isBlank() || inactive(candidate)) {
                    continue;
                }
                fallbackRank++;
                String key = candidate.memoryId();
                String channel = channel(candidate);
                int rank = candidate.rank() > 0 ? candidate.rank() : fallbackRank;
                double contribution = channelWeight(channel, effectivePolicy) / (effectivePolicy.rrfK() + rank);
                double decayFactor = decayFactor(candidate, effectivePolicy, effectiveNow);
                double finalContribution = contribution * decayFactor;
                winners.merge(key, candidate, this::preferCandidate);
                scores.merge(key, finalContribution, Double::sum);
                channelRanks.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(channel, rank);
                channelScores.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(channel, candidate.rawScore());
                channelContributions.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                        .merge(channel, finalContribution, Double::sum);
                sourceChannels.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(channel);
            }
        }
        return winners.entrySet().stream()
                .map(entry -> enrich(
                        entry.getValue(),
                        scores.getOrDefault(entry.getKey(), 0D),
                        effectivePolicy,
                        channelRanks.getOrDefault(entry.getKey(), Map.of()),
                        channelScores.getOrDefault(entry.getKey(), Map.of()),
                        channelContributions.getOrDefault(entry.getKey(), Map.of()),
                        sourceChannels.getOrDefault(entry.getKey(), Set.of())))
                .sorted(Comparator.comparing(MemoryRecallCandidate::rawScore).reversed()
                        .thenComparing(MemoryRecallCandidate::memoryId))
                .limit(effectivePolicy.finalTopK())
                .toList();
    }

    private boolean inactive(MemoryRecallCandidate candidate) {
        String status = candidate.status().toUpperCase(Locale.ROOT);
        return STATUS_OBSOLETE.equals(status)
                || STATUS_ARCHIVED.equals(status)
                || STATUS_DELETED.equals(status)
                || STATUS_COMPACTED.equals(status)
                || STATUS_COLD.equals(status);
    }

    private MemoryRecallCandidate preferCandidate(MemoryRecallCandidate current, MemoryRecallCandidate next) {
        if (STATUS_ACTIVE.equalsIgnoreCase(next.status()) && !STATUS_ACTIVE.equalsIgnoreCase(current.status())) {
            return next;
        }
        if (!next.content().isBlank() && current.content().isBlank()) {
            return next;
        }
        return next.rank() < current.rank() ? next : current;
    }

    private double channelWeight(String channel, MemoryFusionPolicy policy) {
        Double weight = policy.channelWeights().get(channel);
        return weight == null || weight <= 0D ? 1D : weight;
    }

    private double decayFactor(MemoryRecallCandidate candidate, MemoryFusionPolicy policy, Instant now) {
        if (!policy.timeDecayEnabled()) {
            return 1D;
        }
        Instant referencedAt = instant(candidate.metadata().get(METADATA_LAST_REFERENCED_AT));
        if (referencedAt == null) {
            referencedAt = instant(candidate.metadata().get(METADATA_UPDATED_AT));
        }
        if (referencedAt == null) {
            return 1D;
        }
        long days = Math.max(0L, Duration.between(referencedAt, now).toDays());
        return Math.exp(-policy.decayLambda() * Math.log(days + 1D));
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(Objects.toString(value));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private MemoryRecallCandidate enrich(MemoryRecallCandidate candidate,
                                         double score,
                                         MemoryFusionPolicy policy,
                                         Map<String, Integer> channelRanks,
                                         Map<String, Double> channelScores,
                                         Map<String, Double> channelContributions,
                                         Set<String> sourceChannels) {
        Map<String, Object> metadata = new LinkedHashMap<>(candidate.metadata());
        metadata.put("fusionStrategy", "RRF");
        metadata.put("rrfK", policy.rrfK());
        metadata.put("fusionScore", score);
        metadata.put("timeDecayEnabled", policy.timeDecayEnabled());
        metadata.put("channelTimeoutMillis", policy.channelTimeoutMillis());
        metadata.put("channelRanks", new LinkedHashMap<>(channelRanks));
        metadata.put("channelScores", new LinkedHashMap<>(channelScores));
        metadata.put("channelContributions", new LinkedHashMap<>(channelContributions));
        metadata.put("sourceChannels", new ArrayList<>(sourceChannels));
        return candidate.withRawScore(score).withMetadata(metadata);
    }

    private String channel(MemoryRecallCandidate candidate) {
        return candidate.channel().isBlank() ? "unknown" : candidate.channel();
    }
}
