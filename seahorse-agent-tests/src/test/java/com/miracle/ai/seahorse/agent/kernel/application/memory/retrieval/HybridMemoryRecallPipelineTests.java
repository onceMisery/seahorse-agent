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
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryRouter;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFusionPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRerankerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorHit;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HybridMemoryRecallPipelineTests {

    @Test
    void shouldFuseChannelsAndReturnExistingMemoryContextZones() {
        RecordingMemoryStore shortTerm = new RecordingMemoryStore();
        RecordingMemoryStore longTerm = new RecordingMemoryStore();
        RecordingMemoryStore semantic = new RecordingMemoryStore();
        semantic.save(record("semantic-pip", "SEMANTIC", "Pulsar PIP-459 failed because compatibility broke."));
        semantic.save(record("semantic-pip-extra", "SEMANTIC", "PIP-459 rollback note."));
        longTerm.save(record("long-design", "LONG_TERM", "User designed a memory recall pipeline."));

        HybridMemoryRecallPipeline pipeline = pipeline(
                shortTerm,
                longTerm,
                semantic,
                List.of(
                        channel("vector", List.of(
                                candidate("long-design", "vector", 1, 0.91D, "LONG_TERM"),
                                candidate("semantic-pip", "vector", 2, 0.72D, "SEMANTIC"))),
                        channel("keyword", List.of(
                                candidate("semantic-pip", "keyword", 1, 15.0D, "SEMANTIC"),
                                candidate("semantic-pip-extra", "keyword", 2, 9.0D, "SEMANTIC")))));

        MemoryContext context = pipeline.load(MemoryLoadRequest.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion("Pulsar PIP-459")
                .build());

        assertThat(context.getLongTermMemories()).extracting(MemoryItem::getId)
                .containsExactly("long-design");
        assertThat(context.getSemanticMemories()).extracting(MemoryItem::getId)
                .containsExactly("semantic-pip", "semantic-pip-extra");
        assertThat(context.getSemanticMemories().get(0).getRelevanceScore())
                .isGreaterThan(context.getLongTermMemories().get(0).getRelevanceScore());
        assertThat(context.getShortTermMemories()).isEmpty();
        assertThat(context.getBusinessDocumentMemories()).isEmpty();
        assertThat(semantic.recordReadIds).containsExactly("semantic-pip", "semantic-pip-extra");
        assertThat(longTerm.recordReadIds).containsExactly("long-design");
    }

    @Test
    void shouldContinueWhenOneRecallChannelFails() {
        RecordingMemoryStore semantic = new RecordingMemoryStore();
        semantic.save(record("semantic-keyword", "SEMANTIC", "Keyword fallback memory."));
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        MemoryRecallChannelPort failing = new MemoryRecallChannelPort() {
            @Override
            public String channelName() {
                return "vector";
            }

            @Override
            public List<MemoryRecallCandidate> recall(MemoryRecallRequest request) {
                throw new IllegalStateException("vector unavailable");
            }
        };

        HybridMemoryRecallPipeline pipeline = pipeline(
                new RecordingMemoryStore(),
                new RecordingMemoryStore(),
                semantic,
                List.of(
                        failing,
                        channel("keyword", List.of(candidate("semantic-keyword", "keyword", 1, 3.0D, "SEMANTIC")))),
                traceRecorder);

        MemoryContext context = pipeline.load(MemoryLoadRequest.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion("fallback")
                .build());

        assertThat(context.getSemanticMemories()).extracting(MemoryItem::getId)
                .containsExactly("semantic-keyword");
        assertThat(traceRecorder.events)
                .anySatisfy(event -> {
                    assertThat(event.component()).isEqualTo("memory-recall");
                    assertThat(event.eventType()).isEqualTo("channel");
                    assertThat(event.status()).isEqualTo(MemoryTraceEvent.STATUS_FAILED);
                    assertThat(event.subjectId()).isEqualTo("vector");
                    assertThat(event.details()).containsEntry("candidateCount", 0);
                    assertThat(event.details()).containsEntry("timeoutMs", 50L);
                })
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("channel");
                    assertThat(event.status()).isEqualTo(MemoryTraceEvent.STATUS_SUCCESS);
                    assertThat(event.subjectId()).isEqualTo("keyword");
                    assertThat(event.details()).containsEntry("candidateCount", 1);
                })
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("fusion");
                    assertThat(event.details()).containsEntry("channelCount", 2);
                    assertThat(event.details()).containsEntry("fusedCount", 1);
                    assertThat(event.details()).containsEntry("finalTopK", 5);
                });
    }

    @Test
    void shouldTimeoutSlowRecallChannelAndFuseRemainingChannels() {
        RecordingMemoryStore semantic = new RecordingMemoryStore();
        semantic.save(record("semantic-fast", "SEMANTIC", "Fast keyword memory."));
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        MemoryRecallChannelPort slow = new MemoryRecallChannelPort() {
            @Override
            public String channelName() {
                return "slow-vector";
            }

            @Override
            public List<MemoryRecallCandidate> recall(MemoryRecallRequest request) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return List.of(candidate("semantic-slow", "slow-vector", 1, 9.0D, "SEMANTIC"));
            }
        };

        HybridMemoryRecallPipeline pipeline = pipeline(
                new RecordingMemoryStore(),
                new RecordingMemoryStore(),
                semantic,
                List.of(
                        slow,
                        channel("keyword", List.of(candidate("semantic-fast", "keyword", 1, 5.0D, "SEMANTIC")))),
                traceRecorder,
                MemoryFusionPolicy.defaults()
                        .withFinalTopK(5)
                        .withTimeDecayEnabled(false)
                        .withChannelTimeoutMillis(20L));

        MemoryContext context = pipeline.load(MemoryLoadRequest.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion("fast")
                .build());

        assertThat(context.getSemanticMemories()).extracting(MemoryItem::getId)
                .containsExactly("semantic-fast");
        assertThat(traceRecorder.events)
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("channel");
                    assertThat(event.status()).isEqualTo(MemoryTraceEvent.STATUS_FAILED);
                    assertThat(event.subjectId()).isEqualTo("slow-vector");
                    assertThat(event.details()).containsEntry("candidateCount", 0);
                    assertThat(event.details()).containsEntry("timeoutMs", 20L);
                    assertThat(event.details()).containsEntry("error", "timeout");
                })
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("channel");
                    assertThat(event.status()).isEqualTo(MemoryTraceEvent.STATUS_SUCCESS);
                    assertThat(event.subjectId()).isEqualTo("keyword");
                    assertThat(event.details()).containsEntry("candidateCount", 1);
                });
    }

    @Test
    void shouldSkipCandidateWhenGenerationDoesNotMatchActiveRecord() {
        RecordingMemoryStore semantic = new RecordingMemoryStore();
        semantic.save(new MemoryRecord(
                "semantic-profile",
                "SEMANTIC",
                "PROFILE",
                "active occupation is teacher",
                Map.of("userId", "user-1", "generationId", "identity.occupation:g2"),
                Instant.parse("2026-05-21T00:00:00Z")));
        HybridMemoryRecallPipeline pipeline = pipeline(
                new RecordingMemoryStore(),
                new RecordingMemoryStore(),
                semantic,
                List.of(channel("keyword", List.of(new MemoryRecallCandidate(
                        "semantic-profile",
                        "keyword",
                        1,
                        10.0D,
                        "user-1",
                        "default",
                        "SEMANTIC",
                        "PROFILE",
                        "",
                        "identity.occupation:g1",
                        "ACTIVE",
                        Map.of())))));

        MemoryContext context = pipeline.load(MemoryLoadRequest.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion("occupation")
                .build());

        assertThat(context.getSemanticMemories()).isEmpty();
    }

    @Test
    void shouldCanonicalizeRecallQueryWhenAliasMatches() {
        RecordingRecallChannel channel = new RecordingRecallChannel("keyword", List.of());
        HybridMemoryRecallPipeline pipeline = pipeline(
                new RecordingMemoryStore(),
                new RecordingMemoryStore(),
                new RecordingMemoryStore(),
                List.of(channel),
                MemoryTraceRecorder.noop(),
                MemoryFusionPolicy.defaults().withFinalTopK(5).withTimeDecayEnabled(false),
                new StaticAliasPort(new MemoryAliasResolution(
                        "K8s",
                        "k8s",
                        "ent-core-k8s",
                        "Kubernetes",
                        "TECHNOLOGY",
                        0.98D)));

        pipeline.load(MemoryLoadRequest.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion("K8s")
                .build());

        assertThat(channel.requests).hasSize(1);
        MemoryRecallRequest request = channel.requests.get(0);
        assertThat(request.query()).contains("K8s", "Kubernetes", "ent-core-k8s");
        assertThat(request.filters())
                .containsEntry("memoryAliasCanonicalEntityId", "ent-core-k8s")
                .containsEntry("memoryAliasCanonicalName", "Kubernetes")
                .containsEntry("memoryAliasEntityType", "TECHNOLOGY");
    }

    @Test
    void shouldRecordRecallTraceExplanationWithoutRawQuery() {
        RecordingMemoryStore semantic = new RecordingMemoryStore();
        semantic.save(record("semantic-k8s", "SEMANTIC", "Kubernetes project note."));
        semantic.save(record("semantic-project", "SEMANTIC", "Previous project decision."));
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        RecordingReranker reranker = new RecordingReranker(List.of(
                candidate("semantic-project", "reranker", 1, 0.99D, "SEMANTIC"),
                candidate("semantic-k8s", "reranker", 2, 0.75D, "SEMANTIC")));
        String rawQuery = "previous K8s project";

        HybridMemoryRecallPipeline pipeline = pipeline(
                new RecordingMemoryStore(),
                new RecordingMemoryStore(),
                semantic,
                List.of(channel("keyword", List.of(
                        candidate("semantic-k8s", "keyword", 1, 12.0D, "SEMANTIC"),
                        candidate("semantic-project", "keyword", 2, 8.0D, "SEMANTIC")))),
                traceRecorder,
                MemoryFusionPolicy.defaults().withFinalTopK(5).withTimeDecayEnabled(false),
                new StaticAliasPort(new MemoryAliasResolution(
                        "K8s",
                        "k8s",
                        "ent-core-k8s",
                        "Kubernetes",
                        "TECHNOLOGY",
                        0.98D)),
                reranker);

        pipeline.load(MemoryLoadRequest.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion(rawQuery)
                .build());

        assertThat(traceRecorder.events)
                .filteredOn(event -> "channel".equals(event.eventType()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.details())
                            .containsEntry("candidateIds", List.of("semantic-k8s", "semantic-project"))
                            .containsEntry("requestTopK", 10)
                            .containsEntry("queryChangedByAlias", true)
                            .containsEntry("aliasCanonicalEntityId", "ent-core-k8s")
                            .containsEntry("aliasEntityType", "TECHNOLOGY");
                    assertThat(event.details().get("activeTracks"))
                            .asList()
                            .contains("CORRECTION", "EPISODIC", "PROFILE", "SHORT_WINDOW");
                    assertThat(event.details().get("originalQueryHash"))
                            .asString()
                            .matches("[0-9a-f]{64}");
                    assertThat(event.details().get("resolvedQueryHash"))
                            .asString()
                            .matches("[0-9a-f]{64}");
                    assertThat(event.details()).doesNotContainValue(rawQuery);
                });
        assertThat(traceRecorder.events)
                .filteredOn(event -> "fusion".equals(event.eventType()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.details())
                            .containsEntry("inputCandidateIds", List.of("semantic-k8s", "semantic-project"))
                            .containsEntry("fusedCandidateIds", List.of("semantic-k8s", "semantic-project"))
                            .containsEntry("queryChangedByAlias", true);
                    List<Map<String, Object>> explanations = fusionExplanations(event);
                    assertThat(explanations).hasSize(2);
                    assertThat(explanations.get(0))
                            .containsEntry("memoryId", "semantic-k8s")
                            .containsEntry("fusionStrategy", "RRF")
                            .containsEntry("sourceChannels", List.of("keyword"))
                            .containsEntry("channelRanks", Map.of("keyword", 1));
                    assertThat(explanations.get(0).get("channelContributions"))
                            .isInstanceOf(Map.class);
                    assertThat(event.details()).doesNotContainValue(rawQuery);
                });
        assertThat(traceRecorder.events)
                .filteredOn(event -> "rerank".equals(event.eventType()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.details())
                            .containsEntry("inputCandidateIds", List.of("semantic-k8s", "semantic-project"))
                            .containsEntry("outputCandidateIds", List.of("semantic-project", "semantic-k8s"))
                            .containsEntry("queryChangedByAlias", true);
                    assertThat(event.details()).doesNotContainValue(rawQuery);
                });
    }

    @Test
    void shouldRerankFusedRecallCandidatesBeforeMaterializingMemoryItems() {
        RecordingMemoryStore semantic = new RecordingMemoryStore();
        semantic.save(record("semantic-first", "SEMANTIC", "First fused memory."));
        semantic.save(record("semantic-reranked", "SEMANTIC", "Reranked memory."));
        RecordingReranker reranker = new RecordingReranker(List.of(
                candidate("semantic-reranked", "reranker", 1, 0.98D, "SEMANTIC"),
                candidate("semantic-first", "reranker", 2, 0.41D, "SEMANTIC")));

        HybridMemoryRecallPipeline pipeline = pipeline(
                new RecordingMemoryStore(),
                new RecordingMemoryStore(),
                semantic,
                List.of(channel("semantic", List.of(
                        candidate("semantic-first", "semantic", 1, 0.9D, "SEMANTIC"),
                        candidate("semantic-reranked", "semantic", 2, 0.8D, "SEMANTIC")))),
                MemoryTraceRecorder.noop(),
                MemoryFusionPolicy.defaults().withFinalTopK(5).withTimeDecayEnabled(false),
                MemoryAliasPort.noop(),
                reranker);

        MemoryContext context = pipeline.load(MemoryLoadRequest.builder()
                .conversationId("conv-1")
                .userId("user-1")
                .currentQuestion("rerank")
                .build());

        assertThat(reranker.requests).hasSize(1);
        assertThat(reranker.inputs.get(0)).extracting(MemoryRecallCandidate::memoryId)
                .containsExactly("semantic-first", "semantic-reranked");
        assertThat(reranker.inputs.get(0)).extracting(MemoryRecallCandidate::content)
                .containsExactly("First fused memory.", "Reranked memory.");
        assertThat(context.getSemanticMemories()).extracting(MemoryItem::getId)
                .containsExactly("semantic-reranked", "semantic-first");
        assertThat(context.getSemanticMemories()).extracting(MemoryItem::getRelevanceScore)
                .containsExactly(0.98D, 0.41D);
    }

    @Test
    void shouldEnrichLegacyVectorHitsFromLayeredStores() {
        RecordingMemoryStore shortTerm = new RecordingMemoryStore();
        RecordingMemoryStore longTerm = new RecordingMemoryStore();
        RecordingMemoryStore semantic = new RecordingMemoryStore();
        semantic.save(new MemoryRecord(
                "semantic-1",
                "SEMANTIC",
                "PROJECT_FACT",
                "User prefers concise answers.",
                Map.of(
                        "userId", "user-1",
                        "tenantId", "tenant-1",
                        "status", "REFERENCED",
                        "generationId", "generation-9",
                        "importanceScore", 0.7D,
                        "confidenceLevel", 0.8D,
                        "lastReferencedAt", "2026-05-21T09:00:00Z"),
                Instant.parse("2026-05-20T08:00:00Z")));
        MemoryVectorPort legacyVector = new StaticMemoryVectorPort(List.of("missing", "semantic-1"));
        LayeredScoredMemoryVectorPort port = new LayeredScoredMemoryVectorPort(
                legacyVector,
                shortTerm,
                longTerm,
                semantic);

        List<ScoredMemoryVectorHit> hits = port.search("user-1", "tenant-1", "concise", 5);

        assertThat(hits).hasSize(1);
        ScoredMemoryVectorHit hit = hits.get(0);
        assertThat(hit.memoryId()).isEqualTo("semantic-1");
        assertThat(hit.generationId()).isEqualTo("generation-9");
        assertThat(hit.metadata())
                .containsEntry("layer", "SEMANTIC")
                .containsEntry("type", "PROJECT_FACT")
                .containsEntry("status", "REFERENCED")
                .containsEntry("updatedAt", "2026-05-20T08:00:00Z")
                .containsEntry("lastReferencedAt", "2026-05-21T09:00:00Z");
    }

    private HybridMemoryRecallPipeline pipeline(RecordingMemoryStore shortTerm,
                                                RecordingMemoryStore longTerm,
                                                RecordingMemoryStore semantic,
                                                List<MemoryRecallChannelPort> channels) {
        return pipeline(shortTerm, longTerm, semantic, channels, MemoryTraceRecorder.noop());
    }

    private HybridMemoryRecallPipeline pipeline(RecordingMemoryStore shortTerm,
                                                RecordingMemoryStore longTerm,
                                                RecordingMemoryStore semantic,
                                                List<MemoryRecallChannelPort> channels,
                                                MemoryTraceRecorder traceRecorder) {
        return pipeline(shortTerm,
                longTerm,
                semantic,
                channels,
                traceRecorder,
                MemoryFusionPolicy.defaults().withFinalTopK(5).withTimeDecayEnabled(false));
    }

    private HybridMemoryRecallPipeline pipeline(RecordingMemoryStore shortTerm,
                                                RecordingMemoryStore longTerm,
                                                RecordingMemoryStore semantic,
                                                List<MemoryRecallChannelPort> channels,
                                                MemoryTraceRecorder traceRecorder,
                                                MemoryFusionPolicy fusionPolicy) {
        return pipeline(shortTerm, longTerm, semantic, channels, traceRecorder, fusionPolicy, MemoryAliasPort.noop());
    }

    private HybridMemoryRecallPipeline pipeline(RecordingMemoryStore shortTerm,
                                                RecordingMemoryStore longTerm,
                                                RecordingMemoryStore semantic,
                                                List<MemoryRecallChannelPort> channels,
                                                MemoryTraceRecorder traceRecorder,
                                                MemoryFusionPolicy fusionPolicy,
                                                MemoryAliasPort aliasPort) {
        return pipeline(shortTerm,
                longTerm,
                semantic,
                channels,
                traceRecorder,
                fusionPolicy,
                aliasPort,
                MemoryRecallRerankerPort.noop());
    }

    private HybridMemoryRecallPipeline pipeline(RecordingMemoryStore shortTerm,
                                                RecordingMemoryStore longTerm,
                                                RecordingMemoryStore semantic,
                                                List<MemoryRecallChannelPort> channels,
                                                MemoryTraceRecorder traceRecorder,
                                                MemoryFusionPolicy fusionPolicy,
                                                MemoryAliasPort aliasPort,
                                                MemoryRecallRerankerPort recallRerankerPort) {
        return new HybridMemoryRecallPipeline(
                shortTerm,
                longTerm,
                semantic,
                new ObjectMapper(),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryBusinessDocumentRetrieverPort.noop(),
                new RecordingLifecyclePort(List.of(shortTerm, longTerm, semantic)),
                channels,
                new RrfMemoryFusion(),
                fusionPolicy,
                10,
                traceRecorder,
                null,
                aliasPort,
                recallRerankerPort);
    }

    private MemoryRecallChannelPort channel(String name, List<MemoryRecallCandidate> candidates) {
        return new RecordingRecallChannel(name, candidates);
    }

    private MemoryRecallCandidate candidate(String memoryId, String channel, int rank, double score, String layer) {
        return new MemoryRecallCandidate(
                memoryId,
                channel,
                rank,
                score,
                "user-1",
                "default",
                layer,
                "PROJECT_FACT",
                "",
                "generation-1",
                "ACTIVE",
                Map.of());
    }

    private MemoryRecord record(String id, String layer, String content) {
        return new MemoryRecord(
                id,
                layer,
                "PROJECT_FACT",
                content,
                Map.of("userId", "user-1", "importanceScore", 0.8D, "confidenceLevel", 0.9D),
                Instant.parse("2026-05-21T00:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fusionExplanations(MemoryTraceEvent event) {
        return (List<Map<String, Object>>) event.details().get("fusionExplanations");
    }

    private static class RecordingMemoryStore
            implements ShortTermMemoryPort, LongTermMemoryPort, SemanticMemoryPort {

        private final Map<String, MemoryRecord> records = new LinkedHashMap<>();
        private final List<String> recordReadIds = new ArrayList<>();

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return records.values().stream().limit(limit).toList();
        }

        @Override
        public void save(MemoryRecord record) {
            records.put(record.id(), record);
        }

        @Override
        public boolean deleteById(String id) {
            return records.remove(id) != null;
        }
    }

    private static final class RecordingRecallChannel implements MemoryRecallChannelPort {

        private final String name;
        private final List<MemoryRecallCandidate> candidates;
        private final List<MemoryRecallRequest> requests = new ArrayList<>();

        private RecordingRecallChannel(String name, List<MemoryRecallCandidate> candidates) {
            this.name = name;
            this.candidates = candidates;
        }

        @Override
        public String channelName() {
            return name;
        }

        @Override
        public List<MemoryRecallCandidate> recall(MemoryRecallRequest request) {
            requests.add(request);
            return candidates;
        }
    }

    private static final class StaticAliasPort implements MemoryAliasPort {

        private final MemoryAliasResolution resolution;

        private StaticAliasPort(MemoryAliasResolution resolution) {
            this.resolution = resolution;
        }

        @Override
        public Optional<MemoryAliasResolution> resolveAlias(String userId, String tenantId, String aliasText) {
            return Optional.ofNullable(resolution);
        }

        @Override
        public void upsertAlias(MemoryAliasCommand command) {
        }
    }

    private static final class RecordingReranker implements MemoryRecallRerankerPort {

        private final List<MemoryRecallCandidate> reranked;
        private final List<MemoryRecallRequest> requests = new ArrayList<>();
        private final List<List<MemoryRecallCandidate>> inputs = new ArrayList<>();

        private RecordingReranker(List<MemoryRecallCandidate> reranked) {
            this.reranked = reranked;
        }

        @Override
        public List<MemoryRecallCandidate> rerank(MemoryRecallRequest request,
                                                  List<MemoryRecallCandidate> candidates) {
            requests.add(request);
            inputs.add(List.copyOf(candidates));
            return reranked;
        }
    }

    private static final class RecordingLifecyclePort implements MemoryLifecyclePort {

        private final List<RecordingMemoryStore> stores;

        private RecordingLifecyclePort(List<RecordingMemoryStore> stores) {
            this.stores = stores;
        }

        @Override
        public int markObsoleteByProfileSlot(String userId,
                                             String tenantId,
                                             String profileSlot,
                                             String activeGenerationId,
                                             String reason) {
            return 0;
        }

        @Override
        public void recordRead(String layer, String memoryId, Instant referencedAt) {
            stores.stream()
                    .filter(store -> store.records.containsKey(memoryId))
                    .findFirst()
                    .ifPresent(store -> store.recordReadIds.add(memoryId));
        }
    }

    private static final class RecordingTraceRecorder implements MemoryTraceRecorder {

        private final List<MemoryTraceEvent> events = new ArrayList<>();

        @Override
        public void record(MemoryTraceEvent event) {
            events.add(event);
        }

        @Override
        public List<MemoryTraceEvent> listRecent(int limit) {
            return List.copyOf(events);
        }
    }

    private static final class StaticMemoryVectorPort implements MemoryVectorPort {

        private final List<String> hits;

        private StaticMemoryVectorPort(List<String> hits) {
            this.hits = hits;
        }

        @Override
        public void upsert(String memoryId, String userId, String content, String embeddingModel) {
        }

        @Override
        public List<String> search(String userId, String query, int topK) {
            return hits.stream().limit(topK).toList();
        }
    }
}
