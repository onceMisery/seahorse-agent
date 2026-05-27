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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.EvidenceItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 研究步骤执行上下文，在步骤间传递中间结果。
 *
 * <p>每个 run 对应一个 context 实例，步骤按顺序写入搜索结果、抓取内容、证据和报告。
 */
public class ResearchStepContext {

    private final String runId;
    private final String query;
    private final List<String> searchQueries = new ArrayList<>();
    private final List<WebSource> sources = new ArrayList<>();
    private final List<EvidenceItem> evidence = new ArrayList<>();
    private final ConcurrentMap<String, String> fetchedContent = new ConcurrentHashMap<>();
    private String reportContent;

    public ResearchStepContext(String runId, String query) {
        this.runId = runId;
        this.query = query;
    }

    public String runId() { return runId; }
    public String query() { return query; }

    public List<String> searchQueries() { return searchQueries; }
    public void addSearchQuery(String q) { searchQueries.add(q); }

    public List<WebSource> sources() { return sources; }
    public void addSource(WebSource source) { sources.add(source); }

    public List<EvidenceItem> evidence() { return evidence; }
    public void addEvidence(EvidenceItem item) { evidence.add(item); }

    public void putFetchedContent(String sourceId, String content) { fetchedContent.put(sourceId, content); }
    public String getFetchedContent(String sourceId) { return fetchedContent.get(sourceId); }
    public ConcurrentMap<String, String> allFetchedContent() { return fetchedContent; }

    public String reportContent() { return reportContent; }
    public void setReportContent(String content) { this.reportContent = content; }
}
