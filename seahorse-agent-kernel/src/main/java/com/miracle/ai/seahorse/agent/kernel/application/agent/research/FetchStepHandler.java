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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Objects;

/**
 * FETCH 步骤：抓取 SEARCH 阶段发现的网页内容。
 *
 * <p>所有外部网页内容标记为不可信（UNTRUSTED），由后续步骤进行证据提取。
 */
public class FetchStepHandler implements ResearchStepHandler {

    private static final Logger log = LoggerFactory.getLogger(FetchStepHandler.class);
    private static final int MAX_FETCH_COUNT = 10;

    private final WebFetchPort webFetch;

    public FetchStepHandler(WebFetchPort webFetch) {
        this.webFetch = Objects.requireNonNull(webFetch);
    }

    @Override
    public ResearchStepType stepType() {
        return ResearchStepType.FETCH;
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context) {
        int fetched = 0;
        int maxFetchCount = context.maxSources() > 0 ? context.maxSources() : MAX_FETCH_COUNT;
        for (WebSource source : context.sources().stream()
                .sorted(Comparator.comparingInt(FetchStepHandler::trustRank).reversed())
                .toList()) {
            if (fetched >= maxFetchCount) break;
            try {
                WebFetchResult result = webFetch.fetch(new WebFetchRequest(source.url(), 8000));
                if (result.status() == WebFetchStatus.FETCHED && !result.contentText().isBlank()) {
                    context.putFetchedContent(source.sourceId(), result.contentText());
                    fetched++;
                } else {
                    log.debug("Fetch skipped: url={}, status={}, reason={}",
                            source.url(), result.status(), result.reasonCode());
                }
            } catch (Exception e) {
                log.debug("Fetch failed: url={}", source.url(), e);
            }
        }
    }

    private static int trustRank(WebSource source) {
        SourceTrustLevel trustLevel = Objects.requireNonNullElse(source.trustLevel(), SourceTrustLevel.UNTRUSTED);
        return switch (trustLevel) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            case UNTRUSTED -> 0;
        };
    }
}
