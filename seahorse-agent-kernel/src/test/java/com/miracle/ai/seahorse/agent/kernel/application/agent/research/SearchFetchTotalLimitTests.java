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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ExtractionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.SourceTrustLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchHit;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchFetchTotalLimitTests {

    @Test
    void searchHonorsQueryAndSourceLimitsFromContext() {
        RecordingSearchPort webSearch = new RecordingSearchPort();
        SearchStepHandler handler = new SearchStepHandler(webSearch);
        ResearchStepContext context = new ResearchStepContext("run-limit", "q", 0L);
        context.setMaxSearchQueries(2);
        context.setMaxSources(4);
        context.addSearchQuery("query-1");
        context.addSearchQuery("query-2");
        context.addSearchQuery("query-3");

        handler.execute(task("SEARCH"), context);

        assertEquals(2, webSearch.requests.size());
        assertEquals(List.of("query-1", "query-2"), webSearch.requests.stream().map(WebSearchRequest::query).toList());
        assertEquals(4, context.sources().size());
    }

    @Test
    void searchEvaluatesTrustAndDeduplicatesSourcesByContentHash() {
        RecordingSearchPort webSearch = new RecordingSearchPort(List.of(
                new WebSearchHit(
                        "Wikipedia",
                        "https://en.wikipedia.org/wiki/Seahorse",
                        "A detailed encyclopedia entry with enough summary text for a useful research citation.",
                        WebSourceType.SEARCH_RESULT,
                        Instant.EPOCH,
                        1.0d),
                new WebSearchHit(
                        "Wikipedia",
                        "https://en.wikipedia.org/wiki/Seahorse",
                        "A detailed encyclopedia entry with enough summary text for a useful research citation.",
                        WebSourceType.SEARCH_RESULT,
                        Instant.EPOCH,
                        0.9d)));
        SearchStepHandler handler = new SearchStepHandler(webSearch);
        ResearchStepContext context = new ResearchStepContext("run-trust", "q", 0L);
        context.addSearchQuery("seahorse");

        handler.execute(task("SEARCH"), context);

        assertEquals(1, context.sources().size());
        WebSource source = context.sources().get(0);
        assertEquals(SourceTrustLevel.HIGH, source.trustLevel());
        assertEquals(64, source.contentHash().length());
    }

    @Test
    void searchPublishesSourcesWithTrustMetadata() {
        RecordingSearchPort webSearch = new RecordingSearchPort(List.of(
                new WebSearchHit(
                        "Wikipedia",
                        "https://en.wikipedia.org/wiki/Seahorse",
                        "A detailed encyclopedia entry with enough summary text for a useful research citation.",
                        WebSourceType.SEARCH_RESULT,
                        Instant.EPOCH,
                        1.0d)));
        SearchStepHandler handler = new SearchStepHandler(webSearch);
        ResearchStepContext context = new ResearchStepContext("run-source-event", "q", 0L);
        context.addSearchQuery("seahorse");
        RecordingResearchEvents events = new RecordingResearchEvents();

        handler.execute(task("SEARCH"), context, events);

        assertEquals(1, events.events.size());
        assertEquals(StreamEventType.SOURCE_FOUND, events.events.get(0).type());
        @SuppressWarnings("unchecked")
        List<WebSource> sources = (List<WebSource>) events.events.get(0).payload();
        assertEquals(SourceTrustLevel.HIGH, sources.get(0).trustLevel());
        assertEquals(64, sources.get(0).contentHash().length());
    }

    @Test
    void fetchHonorsSourceLimitFromContext() {
        RecordingFetchPort webFetch = new RecordingFetchPort();
        FetchStepHandler handler = new FetchStepHandler(webFetch);
        ResearchStepContext context = new ResearchStepContext("run-fetch-limit", "q", 0L);
        context.setMaxSources(2);
        context.addSource(source("source-1", "https://example.com/1"));
        context.addSource(source("source-2", "https://example.com/2"));
        context.addSource(source("source-3", "https://example.com/3"));

        handler.execute(task("FETCH"), context);

        assertEquals(2, webFetch.requests.size());
        assertEquals("body https://example.com/1", context.getFetchedContent("source-1"));
        assertEquals("body https://example.com/2", context.getFetchedContent("source-2"));
    }

    @Test
    void fetchPrioritizesHighAndMediumTrustSources() {
        RecordingFetchPort webFetch = new RecordingFetchPort();
        FetchStepHandler handler = new FetchStepHandler(webFetch);
        ResearchStepContext context = new ResearchStepContext("run-fetch-rank", "q", 0L);
        context.setMaxSources(2);
        context.addSource(source("low", "https://low.example/1", SourceTrustLevel.LOW));
        context.addSource(source("high", "https://high.example/1", SourceTrustLevel.HIGH));
        context.addSource(source("medium", "https://medium.example/1", SourceTrustLevel.MEDIUM));

        handler.execute(task("FETCH"), context);

        assertEquals(
                List.of("https://high.example/1", "https://medium.example/1"),
                webFetch.requests.stream().map(WebFetchRequest::url).toList());
    }

    private static DurableTask task(String stepType) {
        return new DurableTask("task-1", "run-limit", stepType, 0, Instant.now(), null, null);
    }

    private static WebSource source(String sourceId, String url) {
        return source(sourceId, url, SourceTrustLevel.UNTRUSTED);
    }

    private static WebSource source(String sourceId, String url, SourceTrustLevel trustLevel) {
        return new WebSource(
                sourceId,
                "run-limit",
                url,
                "title",
                "snippet",
                Instant.EPOCH,
                trustLevel,
                null,
                ExtractionStatus.PENDING);
    }

    private static final class RecordingSearchPort implements WebSearchPort {
        private final List<WebSearchRequest> requests = new ArrayList<>();
        private final List<WebSearchHit> fixedHits;

        private RecordingSearchPort() {
            this.fixedHits = null;
        }

        private RecordingSearchPort(List<WebSearchHit> fixedHits) {
            this.fixedHits = List.copyOf(fixedHits);
        }

        @Override
        public WebSearchResult search(WebSearchRequest request) {
            requests.add(request);
            if (fixedHits != null) {
                return new WebSearchResult(request.query(), fixedHits);
            }
            return new WebSearchResult(request.query(), List.of(
                    hit(request.query(), 1),
                    hit(request.query(), 2),
                    hit(request.query(), 3)));
        }

        private static WebSearchHit hit(String query, int index) {
            return new WebSearchHit(
                    "title " + query + " " + index,
                    "https://example.com/" + query + "/" + index,
                    "snippet",
                    WebSourceType.SEARCH_RESULT,
                    Instant.EPOCH,
                    1.0d);
        }
    }

    private static final class RecordingFetchPort implements WebFetchPort {
        private final List<WebFetchRequest> requests = new ArrayList<>();

        @Override
        public WebFetchResult fetch(WebFetchRequest request) {
            requests.add(request);
            return WebFetchResult.fetched(request.url(), "title", "body " + request.url(), "text/html", false);
        }
    }

    private static final class RecordingResearchEvents implements ResearchEventPublisher {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void publish(String runId, ResearchStepContext context, StreamEventType type, Object payload) {
            events.add(new Event(type, payload));
        }
    }

    private record Event(StreamEventType type, Object payload) {
    }
}
