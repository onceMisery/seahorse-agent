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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.SourceTrustLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ExtractionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchHit;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchResult;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * SEARCH 步骤：对 PLAN 阶段生成的每个查询词执行 Web 搜索。
 */
public class SearchStepHandler implements ResearchStepHandler {

    private static final int MAX_RESULTS_PER_QUERY = 5;

    private final WebSearchPort webSearch;

    public SearchStepHandler(WebSearchPort webSearch) {
        this.webSearch = Objects.requireNonNull(webSearch);
    }

    @Override
    public ResearchStepType stepType() {
        return ResearchStepType.SEARCH;
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context) {
        for (String query : context.searchQueries()) {
            WebSearchResult result = webSearch.search(
                    new WebSearchRequest(query, null, null, MAX_RESULTS_PER_QUERY));
            for (WebSearchHit hit : result.hits()) {
                WebSource source = new WebSource(
                        UUID.randomUUID().toString(),
                        task.runId(),
                        hit.url(),
                        hit.title(),
                        hit.snippet(),
                        Instant.now(),
                        SourceTrustLevel.UNTRUSTED,
                        null,
                        ExtractionStatus.PENDING);
                context.addSource(source);
            }
        }

        if (context.sources().isEmpty()) {
            throw new RetryableResearchException("搜索未返回结果，稍后重试");
        }
    }
}
