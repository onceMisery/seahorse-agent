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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryContextAttribution;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFusionPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallFusionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRerankerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTrack;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HybridMemoryRecallPipeline implements MemoryRetrievalPipelinePort {

    private static final Logger LOG = LoggerFactory.getLogger(HybridMemoryRecallPipeline.class);
    private static final String HASH_ALGORITHM_SHA_256 = "SHA-256";
    private static final String FILTER_MEMORY_ALIAS_TEXT = "memoryAliasText";
    private static final String FILTER_MEMORY_ALIAS_NORMALIZED = "memoryAliasNormalized";
    private static final String FILTER_MEMORY_ALIAS_CANONICAL_ENTITY_ID = "memoryAliasCanonicalEntityId";
    private static final String FILTER_MEMORY_ALIAS_CANONICAL_NAME = "memoryAliasCanonicalName";
    private static final String FILTER_MEMORY_ALIAS_ENTITY_TYPE = "memoryAliasEntityType";
    private static final String FILTER_MEMORY_ALIAS_CONFIDENCE_LEVEL = "memoryAliasConfidenceLevel";

    private final ShortTermMemoryPort shortTermPort;
    private final LongTermMemoryPort longTermPort;
    private final SemanticMemoryPort semanticPort;
    private final ObjectMapper objectMapper;
    private final ProfileMemoryPort profileMemoryPort;
    private final CorrectionLedgerPort correctionLedgerPort;
    private final MemoryRouterPort memoryRouterPort;
    private final MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort;
    private final MemoryLifecyclePort memoryLifecyclePort;
    private final List<MemoryRecallChannelPort> channels;
    private final MemoryRecallFusionPort fusionPort;
    private final MemoryRecallRerankerPort recallRerankerPort;
    private final MemoryFusionPolicy fusionPolicy;
    private final int channelTopK;
    private final MemoryTraceRecorder traceRecorder;
    private final Executor recallExecutor;
    private final MemoryAliasPort memoryAliasPort;
    private final ObservationPort observationPort;
    private final MemoryRecallObservationSupport observationSupport;


    private record ChannelRecallTask(
            MemoryRecallChannelPort channel,
            long startedAt,
            CompletableFuture<List<MemoryRecallCandidate>> future
    ) {
    }

    private record AliasResolvedQuery(String query, Map<String, Object> filters) {
    }

    record RecallTraceContext(
            String originalQueryHash,
            String resolvedQueryHash,
            boolean queryChangedByAlias,
            List<String> activeTracks,
            int requestTopK,
            Map<String, Object> aliasDetails
    ) {
    }

    private record RecallOutcome(List<MemoryItem> items, Map<String, List<String>> channelAttribution) {
    }

    private record LoadOutcome(MemoryContext context, Map<String, List<String>> channelAttribution) {
    }

    public static Builder builder(ShortTermMemoryPort shortTermPort,
                                  LongTermMemoryPort longTermPort,
                                  SemanticMemoryPort semanticPort,
                                  ObjectMapper objectMapper,
                                  ProfileMemoryPort profileMemoryPort,
                                  CorrectionLedgerPort correctionLedgerPort,
                                  MemoryRouterPort memoryRouterPort,
                                  MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort,
                                  MemoryLifecyclePort memoryLifecyclePort) {
        return new Builder(shortTermPort,
                longTermPort,
                semanticPort,
                objectMapper,
                profileMemoryPort,
                correctionLedgerPort,
                memoryRouterPort,
                businessDocumentRetrieverPort,
                memoryLifecyclePort);
    }

    private HybridMemoryRecallPipeline(ShortTermMemoryPort shortTermPort,
                                      LongTermMemoryPort longTermPort,
                                      SemanticMemoryPort semanticPort,
                                      ObjectMapper objectMapper,
                                      ProfileMemoryPort profileMemoryPort,
                                      CorrectionLedgerPort correctionLedgerPort,
                                      MemoryRouterPort memoryRouterPort,
                                      MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort,
                                      MemoryLifecyclePort memoryLifecyclePort,
                                      List<MemoryRecallChannelPort> channels,
                                      MemoryRecallFusionPort fusionPort,
                                      MemoryFusionPolicy fusionPolicy,
                                      int channelTopK,
                                      MemoryTraceRecorder traceRecorder,
                                      Executor recallExecutor,
                                      MemoryAliasPort memoryAliasPort,
                                      MemoryRecallRerankerPort recallRerankerPort,
                                      ObservationPort observationPort) {
        this.shortTermPort = Objects.requireNonNull(shortTermPort, "shortTermPort must not be null");
        this.longTermPort = Objects.requireNonNull(longTermPort, "longTermPort must not be null");
        this.semanticPort = Objects.requireNonNull(semanticPort, "semanticPort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.profileMemoryPort = Objects.requireNonNull(profileMemoryPort, "profileMemoryPort must not be null");
        this.correctionLedgerPort = Objects.requireNonNull(correctionLedgerPort, "correctionLedgerPort must not be null");
        this.memoryRouterPort = Objects.requireNonNull(memoryRouterPort, "memoryRouterPort must not be null");
        this.businessDocumentRetrieverPort = Objects.requireNonNull(businessDocumentRetrieverPort,
                "businessDocumentRetrieverPort must not be null");
        this.memoryLifecyclePort = Objects.requireNonNull(memoryLifecyclePort, "memoryLifecyclePort must not be null");
        this.channels = sortedChannels(channels);
        this.fusionPort = Objects.requireNonNull(fusionPort, "fusionPort must not be null");
        this.recallRerankerPort = Objects.requireNonNullElseGet(recallRerankerPort, MemoryRecallRerankerPort::noop);
        this.fusionPolicy = Objects.requireNonNullElseGet(fusionPolicy, MemoryFusionPolicy::defaults);
        this.channelTopK = channelTopK > 0 ? channelTopK : MemoryFusionPolicy.DEFAULT_CHANNEL_TOP_K;
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
        this.recallExecutor = Objects.requireNonNullElseGet(recallExecutor, ForkJoinPool::commonPool);
        this.memoryAliasPort = Objects.requireNonNullElseGet(memoryAliasPort, MemoryAliasPort::noop);
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
        this.observationSupport = new MemoryRecallObservationSupport(
                this.traceRecorder, this.observationPort, this.fusionPolicy);
    }

    /**
     * 召回流水线 Builder：默认值与原望远镜构造器完全一致（fusion 默认策略档、
     * 通道 top-K 默认值、noop 追踪/别名/重排/观测、公共池执行器）。
     */
    public static final class Builder {

        private final ShortTermMemoryPort shortTermPort;
        private final LongTermMemoryPort longTermPort;
        private final SemanticMemoryPort semanticPort;
        private final ObjectMapper objectMapper;
        private final ProfileMemoryPort profileMemoryPort;
        private final CorrectionLedgerPort correctionLedgerPort;
        private final MemoryRouterPort memoryRouterPort;
        private final MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort;
        private final MemoryLifecyclePort memoryLifecyclePort;
        private List<MemoryRecallChannelPort> channels;
        private MemoryRecallFusionPort fusionPort;
        private MemoryFusionPolicy fusionPolicy;
        private int channelTopK;
        private MemoryTraceRecorder traceRecorder;
        private Executor recallExecutor;
        private MemoryAliasPort memoryAliasPort;
        private MemoryRecallRerankerPort recallRerankerPort;
        private ObservationPort observationPort;

        private Builder(ShortTermMemoryPort shortTermPort,
                        LongTermMemoryPort longTermPort,
                        SemanticMemoryPort semanticPort,
                        ObjectMapper objectMapper,
                        ProfileMemoryPort profileMemoryPort,
                        CorrectionLedgerPort correctionLedgerPort,
                        MemoryRouterPort memoryRouterPort,
                        MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort,
                        MemoryLifecyclePort memoryLifecyclePort) {
            this.shortTermPort = shortTermPort;
            this.longTermPort = longTermPort;
            this.semanticPort = semanticPort;
            this.objectMapper = objectMapper;
            this.profileMemoryPort = profileMemoryPort;
            this.correctionLedgerPort = correctionLedgerPort;
            this.memoryRouterPort = memoryRouterPort;
            this.businessDocumentRetrieverPort = businessDocumentRetrieverPort;
            this.memoryLifecyclePort = memoryLifecyclePort;
        }

        public Builder channels(List<MemoryRecallChannelPort> channels) {
            this.channels = channels;
            return this;
        }

        public Builder fusionPort(MemoryRecallFusionPort fusionPort) {
            this.fusionPort = fusionPort;
            return this;
        }

        public Builder fusionPolicy(MemoryFusionPolicy fusionPolicy) {
            this.fusionPolicy = fusionPolicy;
            return this;
        }

        public Builder channelTopK(int channelTopK) {
            this.channelTopK = channelTopK;
            return this;
        }

        public Builder traceRecorder(MemoryTraceRecorder traceRecorder) {
            this.traceRecorder = traceRecorder;
            return this;
        }

        public Builder recallExecutor(Executor recallExecutor) {
            this.recallExecutor = recallExecutor;
            return this;
        }

        public Builder memoryAliasPort(MemoryAliasPort memoryAliasPort) {
            this.memoryAliasPort = memoryAliasPort;
            return this;
        }

        public Builder recallRerankerPort(MemoryRecallRerankerPort recallRerankerPort) {
            this.recallRerankerPort = recallRerankerPort;
            return this;
        }

        public Builder observationPort(ObservationPort observationPort) {
            this.observationPort = observationPort;
            return this;
        }

        public HybridMemoryRecallPipeline build() {
            return new HybridMemoryRecallPipeline(
                    shortTermPort,
                    longTermPort,
                    semanticPort,
                    objectMapper,
                    profileMemoryPort,
                    correctionLedgerPort,
                    memoryRouterPort,
                    businessDocumentRetrieverPort,
                    memoryLifecyclePort,
                    channels,
                    fusionPort,
                    fusionPolicy,
                    channelTopK,
                    traceRecorder,
                    recallExecutor,
                    memoryAliasPort,
                    recallRerankerPort,
                    observationPort);
        }
    }


    @Override
    public MemoryContext load(MemoryLoadRequest request) {
        return loadInternal(request).context();
    }

    @Override
    public MemoryContextAttribution loadWithAttribution(MemoryLoadRequest request) {
        LoadOutcome outcome = loadInternal(request);
        return new MemoryContextAttribution(outcome.context(), outcome.channelAttribution());
    }

    private LoadOutcome loadInternal(MemoryLoadRequest request) {
        if (request == null || isBlank(request.userId())) {
            return new LoadOutcome(emptyContext(request), Map.of());
        }
        String userId = request.userId();
        String tenantId = request.tenantId();
        var routePlan = memoryRouterPort.route(new MemoryRouteRequest(userId, tenantId,
                request.currentQuestion()));
        boolean loadCorrection = routePlan.isActive(MemoryTrack.CORRECTION);
        boolean loadProfile = routePlan.isActive(MemoryTrack.PROFILE);
        boolean loadEpisodic = routePlan.isActive(MemoryTrack.EPISODIC);
        boolean loadBusinessDocument = routePlan.isActive(MemoryTrack.BUSINESS_DOCUMENT);

        List<MemoryItem> corrections = loadCorrection ? loadCorrections(userId, tenantId) : Collections.emptyList();
        List<MemoryItem> profile = loadProfile ? loadProfileFacts(userId, tenantId) : Collections.emptyList();
        Set<String> correctionProfileSlots = correctionProfileSlots(corrections);
        if (!correctionProfileSlots.isEmpty()) {
            profile = removeActiveProfileSlotMemories(profile, correctionProfileSlots);
        }

        List<MemoryItem> shortTerm = Collections.emptyList();
        List<MemoryItem> longTerm = Collections.emptyList();
        List<MemoryItem> semantic = Collections.emptyList();
        Map<String, List<String>> channelAttribution = Map.of();
        if (loadEpisodic || (!channels.isEmpty() && !isBlank(request.currentQuestion()))) {
            RecallOutcome recalled = recallUserMemoriesWithAttribution(
                    userId, tenantId, request.currentQuestion(), routePlan.activeTracks());
            shortTerm = filterByLayer(recalled.items(), MemoryLayer.SHORT_TERM);
            longTerm = filterByLayer(recalled.items(), MemoryLayer.LONG_TERM);
            semantic = filterByLayer(recalled.items(), MemoryLayer.SEMANTIC);
            channelAttribution = recalled.channelAttribution();
        }
        List<MemoryItem>             businessDocuments = loadBusinessDocument
                    ? loadBusinessDocuments(userId, tenantId, request.currentQuestion(), fusionPolicy.finalTopK(),
                    request.knowledgeBaseIds())
                    : Collections.emptyList();

        Set<String> activeProfileSlots = activeProfileSlots(profile);
        Set<String> suppressedProfileSlots = new LinkedHashSet<>();
        suppressedProfileSlots.addAll(activeProfileSlots);
        suppressedProfileSlots.addAll(correctionProfileSlots);
        if (!suppressedProfileSlots.isEmpty()) {
            shortTerm = removeActiveProfileSlotMemories(shortTerm, suppressedProfileSlots);
            longTerm = removeActiveProfileSlotMemories(longTerm, suppressedProfileSlots);
            semantic = removeActiveProfileSlotMemories(semantic, suppressedProfileSlots);
        }

        shortTerm = deduplicateById(shortTerm);
        longTerm = deduplicateProfileSlots(deduplicateById(longTerm));
        semantic = deduplicateProfileSlots(deduplicateById(semantic));
        businessDocuments = deduplicateById(businessDocuments);

        Instant referencedAt = Instant.now();
        recordProfileReadFeedback(profile, referencedAt, tenantId);
        recordLayerReadFeedback(shortTerm, longTerm, semantic, referencedAt);

        MemoryContext context = MemoryContext.builder()
                .conversationId(request.conversationId())
                .userId(userId)
                .currentQuestion(request.currentQuestion())
                .workingMemory(Collections.emptyList())
                .correctionMemories(corrections)
                .profileMemories(profile)
                .shortTermMemories(shortTerm)
                .businessDocumentMemories(businessDocuments)
                .longTermMemories(longTerm)
                .semanticMemories(semantic)
                .promptMessages(Collections.emptyList())
                .build();
        return new LoadOutcome(context, channelAttribution);
    }

    private List<MemoryRecallChannelPort> sortedChannels(List<MemoryRecallChannelPort> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(MemoryRecallChannelPort::order)
                        .thenComparing(MemoryRecallChannelPort::channelName))
                .toList();
    }

    private List<MemoryItem> recallUserMemories(String userId, String tenantId, String query, Set<MemoryTrack> activeTracks) {
        return recallUserMemoriesWithAttribution(userId, tenantId, query, activeTracks).items();
    }

    private RecallOutcome recallUserMemoriesWithAttribution(String userId,
                                                            String tenantId,
                                                            String query,
                                                            Set<MemoryTrack> activeTracks) {
        if (isBlank(query) || channels.isEmpty()) {
            return new RecallOutcome(List.of(), Map.of());
        }
        AliasResolvedQuery resolvedQuery = resolveRecallAlias(userId, tenantId, query);
        MemoryRecallRequest recallRequest = new MemoryRecallRequest(
                userId,
                tenantId,
                resolvedQuery.query(),
                activeTracks,
                channelTopK,
                resolvedQuery.filters());
        RecallTraceContext traceContext = traceContext(query, recallRequest);
        List<ChannelRecallTask> tasks = channels.stream()
                .map(channel -> recallTask(channel, recallRequest))
                .toList();
        List<List<MemoryRecallCandidate>> channelResults = collectChannelResults(tasks, userId, tenantId, traceContext);
        List<MemoryRecallCandidate> fused = fusionPort.fuse(channelResults, fusionPolicy, Instant.now());
        observationSupport.recordRecallFusion(userId, tenantId, channelResults, fused, fusionPolicy.finalTopK(), traceContext);
        List<MemoryRecallCandidate> reranked = rerankFusedCandidates(recallRequest, fused);
        observationSupport.recordRecallRerank(userId, tenantId, fused, reranked, traceContext);
        List<MemoryItem> items = reranked.stream()
                .map(this::toMemoryItem)
                .flatMap(Optional::stream)
                .toList();
        return new RecallOutcome(items, buildChannelAttribution(channelResults));
    }

    private Map<String, List<String>> buildChannelAttribution(List<List<MemoryRecallCandidate>> channelResults) {
        if (channelResults == null || channelResults.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> attribution = new LinkedHashMap<>();
        int columns = Math.min(channels.size(), channelResults.size());
        for (int index = 0; index < columns; index++) {
            String channelName = channels.get(index).channelName();
            if (channelName == null || channelName.isBlank()) {
                continue;
            }
            List<String> candidateIds = channelResults.get(index).stream()
                    .map(MemoryRecallCandidate::memoryId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            attribution.put(channelName, candidateIds);
        }
        return attribution;
    }

    private List<MemoryRecallCandidate> rerankFusedCandidates(MemoryRecallRequest request,
                                                              List<MemoryRecallCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<MemoryRecallCandidate> rerankInputs = candidates.stream()
                .map(this::enrichCandidateForRerank)
                .toList();
        try {
            List<MemoryRecallCandidate> reranked = recallRerankerPort.rerank(request, rerankInputs);
            return reranked == null ? rerankInputs : reranked.stream()
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("memory recall reranker failed, falling back to fused order: userId={}, query={}",
                    request.userId(), request.query(), ex);
            return rerankInputs;
        }
    }

    private MemoryRecallCandidate enrichCandidateForRerank(MemoryRecallCandidate candidate) {
        if (candidate == null || !candidate.content().isBlank()) {
            return candidate;
        }
        Optional<MemoryRecord> record = findMemoryById(candidate.memoryId(), candidate.layer());
        if (record.isEmpty() || !generationMatches(candidate, record.get())) {
            return candidate;
        }
        MemoryRecord memoryRecord = record.get();
        Map<String, Object> metadata = new LinkedHashMap<>(memoryRecord.metadata());
        metadata.putAll(candidate.metadata());
        return new MemoryRecallCandidate(
                candidate.memoryId(),
                candidate.channel(),
                candidate.rank(),
                candidate.rawScore(),
                candidate.userId(),
                candidate.tenantId(),
                isBlank(candidate.layer()) ? memoryRecord.layer() : candidate.layer(),
                isBlank(candidate.type()) ? memoryRecord.type() : candidate.type(),
                memoryRecord.content(),
                candidate.generationId(),
                candidate.status(),
                metadata);
    }

    private AliasResolvedQuery resolveRecallAlias(String userId, String tenantId, String query) {
        try {
            Optional<MemoryAliasResolution> resolved = memoryAliasPort.resolveAlias(userId, tenantId, query);
            if (resolved.isEmpty()) {
                return new AliasResolvedQuery(query, Map.of());
            }
            MemoryAliasResolution alias = resolved.get();
            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put(FILTER_MEMORY_ALIAS_TEXT, alias.aliasText());
            filters.put(FILTER_MEMORY_ALIAS_NORMALIZED, alias.normalizedAlias());
            filters.put(FILTER_MEMORY_ALIAS_CANONICAL_ENTITY_ID, alias.canonicalEntityId());
            filters.put(FILTER_MEMORY_ALIAS_CANONICAL_NAME, alias.canonicalName());
            filters.put(FILTER_MEMORY_ALIAS_ENTITY_TYPE, alias.entityType());
            filters.put(FILTER_MEMORY_ALIAS_CONFIDENCE_LEVEL, alias.confidenceLevel());
            return new AliasResolvedQuery(appendCanonicalAlias(query, alias), filters);
        } catch (RuntimeException ex) {
            LOG.debug("memory alias resolution failed: userId={}, query={}", userId, query, ex);
            return new AliasResolvedQuery(query, Map.of());
        }
    }

    private String appendCanonicalAlias(String query, MemoryAliasResolution alias) {
        List<String> terms = new ArrayList<>();
        terms.add(query);
        addDistinctAliasTerm(terms, alias.canonicalName());
        addDistinctAliasTerm(terms, alias.canonicalEntityId());
        return String.join(" ", terms);
    }

    private void addDistinctAliasTerm(List<String> terms, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String term : terms) {
            if (term.equalsIgnoreCase(value)) {
                return;
            }
        }
        terms.add(value);
    }

    private ChannelRecallTask recallTask(MemoryRecallChannelPort channel, MemoryRecallRequest recallRequest) {
        long startedAt = System.nanoTime();
        CompletableFuture<List<MemoryRecallCandidate>> future = CompletableFuture.supplyAsync(
                () -> channel.recall(recallRequest),
                recallExecutor);
        return new ChannelRecallTask(channel, startedAt, future);
    }

    private List<List<MemoryRecallCandidate>> collectChannelResults(List<ChannelRecallTask> tasks,
                                                                    String userId,
                                                                    String tenantId,
                                                                    RecallTraceContext traceContext) {
        List<List<MemoryRecallCandidate>> channelResults = new ArrayList<>();
        for (ChannelRecallTask task : tasks) {
            try {
                List<MemoryRecallCandidate> result = task.future()
                        .get(fusionPolicy.channelTimeoutMillis(), TimeUnit.MILLISECONDS);
                List<MemoryRecallCandidate> safeResult = result == null ? List.of() : result;
                channelResults.add(safeResult);
                observationSupport.recordRecallChannel(task.channel(), userId, tenantId, safeResult,
                        elapsedMillis(task.startedAt()), MemoryTraceEvent.STATUS_SUCCESS, "", traceContext);
            } catch (TimeoutException ex) {
                task.future().cancel(true);
                channelResults.add(List.of());
                LOG.warn("memory recall channel timed out: channel={}, userId={}, timeoutMs={}",
                        task.channel().channelName(), userId, fusionPolicy.channelTimeoutMillis());
                observationSupport.recordRecallChannel(task.channel(), userId, tenantId, List.of(),
                        elapsedMillis(task.startedAt()), MemoryTraceEvent.STATUS_FAILED, "timeout", traceContext);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                task.future().cancel(true);
                channelResults.add(List.of());
                observationSupport.recordRecallChannel(task.channel(), userId, tenantId, List.of(),
                        elapsedMillis(task.startedAt()), MemoryTraceEvent.STATUS_FAILED, "interrupted", traceContext);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                LOG.warn("memory recall channel failed: channel={}, userId={}", task.channel().channelName(), userId,
                        cause);
                channelResults.add(List.of());
                observationSupport.recordRecallChannel(task.channel(), userId, tenantId, List.of(),
                        elapsedMillis(task.startedAt()), MemoryTraceEvent.STATUS_FAILED,
                        Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getName()), traceContext);
            }
        }
        return channelResults;
    }







    private RecallTraceContext traceContext(String originalQuery, MemoryRecallRequest request) {
        String safeOriginalQuery = Objects.requireNonNullElse(originalQuery, "");
        String safeResolvedQuery = request == null ? "" : request.query();
        Map<String, Object> aliasDetails = aliasTraceDetails(request == null ? Map.of() : request.filters());
        return new RecallTraceContext(
                sha256(safeOriginalQuery),
                sha256(safeResolvedQuery),
                !safeOriginalQuery.equals(safeResolvedQuery),
                activeTrackNames(request == null ? Set.of() : request.activeTracks()),
                request == null ? channelTopK : request.topK(),
                aliasDetails);
    }


    private List<String> activeTrackNames(Set<MemoryTrack> activeTracks) {
        if (activeTracks == null || activeTracks.isEmpty()) {
            return List.of();
        }
        return activeTracks.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MemoryTrack::name))
                .map(MemoryTrack::name)
                .toList();
    }

    private Map<String, Object> aliasTraceDetails(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> details = new LinkedHashMap<>();
        putIfPresent(details, MemoryRecallObservationSupport.TRACE_KEY_ALIAS_CANONICAL_ENTITY_ID,
                filters.get(FILTER_MEMORY_ALIAS_CANONICAL_ENTITY_ID));
        putIfPresent(details, MemoryRecallObservationSupport.TRACE_KEY_ALIAS_ENTITY_TYPE, filters.get(FILTER_MEMORY_ALIAS_ENTITY_TYPE));
        putIfPresent(details, MemoryRecallObservationSupport.TRACE_KEY_ALIAS_CONFIDENCE_LEVEL, filters.get(FILTER_MEMORY_ALIAS_CONFIDENCE_LEVEL));
        return details;
    }



    static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null || Objects.toString(value, "").isBlank()) {
            return;
        }
        target.put(key, value);
    }

    static List<String> candidateIds(List<MemoryRecallCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (MemoryRecallCandidate candidate : candidates) {
            if (candidate != null && !candidate.memoryId().isBlank()) {
                ids.add(candidate.memoryId());
            }
        }
        return new ArrayList<>(ids);
    }

    static List<String> candidateIdsFromChannelResults(List<List<MemoryRecallCandidate>> channelResults) {
        if (channelResults == null || channelResults.isEmpty()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (List<MemoryRecallCandidate> channelResult : channelResults) {
            ids.addAll(candidateIds(channelResult));
        }
        return new ArrayList<>(ids);
    }

    static List<MemoryRecallCandidate> safeCandidates(List<MemoryRecallCandidate> candidates) {
        return candidates == null ? List.of() : candidates;
    }

    static List<List<MemoryRecallCandidate>> safeChannelResults(List<List<MemoryRecallCandidate>> channelResults) {
        return channelResults == null ? List.of() : channelResults;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM_SHA_256);
            return HexFormat.of().formatHex(digest.digest(Objects.requireNonNullElse(value, "")
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private Optional<MemoryItem> toMemoryItem(MemoryRecallCandidate candidate) {
        Optional<MemoryRecord> record = findMemoryById(candidate.memoryId(), candidate.layer());
        if (record.isEmpty() && !candidate.content().isBlank()) {
            MemoryLayer layer = parseLayer(candidate.layer()).orElse(MemoryLayer.SEMANTIC);
            return Optional.of(MemoryItem.builder()
                    .id(candidate.memoryId())
                    .userId(candidate.userId())
                    .layer(layer)
                    .type(candidate.type())
                    .content(candidate.content())
                    .metadataJson(serializeMetadata(candidate.metadata()))
                    .importanceScore(number(candidate.metadata().get("importanceScore")))
                    .confidenceLevel(number(candidate.metadata().get("confidenceLevel")))
                    .relevanceScore(candidate.rawScore())
                    .build());
        }
        return record
                .filter(memoryRecord -> generationMatches(candidate, memoryRecord))
                .map(memoryRecord -> toMemoryItem(memoryRecord, candidate.rawScore()));
    }

    private boolean generationMatches(MemoryRecallCandidate candidate, MemoryRecord record) {
        if (candidate.generationId().isBlank()) {
            return true;
        }
        String activeGenerationId = stringField(record.metadata(), "generationId");
        return activeGenerationId.isBlank() || candidate.generationId().equals(activeGenerationId);
    }

    private Optional<MemoryRecord> findMemoryById(String memoryId, String candidateLayer) {
        Optional<MemoryLayer> layer = parseLayer(candidateLayer);
        if (layer.isPresent()) {
            return switch (layer.get()) {
                case SHORT_TERM -> safeFindById(shortTermPort, memoryId);
                case LONG_TERM -> safeFindById(longTermPort, memoryId);
                case SEMANTIC -> safeFindById(semanticPort, memoryId);
                case WORKING -> Optional.empty();
            };
        }
        Optional<MemoryRecord> shortTerm = safeFindById(shortTermPort, memoryId);
        if (shortTerm.isPresent()) {
            return shortTerm;
        }
        Optional<MemoryRecord> longTerm = safeFindById(longTermPort, memoryId);
        if (longTerm.isPresent()) {
            return longTerm;
        }
        return safeFindById(semanticPort, memoryId);
    }

    private Optional<MemoryRecord> safeFindById(MemoryStorePort port, String memoryId) {
        if (isBlank(memoryId)) {
            return Optional.empty();
        }
        try {
            return port.findById(memoryId);
        } catch (RuntimeException ex) {
            LOG.debug("memory find by id failed: memoryId={}", memoryId, ex);
            return Optional.empty();
        }
    }

    private Optional<MemoryLayer> parseLayer(String layer) {
        if (isBlank(layer)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MemoryLayer.valueOf(layer.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private MemoryItem toMemoryItem(MemoryRecord record, double relevanceScore) {
        MemoryLayer layer = parseLayer(record.layer()).orElse(MemoryLayer.SEMANTIC);
        return MemoryItem.builder()
                .id(record.id())
                .userId(stringField(record.metadata(), "userId"))
                .conversationId(stringField(record.metadata(), "conversationId"))
                .layer(layer)
                .type(record.type())
                .content(record.content())
                .metadataJson(serializeMetadata(record.metadata()))
                .importanceScore(numberField(record.metadata(), "importanceScore", 0D))
                .confidenceLevel(numberField(record.metadata(), "confidenceLevel", 0D))
                .relevanceScore(relevanceScore)
                .createTime(record.updatedAt() != null
                        ? record.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()
                        : null)
                .build();
    }

    private List<MemoryItem> loadCorrections(String userId, String tenantId) {
        try {
            return correctionLedgerPort.listActive(userId, tenantId, fusionPolicy.finalTopK()).stream()
                    .map(this::toCorrectionItem)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("load correction ledger failed: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private List<MemoryItem> loadProfileFacts(String userId, String tenantId) {
        try {
            return profileMemoryPort.listActive(userId, tenantId, fusionPolicy.finalTopK()).stream()
                    .map(this::toProfileItem)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("load profile facts failed: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private MemoryItem toCorrectionItem(CorrectionRule rule) {
        return MemoryItem.builder()
                .id(rule.id())
                .userId(rule.userId())
                .layer(MemoryLayer.SEMANTIC)
                .type("CORRECTION")
                .content(rule.ruleText())
                .metadataJson(serializeMetadata(Map.of(
                        "userId", rule.userId(),
                        "tenantId", rule.tenantId(),
                        "targetKind", rule.targetKind(),
                        "targetKey", rule.targetKey(),
                        "incorrectValue", rule.incorrectValue(),
                        "correctValue", rule.correctValue(),
                        "priority", rule.priority(),
                        "generationId", rule.generationId())))
                .importanceScore(1D)
                .confidenceLevel(1D)
                .createTime(rule.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    private MemoryItem toProfileItem(ProfileFact fact) {
        return MemoryItem.builder()
                .id(fact.id())
                .userId(fact.userId())
                .layer(MemoryLayer.SEMANTIC)
                .type("PROFILE")
                .content(fact.valueText())
                .metadataJson(serializeMetadata(Map.of(
                        "userId", fact.userId(),
                        "tenantId", fact.tenantId(),
                        "profileSlot", fact.slotKey(),
                        "sourceType", fact.sourceType(),
                        "generationId", fact.generationId(),
                        "status", fact.status())))
                .importanceScore(1D)
                .confidenceLevel(fact.confidenceLevel())
                .createTime(fact.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    private List<MemoryItem> loadBusinessDocuments(String userId,
                                                   String tenantId,
                                                   String query,
                                                   int limit,
                                                   List<String> knowledgeBaseIds) {
        if (isBlank(query)) {
            return Collections.emptyList();
        }
        try {
            return businessDocumentRetrieverPort.retrieve(tenantId, query, limit, knowledgeBaseIds);
        } catch (RuntimeException ex) {
            LOG.warn("load business document memories failed: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private List<MemoryItem> filterByLayer(List<MemoryItem> items, MemoryLayer layer) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .filter(item -> item != null && item.getLayer() == layer)
                .toList();
    }

    private Set<String> correctionProfileSlots(List<MemoryItem> corrections) {
        if (corrections == null || corrections.isEmpty()) {
            return Set.of();
        }
        Set<String> slots = new LinkedHashSet<>();
        for (MemoryItem correction : corrections) {
            String slot = correctionTargetSlot(correction);
            if (!slot.isBlank()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private String correctionTargetSlot(MemoryItem correction) {
        String metadata = correction == null ? "" : Objects.requireNonNullElse(correction.getMetadataJson(), "");
        String targetKind = metadataValue(metadata, "targetKind");
        String targetKey = metadataValue(metadata, "targetKey");
        if ("PROFILE_SLOT".equalsIgnoreCase(targetKind) && !targetKey.isBlank()) {
            return targetKey;
        }
        return "";
    }

    private Set<String> activeProfileSlots(List<MemoryItem> profile) {
        if (profile == null || profile.isEmpty()) {
            return Set.of();
        }
        Set<String> slots = new LinkedHashSet<>();
        for (MemoryItem item : profile) {
            String slot = semanticSlot(item);
            if (!slot.isBlank()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private List<MemoryItem> removeActiveProfileSlotMemories(List<MemoryItem> items, Set<String> activeSlots) {
        if (items == null || items.isEmpty() || activeSlots == null || activeSlots.isEmpty()) {
            return items == null ? Collections.emptyList() : items;
        }
        return items.stream()
                .filter(item -> !activeSlots.contains(semanticSlot(item)))
                .toList();
    }

    private String semanticSlot(MemoryItem item) {
        if (item == null) {
            return "";
        }
        String metadata = Objects.requireNonNullElse(item.getMetadataJson(), "");
        String profileSlot = metadataValue(metadata, "profileSlot");
        if (!profileSlot.isBlank()) {
            return profileSlot;
        }
        String semanticKey = metadataValue(metadata, "semanticKey");
        if ("profile:occupation".equals(semanticKey)) {
            return "identity.occupation";
        }
        return semanticKey.startsWith("identity.") || semanticKey.startsWith("skills.")
                || semanticKey.startsWith("preferences.")
                ? semanticKey
                : "";
    }

    private List<MemoryItem> deduplicateById(List<MemoryItem> items) {
        if (items == null || items.size() <= 1) {
            return items == null ? Collections.emptyList() : items;
        }
        Set<String> seen = new LinkedHashSet<>();
        List<MemoryItem> result = new ArrayList<>();
        for (MemoryItem item : items) {
            if (item != null && !isBlank(item.getId()) && seen.add(item.getId())) {
                result.add(item);
            }
        }
        return result;
    }

    private List<MemoryItem> deduplicateProfileSlots(List<MemoryItem> items) {
        if (items == null || items.size() <= 1) {
            return items == null ? Collections.emptyList() : items;
        }
        Map<String, MemoryItem> slotWinners = new LinkedHashMap<>();
        List<String> itemSlots = new ArrayList<>();
        for (MemoryItem item : items) {
            String slot = semanticSlot(item);
            itemSlots.add(slot);
            if (slot.isBlank()) {
                continue;
            }
            MemoryItem current = slotWinners.get(slot);
            if (current == null || prefer(item, current) > 0) {
                slotWinners.put(slot, item);
            }
        }
        Set<String> emittedSlots = new LinkedHashSet<>();
        List<MemoryItem> result = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            MemoryItem item = items.get(index);
            String slot = itemSlots.get(index);
            if (slot.isBlank()) {
                result.add(item);
            } else if (emittedSlots.add(slot)) {
                result.add(slotWinners.get(slot));
            }
        }
        return result;
    }

    private int prefer(MemoryItem candidate, MemoryItem current) {
        int byRelevance = Double.compare(number(candidate.getRelevanceScore()), number(current.getRelevanceScore()));
        if (byRelevance != 0) {
            return byRelevance;
        }
        int byTime = Comparator.nullsFirst(java.time.LocalDateTime::compareTo)
                .compare(candidate.getCreateTime(), current.getCreateTime());
        if (byTime != 0) {
            return byTime;
        }
        return Double.compare(score(candidate), score(current));
    }

    private void recordProfileReadFeedback(List<MemoryItem> profile, Instant referencedAt, String tenantId) {
        if (profile == null || profile.isEmpty()) {
            return;
        }
        for (MemoryItem item : profile) {
            String slot = semanticSlot(item);
            if (slot.isBlank()) {
                continue;
            }
            try {
                profileMemoryPort.recordRead(item.getUserId(), tenantId, slot, referencedAt);
            } catch (RuntimeException ex) {
                LOG.debug("record profile read feedback failed: userId={}, slot={}", item.getUserId(), slot, ex);
            }
        }
    }

    private void recordLayerReadFeedback(List<MemoryItem> shortTerm,
                                         List<MemoryItem> longTerm,
                                         List<MemoryItem> semantic,
                                         Instant referencedAt) {
        recordLayerReadFeedback(MemoryLayer.SHORT_TERM, shortTerm, referencedAt);
        recordLayerReadFeedback(MemoryLayer.LONG_TERM, longTerm, referencedAt);
        recordLayerReadFeedback(MemoryLayer.SEMANTIC, semantic, referencedAt);
    }

    private void recordLayerReadFeedback(MemoryLayer layer, List<MemoryItem> items, Instant referencedAt) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (MemoryItem item : items) {
            if (item == null || isBlank(item.getId())) {
                continue;
            }
            try {
                memoryLifecyclePort.recordRead(layer.name().toLowerCase(Locale.ROOT), item.getId(), referencedAt);
            } catch (RuntimeException ex) {
                LOG.debug("record memory read feedback failed: layer={}, memoryId={}", layer, item.getId(), ex);
            }
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            LOG.debug("serialize memory metadata failed", ex);
            return "{}";
        }
    }

    private String metadataValue(String metadata, String key) {
        if (metadata == null || metadata.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        String compactPrefix = "\"" + key + "\":\"";
        int compactStart = metadata.indexOf(compactPrefix);
        if (compactStart >= 0) {
            int valueStart = compactStart + compactPrefix.length();
            int valueEnd = metadata.indexOf('"', valueStart);
            return valueEnd > valueStart ? metadata.substring(valueStart, valueEnd) : "";
        }
        String spacedPrefix = "\"" + key + "\": \"";
        int spacedStart = metadata.indexOf(spacedPrefix);
        if (spacedStart >= 0) {
            int valueStart = spacedStart + spacedPrefix.length();
            int valueEnd = metadata.indexOf('"', valueStart);
            return valueEnd > valueStart ? metadata.substring(valueStart, valueEnd) : "";
        }
        return "";
    }

    private String stringField(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }

    private double numberField(Map<String, Object> metadata, String key, double fallback) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private double score(MemoryItem item) {
        return number(item.getImportanceScore()) + number(item.getConfidenceLevel());
    }

    private MemoryContext emptyContext(MemoryLoadRequest request) {
        return MemoryContext.builder()
                .conversationId(request != null ? request.conversationId() : "")
                .userId(request != null ? request.userId() : "")
                .currentQuestion(request != null ? request.currentQuestion() : "")
                .workingMemory(Collections.emptyList())
                .correctionMemories(Collections.emptyList())
                .profileMemories(Collections.emptyList())
                .shortTermMemories(Collections.emptyList())
                .businessDocumentMemories(Collections.emptyList())
                .longTermMemories(Collections.emptyList())
                .semanticMemories(Collections.emptyList())
                .promptMessages(Collections.emptyList())
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
