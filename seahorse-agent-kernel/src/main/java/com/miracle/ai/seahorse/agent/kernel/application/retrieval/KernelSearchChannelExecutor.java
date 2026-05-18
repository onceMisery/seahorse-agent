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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 执行已激活的检索通道，并处理通道级 timeout、trace 和降级。
 * <p>
 * 该类不参与上下文构建和结果后处理，避免检索编排器继续吸收并发执行细节。
 */
final class KernelSearchChannelExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(KernelSearchChannelExecutor.class);
    private static final String LOG_MSG_CHANNEL_FAILED = "检索通道 {} 执行失败，按空结果降级";
    private static final String LOG_MSG_CHANNEL_TIMEOUT = "检索通道 {} 执行超时，按空结果降级";

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
        // 单通道超时只降级当前通道，避免慢后端拖住整个多通道检索流程。
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
            traceRecorder.finishNode(nodeScope);
            return safeResult;
        }
        SearchChannelResult fallback = emptyResult(channel, elapsedMs);
        boolean timedOut = cause instanceof TimeoutException;
        observationSupport.recordChannelCompleted(channel, context, fallback, cause, elapsedMs, timeoutMs, timedOut);
        traceRecorder.finishNode(nodeScope, cause);
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
        // Trace 只记录编排节点，不改变通道失败时返回空结果的降级语义。
        return new TraceNodeStartCommand(
                "search-channel:" + Objects.requireNonNullElse(channel.name(), "unknown"),
                "RETRIEVAL_CHANNEL",
                channel.getClass().getName(),
                "search",
                null,
                1);
    }
}
