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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchChannelFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes enabled retrieval channels with per-channel timeout, observation, trace, and fallback handling.
 */
final class KernelSearchChannelExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(KernelSearchChannelExecutor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String LOG_MSG_CHANNEL_FAILED = "Search channel {} failed, fallback to empty result";
    private static final String LOG_MSG_CHANNEL_TIMEOUT = "Search channel {} timed out, fallback to empty result";
    private static final int TRACE_QUERY_MAX_LENGTH = 500;
    private static final int TRACE_HIT_PREVIEW_MAX_LENGTH = 300;
    private static final int TRACE_HIT_LIMIT = 3;

    private final ExtensionRegistry extensionRegistry;
    private final Executor retrievalExecutor;
    private final FeatureActivationContext activationContext;
    private final KernelRagTraceRecorder traceRecorder;
    private final KernelRetrievalObservationSupport observationSupport;
    private final Duration defaultChannelTimeout;

    KernelSearchChannelExecutor(ExtensionRegistry extensionRegistry,
                                Executor retrievalExecutor,
                                FeatureActivationContext activationContext,
                                KernelRagTraceRecorder traceRecorder,
                                KernelRetrievalObservationSupport observationSupport,
                                Duration defaultChannelTimeout) {
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "extensionRegistry must not be null");
        this.retrievalExecutor = Objects.requireNonNull(retrievalExecutor, "retrievalExecutor must not be null");
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        this.observationSupport = Objects.requireNonNull(observationSupport, "observationSupport must not be null");
        this.defaultChannelTimeout = positiveDuration(defaultChannelTimeout, Duration.ofSeconds(5));
    }

    List<SearchChannelResult> execute(SearchContext context) {
        List<SearchChannelFeature> enabledChannels = extensionRegistry
                .getActivatedExtensions(SearchChannelFeature.class, activationContext)
                .stream()
                .filter(channel -> channel.enabled(context))
                .toList();

        if (enabledChannels.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<SearchChannelResult>> futures = enabledChannels.stream()
                .map(channel -> executeSingleChannelWithTimeout(channel, context))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private CompletableFuture<SearchChannelResult> executeSingleChannelWithTimeout(SearchChannelFeature channel,
                                                                                   SearchContext context) {
        TraceNodeScope nodeScope = traceRecorder.startNode(traceRunScope(context), channelTraceCommand(channel));
        long startedAt = System.currentTimeMillis();
        long timeoutMs = channelTimeout(context, channel).toMillis();
        return CompletableFuture.supplyAsync(() -> channel.search(context), retrievalExecutor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .handle((result, throwable) -> completeSingleChannel(
                        channel, context, nodeScope, startedAt, timeoutMs, result, throwable));
    }

    private SearchChannelResult completeSingleChannel(SearchChannelFeature channel,
                                                      SearchContext context,
                                                      TraceNodeScope nodeScope,
                                                      long startedAt,
                                                      long timeoutMs,
                                                      SearchChannelResult result,
                                                      Throwable throwable) {
        long elapsedMs = System.currentTimeMillis() - startedAt;
        Throwable cause = unwrap(throwable);
        if (cause == null) {
            SearchChannelResult safeResult = result == null ? emptyResult(channel, elapsedMs) : result;
            observationSupport.recordChannelCompleted(channel, context, safeResult, null, elapsedMs, timeoutMs, false);
            traceRecorder.finishNode(nodeScope, null,
                    channelTraceExtraData(channel, context, safeResult, elapsedMs, timeoutMs, null, false));
            return safeResult;
        }
        SearchChannelResult fallback = emptyResult(channel, elapsedMs);
        boolean timedOut = cause instanceof TimeoutException;
        observationSupport.recordChannelCompleted(channel, context, fallback, cause, elapsedMs, timeoutMs, timedOut);
        traceRecorder.finishNode(nodeScope, cause,
                channelTraceExtraData(channel, context, fallback, elapsedMs, timeoutMs, cause, timedOut));
        if (timedOut) {
            LOG.warn(LOG_MSG_CHANNEL_TIMEOUT, channel.name(), cause);
        } else {
            LOG.error(LOG_MSG_CHANNEL_FAILED, channel.name(), cause);
        }
        return fallback;
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private Duration channelTimeout(SearchContext context, SearchChannelFeature channel) {
        RetrievalOptions options = context == null ? null : context.effectiveOptions();
        SearchChannelType type = channel == null ? null : channel.channelType();
        Duration configured = switch (type == null ? SearchChannelType.HYBRID : type) {
            case VECTOR_GLOBAL, INTENT_DIRECTED -> options == null ? null : options.vectorTimeout();
            case KEYWORD_BM25, KEYWORD_ES -> options == null ? null : options.keywordTimeout();
            case HYBRID -> null;
        };
        return positiveDuration(configured, defaultChannelTimeout);
    }

    private Duration positiveDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    private SearchChannelResult emptyResult(SearchChannelFeature channel, long latencyMs) {
        return SearchChannelResult.builder()
                .channelType(channel.channelType())
                .channelName(channel.name())
                .chunks(List.<RetrievedChunk>of())
                .latencyMs(latencyMs)
                .build();
    }

    private TraceRunScope traceRunScope(SearchContext context) {
        TraceRunScope traceRunScope = context == null ? null : context.getTraceRunScope();
        return traceRunScope == null ? TraceRunScope.disabled() : traceRunScope;
    }

    private TraceNodeStartCommand channelTraceCommand(SearchChannelFeature channel) {
        return new TraceNodeStartCommand(
                "search-channel:" + Objects.requireNonNullElse(channel.name(), "unknown"),
                "RETRIEVAL_CHANNEL",
                channel.getClass().getName(),
                "search",
                null,
                1);
    }

    private String channelTraceExtraData(SearchChannelFeature channel,
                                         SearchContext context,
                                         SearchChannelResult result,
                                         long elapsedMs,
                                         long timeoutMs,
                                         Throwable error,
                                         boolean timedOut) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", truncate(context == null ? null : context.getMainQuestion(), TRACE_QUERY_MAX_LENGTH));
        payload.put("query", truncate(context == null ? null : context.getMainQuestion(), TRACE_QUERY_MAX_LENGTH));
        payload.put("originalQuestion", truncate(context == null ? null : context.getOriginalQuestion(),
                TRACE_QUERY_MAX_LENGTH));
        payload.put("rewrittenQuestion", truncate(context == null ? null : context.getRewrittenQuestion(),
                TRACE_QUERY_MAX_LENGTH));
        payload.put("channelName", channel == null ? null : channel.name());
        payload.put("channelType", channel == null || channel.channelType() == null ? null : channel.channelType().name());
        payload.put("topK", context == null ? 0 : context.getTopK());
        payload.put("timeoutMs", timeoutMs);
        payload.put("latencyMs", elapsedMs);
        payload.put("timedOut", timedOut);
        payload.put("hitCount", result == null || result.getChunks() == null ? 0 : result.getChunks().size());
        payload.put("output", result == null || result.getChunks() == null
                ? "0 hits"
                : result.getChunks().size() + " hits");
        payload.put("hits", traceHits(result));
        if (error != null) {
            payload.put("errorType", error.getClass().getSimpleName());
            payload.put("errorMessage", truncate(error.getMessage(), TRACE_HIT_PREVIEW_MAX_LENGTH));
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            LOG.debug("RAG Trace channel extra_data serialization failed, channel={}",
                    channel == null ? null : channel.name(), ex);
            return null;
        }
    }

    private List<Map<String, Object>> traceHits(SearchChannelResult result) {
        if (result == null || result.getChunks() == null || result.getChunks().isEmpty()) {
            return List.of();
        }
        return result.getChunks().stream()
                .limit(TRACE_HIT_LIMIT)
                .map(this::traceHit)
                .toList();
    }

    private Map<String, Object> traceHit(RetrievedChunk chunk) {
        Map<String, Object> hit = new LinkedHashMap<>();
        if (chunk == null) {
            return hit;
        }
        hit.put("id", chunk.getId());
        hit.put("docId", chunk.getDocId());
        hit.put("kbId", chunk.getKbId());
        hit.put("collectionName", chunk.getCollectionName());
        hit.put("chunkIndex", chunk.getChunkIndex());
        hit.put("score", chunk.getScore());
        hit.put("textPreview", truncate(chunk.getText(), TRACE_HIT_PREVIEW_MAX_LENGTH));
        return hit;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(maxLength - 3, 0)) + "...";
    }
}
