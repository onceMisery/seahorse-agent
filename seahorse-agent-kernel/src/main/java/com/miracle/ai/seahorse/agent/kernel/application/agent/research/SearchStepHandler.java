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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.SourceTrustLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ExtractionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchHit;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchResult;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * SEARCH 步骤：对 PLAN 阶段生成的每个查询词执行 Web 搜索。
 */
public class SearchStepHandler implements ResearchStepHandler {

    private static final int MAX_RESULTS_PER_QUERY = 5;

    private final WebSearchPort webSearch;
    private final SourceTrustEvaluator sourceTrustEvaluator;

    public SearchStepHandler(WebSearchPort webSearch) {
        this(webSearch, new SourceTrustEvaluator());
    }

    public SearchStepHandler(WebSearchPort webSearch, SourceTrustEvaluator sourceTrustEvaluator) {
        this.webSearch = Objects.requireNonNull(webSearch);
        this.sourceTrustEvaluator = Objects.requireNonNull(sourceTrustEvaluator);
    }

    @Override
    public ResearchStepType stepType() {
        return ResearchStepType.SEARCH;
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context) {
        int maxQueries = context.maxSearchQueries() > 0 ? context.maxSearchQueries() : context.searchQueries().size();
        int maxSources = context.maxSources() > 0 ? context.maxSources() : Integer.MAX_VALUE;
        List<String> queries = context.searchQueries().stream()
                .limit(maxQueries)
                .toList();
        Set<String> seenHashes = new HashSet<>();
        for (WebSource source : context.sources()) {
            if (source.contentHash() != null && !source.contentHash().isBlank()) {
                seenHashes.add(source.contentHash());
            }
        }
        for (String query : queries) {
            if (context.sources().size() >= maxSources) {
                break;
            }
            int remainingSources = maxSources - context.sources().size();
            WebSearchResult result = webSearch.search(
                    new WebSearchRequest(query, null, null, Math.min(MAX_RESULTS_PER_QUERY, remainingSources)));
            for (WebSearchHit hit : result.hits()) {
                if (context.sources().size() >= maxSources) {
                    break;
                }
                Instant retrievedAt = Instant.now();
                WebSource source = new WebSource(
                        SnowflakeIds.nextIdString(),
                        task.runId(),
                        hit.url(),
                        hit.title(),
                        hit.snippet(),
                        retrievedAt,
                        SourceTrustLevel.UNTRUSTED,
                        null,
                        ExtractionStatus.PENDING);
                String contentHash = sourceTrustEvaluator.contentHash(source);
                if (!seenHashes.add(contentHash)) {
                    continue;
                }
                context.addSource(new WebSource(
                        source.sourceId(),
                        source.runId(),
                        source.url(),
                        source.title(),
                        source.snippet(),
                        source.retrievedAt(),
                        sourceTrustEvaluator.evaluate(source),
                        contentHash,
                        source.extractionStatus()));
            }
        }

        if (context.sources().isEmpty()) {
            throw new RetryableResearchException("搜索未返回结果，稍后重试");
        }
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context, ResearchEventPublisher events) {
        int previousSize = context.sources().size();
        execute(task, context);
        List<WebSource> newSources = context.sources().stream().skip(previousSize).toList();
        if (!newSources.isEmpty()) {
            Objects.requireNonNullElseGet(events, ResearchEventPublisher::noop)
                    .publish(task.runId(), context, StreamEventType.SOURCE_FOUND, newSources);
        }
    }
}
