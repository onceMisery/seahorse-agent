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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.EvidenceItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ExtractionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.SourceTrustLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 研究步骤执行上下文，在步骤间传递中间结果。
 *
 * <p>每个 run 对应一个 context 实例，步骤按顺序写入搜索结果、抓取内容、证据和报告。
 */
public class ResearchStepContext {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String runId;
    private final String query;
    private final AtomicLong seqCounter;
    private final List<String> searchQueries = new ArrayList<>();
    private final List<WebSource> sources = new ArrayList<>();
    private final List<EvidenceItem> evidence = new ArrayList<>();
    private final ConcurrentMap<String, String> fetchedContent = new ConcurrentHashMap<>();
    private String reportContent;
    private String tenantId;
    private String userId;
    private String artifactId;
    private int maxSearchQueries;
    private int maxSources;

    public ResearchStepContext(String runId, String query) {
        this(runId, query, 0L);
    }

    public ResearchStepContext(String runId, String query, long initialSeq) {
        this.runId = runId;
        this.query = query;
        this.seqCounter = new AtomicLong(initialSeq);
    }

    public long nextSeq() {
        return seqCounter.incrementAndGet();
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

    public String tenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String userId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String artifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public int maxSearchQueries() { return maxSearchQueries; }
    public void setMaxSearchQueries(int maxSearchQueries) { this.maxSearchQueries = Math.max(0, maxSearchQueries); }

    public int maxSources() { return maxSources; }
    public void setMaxSources(int maxSources) { this.maxSources = Math.max(0, maxSources); }

    /**
     * 将上下文序列化为可写入 DurableTask.payloadJson 的 JSON 字符串。
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(snapshot());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize research context", ex);
        }
    }

    /**
     * 从 DurableTask.payloadJson 还原上下文。空 payload 返回 null，调用方按需新建。
     */
    public static ResearchStepContext fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Snapshot snap = MAPPER.readValue(json, Snapshot.class);
            return restore(snap);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize research context", ex);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                runId,
                query,
                seqCounter.get(),
                List.copyOf(searchQueries),
                sources.stream().map(SourceSnapshot::of).toList(),
                List.copyOf(evidence),
                new LinkedHashMap<>(fetchedContent),
                reportContent,
                tenantId,
                userId,
                artifactId,
                maxSearchQueries,
                maxSources);
    }

    public static ResearchStepContext restore(Snapshot snap) {
        Objects.requireNonNull(snap, "snapshot must not be null");
        ResearchStepContext ctx = new ResearchStepContext(snap.runId(), snap.query(), snap.seqCounter());
        if (snap.searchQueries() != null) {
            ctx.searchQueries.addAll(snap.searchQueries());
        }
        if (snap.sources() != null) {
            for (SourceSnapshot s : snap.sources()) {
                ctx.sources.add(s.toSource());
            }
        }
        if (snap.evidence() != null) {
            ctx.evidence.addAll(snap.evidence());
        }
        if (snap.fetchedContent() != null) {
            ctx.fetchedContent.putAll(snap.fetchedContent());
        }
        ctx.reportContent = snap.reportContent();
        ctx.tenantId = snap.tenantId();
        ctx.userId = snap.userId();
        ctx.artifactId = snap.artifactId();
        ctx.maxSearchQueries = snap.maxSearchQueries();
        ctx.maxSources = snap.maxSources();
        return ctx;
    }

    /**
     * 上下文快照，用于 JSON 持久化（避免直接序列化 Instant 等复杂类型）。
     */
    public record Snapshot(
            String runId,
            String query,
            long seqCounter,
            List<String> searchQueries,
            List<SourceSnapshot> sources,
            List<EvidenceItem> evidence,
            Map<String, String> fetchedContent,
            String reportContent,
            String tenantId,
            String userId,
            String artifactId,
            int maxSearchQueries,
            int maxSources
    ) {
        public Snapshot {
            searchQueries = searchQueries == null ? List.of() : List.copyOf(searchQueries);
            sources = sources == null ? List.of() : List.copyOf(sources);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            fetchedContent = fetchedContent == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fetchedContent));
            maxSearchQueries = Math.max(0, maxSearchQueries);
            maxSources = Math.max(0, maxSources);
        }
    }

    /**
     * WebSource 的可序列化镜像：把 Instant 转成 epoch millis。
     */
    public record SourceSnapshot(
            String sourceId,
            String runId,
            String url,
            String title,
            String snippet,
            Long retrievedAtMillis,
            SourceTrustLevel trustLevel,
            String contentHash,
            ExtractionStatus extractionStatus
    ) {
        public static SourceSnapshot of(WebSource s) {
            return new SourceSnapshot(
                    s.sourceId(), s.runId(), s.url(), s.title(), s.snippet(),
                    s.retrievedAt() == null ? null : s.retrievedAt().toEpochMilli(),
                    s.trustLevel(), s.contentHash(), s.extractionStatus());
        }

        public WebSource toSource() {
            return new WebSource(
                    sourceId, runId, url, title, snippet,
                    retrievedAtMillis == null ? null : Instant.ofEpochMilli(retrievedAtMillis),
                    trustLevel, contentHash, extractionStatus);
        }
    }
}
