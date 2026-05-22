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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RrfMemoryFusionTests {

    @Test
    void shouldFuseDuplicatedMemoryByChannelRank() {
        RrfMemoryFusion fusion = new RrfMemoryFusion();
        MemoryFusionPolicy policy = MemoryFusionPolicy.defaults()
                .withFinalTopK(3)
                .withTimeDecayEnabled(false);

        List<MemoryRecallCandidate> fused = fusion.fuse(List.of(
                List.of(candidate("a", "vector", 1, 0.91D), candidate("b", "vector", 2, 0.82D)),
                List.of(candidate("b", "keyword", 1, 14.0D), candidate("c", "keyword", 2, 7.0D))
        ), policy, Instant.parse("2026-05-21T00:00:00Z"));

        assertThat(fused).extracting(MemoryRecallCandidate::memoryId)
                .containsExactly("b", "a", "c");
        assertThat(fused.get(0).rawScore()).isGreaterThan(fused.get(1).rawScore());
        assertThat(fused.get(0).metadata()).containsEntry("fusionStrategy", "RRF");
        assertThat(fused.get(0).metadata()).containsEntry("rrfK", 60);
        assertThat(fused.get(0).metadata()).containsEntry("channelTimeoutMillis", 50L);
        assertThat(fused.get(0).metadata()).containsEntry("sourceChannels", List.of("vector", "keyword"));
        assertThat(fused.get(0).metadata().get("channelRanks"))
                .isEqualTo(Map.of("vector", 2, "keyword", 1));
    }

    @Test
    void shouldFilterInactiveStatusesAndApplyTimeDecay() {
        RrfMemoryFusion fusion = new RrfMemoryFusion();
        MemoryFusionPolicy policy = MemoryFusionPolicy.defaults()
                .withFinalTopK(3)
                .withDecayLambda(1.0D);

        Instant now = Instant.parse("2026-05-21T00:00:00Z");
        MemoryRecallCandidate oldTopRank = candidate(
                "old",
                "keyword",
                1,
                20.0D,
                "ACTIVE",
                Map.of("lastReferencedAt", "2025-05-21T00:00:00Z"));
        MemoryRecallCandidate freshSecondRank = candidate(
                "fresh",
                "keyword",
                2,
                10.0D,
                "ACTIVE",
                Map.of("lastReferencedAt", "2026-05-21T00:00:00Z"));
        MemoryRecallCandidate obsolete = candidate(
                "obsolete",
                "keyword",
                3,
                30.0D,
                "OBSOLETE",
                Map.of("lastReferencedAt", "2026-05-21T00:00:00Z"));

        List<MemoryRecallCandidate> fused = fusion.fuse(List.of(
                List.of(oldTopRank, freshSecondRank, obsolete)
        ), policy, now);

        assertThat(fused).extracting(MemoryRecallCandidate::memoryId)
                .containsExactly("fresh", "old");
        assertThat(fused).noneMatch(candidate -> "obsolete".equals(candidate.memoryId()));
        assertThat(fused.get(0).metadata()).containsEntry("timeDecayEnabled", true);
    }

    private MemoryRecallCandidate candidate(String memoryId, String channel, int rank, double score) {
        return candidate(memoryId, channel, rank, score, "ACTIVE", Map.of());
    }

    private MemoryRecallCandidate candidate(String memoryId,
                                            String channel,
                                            int rank,
                                            double score,
                                            String status,
                                            Map<String, Object> metadata) {
        return new MemoryRecallCandidate(
                memoryId,
                channel,
                rank,
                score,
                "user-1",
                "default",
                "SEMANTIC",
                "PROJECT_FACT",
                "content-" + memoryId,
                "generation-1",
                status,
                metadata);
    }
}
