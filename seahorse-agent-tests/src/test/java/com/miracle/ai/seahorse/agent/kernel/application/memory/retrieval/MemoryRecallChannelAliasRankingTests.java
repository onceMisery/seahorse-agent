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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryRecallChannelAliasRankingTests {

    @Test
    void shouldPromoteKeywordHitMatchingAliasCanonicalEntity() {
        KeywordMemoryRecallChannel channel = new KeywordMemoryRecallChannel((userId, tenantId, query, topK) -> List.of(
                keywordHit("memory-docker", 18.0D, Map.of(
                        "type", "PROJECT_FACT",
                        "canonicalEntityId", "entity-docker",
                        "canonicalName", "Docker",
                        "entityType", "TECHNOLOGY")),
                keywordHit("memory-kubernetes", 12.0D, Map.of(
                        "type", "PROJECT_FACT",
                        "canonicalEntityId", "entity-kubernetes",
                        "canonicalName", "Kubernetes",
                        "entityType", "TECHNOLOGY"))));

        List<MemoryRecallCandidate> candidates = channel.recall(aliasRequest());

        assertThat(candidates).extracting(MemoryRecallCandidate::memoryId)
                .containsExactly("memory-kubernetes", "memory-docker");
        assertThat(candidates).extracting(MemoryRecallCandidate::rank)
                .containsExactly(1, 2);
        assertThat(candidates.get(0).metadata())
                .containsEntry("aliasFilterMatched", true)
                .containsEntry("aliasOriginalRank", 2);
        assertThat(candidates.get(0).metadata().get("aliasMatchedFields"))
                .isEqualTo(List.of("canonicalEntityId", "canonicalName", "entityType"));
    }

    @Test
    void shouldPromoteVectorHitMatchingAliasCanonicalEntity() {
        VectorMemoryRecallChannel channel = new VectorMemoryRecallChannel((userId, tenantId, query, topK) -> List.of(
                vectorHit("vector-docker", 0.94D, Map.of(
                        "layer", "SEMANTIC",
                        "type", "PROJECT_FACT",
                        "canonicalEntityId", "entity-docker",
                        "canonicalName", "Docker",
                        "canonicalEntityType", "TECHNOLOGY")),
                vectorHit("vector-kubernetes", 0.82D, Map.of(
                        "layer", "SEMANTIC",
                        "type", "PROJECT_FACT",
                        "canonicalEntityId", "entity-kubernetes",
                        "canonicalName", "Kubernetes",
                        "canonicalEntityType", "TECHNOLOGY"))));

        List<MemoryRecallCandidate> candidates = channel.recall(aliasRequest());

        assertThat(candidates).extracting(MemoryRecallCandidate::memoryId)
                .containsExactly("vector-kubernetes", "vector-docker");
        assertThat(candidates).extracting(MemoryRecallCandidate::rank)
                .containsExactly(1, 2);
        assertThat(candidates.get(0).metadata())
                .containsEntry("aliasFilterMatched", true)
                .containsEntry("aliasOriginalRank", 2)
                .containsEntry("embeddingModel", "memory-embedding-v1");
        assertThat(candidates.get(0).metadata().get("aliasMatchedFields"))
                .isEqualTo(List.of("canonicalEntityId", "canonicalName", "entityType"));
    }

    @Test
    void shouldKeepKeywordRankingWhenAliasFiltersAreAbsent() {
        KeywordMemoryRecallChannel channel = new KeywordMemoryRecallChannel((userId, tenantId, query, topK) -> List.of(
                keywordHit("memory-docker", 18.0D, Map.of("canonicalEntityId", "entity-docker")),
                keywordHit("memory-kubernetes", 12.0D, Map.of("canonicalEntityId", "entity-kubernetes"))));

        List<MemoryRecallCandidate> candidates = channel.recall(new MemoryRecallRequest(
                "user-1",
                "default",
                "K8s",
                null,
                5,
                Map.of()));

        assertThat(candidates).extracting(MemoryRecallCandidate::memoryId)
                .containsExactly("memory-docker", "memory-kubernetes");
        assertThat(candidates).extracting(MemoryRecallCandidate::rank)
                .containsExactly(1, 2);
        assertThat(candidates.get(0).metadata()).doesNotContainKey("aliasFilterMatched");
    }

    @Test
    void shouldNotPromoteKeywordHitWhenAliasEntityTypeConflicts() {
        KeywordMemoryRecallChannel channel = new KeywordMemoryRecallChannel((userId, tenantId, query, topK) -> List.of(
                keywordHit("memory-person", 18.0D, Map.of(
                        "canonicalEntityId", "entity-kubernetes",
                        "canonicalName", "Kubernetes",
                        "entityType", "PERSON")),
                keywordHit("memory-docker", 12.0D, Map.of(
                        "canonicalEntityId", "entity-docker",
                        "canonicalName", "Docker",
                        "entityType", "TECHNOLOGY"))));

        List<MemoryRecallCandidate> candidates = channel.recall(aliasRequest());

        assertThat(candidates).extracting(MemoryRecallCandidate::memoryId)
                .containsExactly("memory-person", "memory-docker");
        assertThat(candidates.get(0).metadata()).doesNotContainKey("aliasFilterMatched");
    }

    private MemoryRecallRequest aliasRequest() {
        return new MemoryRecallRequest(
                "user-1",
                "default",
                "K8s Kubernetes entity-kubernetes",
                null,
                5,
                Map.of(
                        "memoryAliasCanonicalEntityId", "entity-kubernetes",
                        "memoryAliasCanonicalName", "Kubernetes",
                        "memoryAliasEntityType", "TECHNOLOGY"));
    }

    private MemoryKeywordSearchPort.MemoryKeywordHit keywordHit(String memoryId,
                                                               double score,
                                                               Map<String, Object> metadata) {
        return new MemoryKeywordSearchPort.MemoryKeywordHit(memoryId, score, "SEMANTIC", metadata);
    }

    private ScoredMemoryVectorHit vectorHit(String memoryId, double score, Map<String, Object> metadata) {
        return new ScoredMemoryVectorHit(memoryId, score, "generation-1", "memory-embedding-v1", metadata);
    }
}
