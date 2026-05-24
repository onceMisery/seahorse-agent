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

package com.miracle.ai.seahorse.agent.adapters.spring.properties;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Slice 4：记忆配置 properties 基线。
 *
 * <p>spec §9 拆分目标 — 现阶段 {@code SeahorseAgentKernelMemoryAutoConfiguration} 持有 83 处
 * {@code @Value}，难以发现、迁移与测试。本类按 6 个能力域分组（{@code policy}、{@code recall}、
 * {@code aggregation}、{@code outbox}、{@code maintenance}、{@code refiner}），后续切片将逐步把
 * 各子域的 {@code @Value} 替换为对本类的 binding。
 *
 * <p>当前切片仅落地 {@link Aggregation} 子段（首个迁移子域），其余子段保留为占位嵌套类，等待后续
 * 切片填充，避免一次性大改导致 starter 风险面爆炸（spec §1 中风险等级 - "中"）。
 *
 * <p>spec §9.3 约束：保留所有现有 property key，不做破坏性重命名；每个子 auto configuration 聚焦
 * 一个能力域。
 */
@ConfigurationProperties(prefix = "seahorse-agent.memory")
public class MemoryProperties {

    private final Policy policy = new Policy();
    private final Recall recall = new Recall();
    private final Aggregation aggregation = new Aggregation();
    private final Outbox outbox = new Outbox();
    private final Maintenance maintenance = new Maintenance();
    private final Refiner refiner = new Refiner();
    private final AliasResolution aliasResolution = new AliasResolution();
    private final Trace trace = new Trace();

    public Policy getPolicy() {
        return policy;
    }

    public Recall getRecall() {
        return recall;
    }

    public Aggregation getAggregation() {
        return aggregation;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Maintenance getMaintenance() {
        return maintenance;
    }

    public Refiner getRefiner() {
        return refiner;
    }

    public AliasResolution getAliasResolution() {
        return aliasResolution;
    }

    public Trace getTrace() {
        return trace;
    }

    /**
     * Slice 4 续：策略阈值与开关，覆盖 capture / risk / refiner / 告警阈值 / 灰度。
     *
     * <p>所有字段默认值与历史 {@code @Value("${seahorse-agent.memory.policy.*}")} 完全一致。
     */
    public static class Policy {

        private double captureAcceptThreshold = 0.4d;
        private double highValueThreshold = 0.75d;
        private double riskRejectThreshold = 0.7d;
        private int tokenBudget = 2400;
        private boolean reviewEnabled = false;
        private double refinerDropConfidenceThreshold = 0.5d;
        private double refinerAutoCommitConfidenceThreshold = 0.85d;
        private double refinerReviewRiskThreshold = 0.7d;
        private int schemaFailureAlertThreshold = 0;
        private int outboxBacklogAlertThreshold = 0;
        private String greyReleaseKey = "";

        public double getCaptureAcceptThreshold() {
            return captureAcceptThreshold;
        }

        public void setCaptureAcceptThreshold(double captureAcceptThreshold) {
            this.captureAcceptThreshold = captureAcceptThreshold;
        }

        public double getHighValueThreshold() {
            return highValueThreshold;
        }

        public void setHighValueThreshold(double highValueThreshold) {
            this.highValueThreshold = highValueThreshold;
        }

        public double getRiskRejectThreshold() {
            return riskRejectThreshold;
        }

        public void setRiskRejectThreshold(double riskRejectThreshold) {
            this.riskRejectThreshold = riskRejectThreshold;
        }

        public int getTokenBudget() {
            return tokenBudget;
        }

        public void setTokenBudget(int tokenBudget) {
            this.tokenBudget = tokenBudget;
        }

        public boolean isReviewEnabled() {
            return reviewEnabled;
        }

        public void setReviewEnabled(boolean reviewEnabled) {
            this.reviewEnabled = reviewEnabled;
        }

        public double getRefinerDropConfidenceThreshold() {
            return refinerDropConfidenceThreshold;
        }

        public void setRefinerDropConfidenceThreshold(double refinerDropConfidenceThreshold) {
            this.refinerDropConfidenceThreshold = refinerDropConfidenceThreshold;
        }

        public double getRefinerAutoCommitConfidenceThreshold() {
            return refinerAutoCommitConfidenceThreshold;
        }

        public void setRefinerAutoCommitConfidenceThreshold(double refinerAutoCommitConfidenceThreshold) {
            this.refinerAutoCommitConfidenceThreshold = refinerAutoCommitConfidenceThreshold;
        }

        public double getRefinerReviewRiskThreshold() {
            return refinerReviewRiskThreshold;
        }

        public void setRefinerReviewRiskThreshold(double refinerReviewRiskThreshold) {
            this.refinerReviewRiskThreshold = refinerReviewRiskThreshold;
        }

        public int getSchemaFailureAlertThreshold() {
            return schemaFailureAlertThreshold;
        }

        public void setSchemaFailureAlertThreshold(int schemaFailureAlertThreshold) {
            this.schemaFailureAlertThreshold = schemaFailureAlertThreshold;
        }

        public int getOutboxBacklogAlertThreshold() {
            return outboxBacklogAlertThreshold;
        }

        public void setOutboxBacklogAlertThreshold(int outboxBacklogAlertThreshold) {
            this.outboxBacklogAlertThreshold = outboxBacklogAlertThreshold;
        }

        public String getGreyReleaseKey() {
            return greyReleaseKey;
        }

        public void setGreyReleaseKey(String greyReleaseKey) {
            this.greyReleaseKey = greyReleaseKey;
        }
    }

    /**
     * 占位：召回相关 rrf / decay / channel / rerank 等参数，迁移待后续切片。
     */
    public static class Recall {

        private int rrfK = 60;
        private double decayLambda = 0.05d;
        private int finalTopK = 8;
        private boolean timeDecayEnabled = true;
        private long channelTimeoutMs = 50L;
        private final Map<String, Double> channelWeights = new LinkedHashMap<>();
        private String rerankModel = "";
        private int rerankInputTopK = 8;
        private int rerankMaxTextChars = 4000;
        private String vectorCollection = "memory_vectors";
        private String embeddingModel = "";
        private int graphMaxHops = 1;
        private int channelTopK = 20;

        public int getRrfK() {
            return rrfK;
        }

        public void setRrfK(int rrfK) {
            this.rrfK = rrfK;
        }

        public double getDecayLambda() {
            return decayLambda;
        }

        public void setDecayLambda(double decayLambda) {
            this.decayLambda = decayLambda;
        }

        public int getFinalTopK() {
            return finalTopK;
        }

        public void setFinalTopK(int finalTopK) {
            this.finalTopK = finalTopK;
        }

        public boolean isTimeDecayEnabled() {
            return timeDecayEnabled;
        }

        public void setTimeDecayEnabled(boolean timeDecayEnabled) {
            this.timeDecayEnabled = timeDecayEnabled;
        }

        public long getChannelTimeoutMs() {
            return channelTimeoutMs;
        }

        public void setChannelTimeoutMs(long channelTimeoutMs) {
            this.channelTimeoutMs = channelTimeoutMs;
        }

        public Map<String, Double> getChannelWeights() {
            return channelWeights;
        }

        public String getRerankModel() {
            return rerankModel;
        }

        public void setRerankModel(String rerankModel) {
            this.rerankModel = rerankModel;
        }

        public int getRerankInputTopK() {
            return rerankInputTopK;
        }

        public void setRerankInputTopK(int rerankInputTopK) {
            this.rerankInputTopK = rerankInputTopK;
        }

        public int getRerankMaxTextChars() {
            return rerankMaxTextChars;
        }

        public void setRerankMaxTextChars(int rerankMaxTextChars) {
            this.rerankMaxTextChars = rerankMaxTextChars;
        }

        public String getVectorCollection() {
            return vectorCollection;
        }

        public void setVectorCollection(String vectorCollection) {
            this.vectorCollection = vectorCollection;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public int getGraphMaxHops() {
            return graphMaxHops;
        }

        public void setGraphMaxHops(int graphMaxHops) {
            this.graphMaxHops = graphMaxHops;
        }

        public int getChannelTopK() {
            return channelTopK;
        }

        public void setChannelTopK(int channelTopK) {
            this.channelTopK = channelTopK;
        }
    }

    /**
     * Slice 4 第一个迁移子域：对话聚合策略。
     *
     * <p>所有字段及默认值与历史 {@code @Value("${seahorse-agent.memory.aggregation.*}")}
     * 完全一致；改动 default 必须同步升级集成测试。
     */
    public static class Aggregation {

        private boolean enabled = false;
        private long idleFlushMillis = 40000L;
        private int maxTurns = 10;
        private int maxTokens = 2000;
        private int maxContextBlocks = 32;
        private long bufferTtlMillis = 86400000L;
        private boolean captureOnError = false;
        private boolean topicShiftFlushEnabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIdleFlushMillis() {
            return idleFlushMillis;
        }

        public void setIdleFlushMillis(long idleFlushMillis) {
            this.idleFlushMillis = idleFlushMillis;
        }

        public int getMaxTurns() {
            return maxTurns;
        }

        public void setMaxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getMaxContextBlocks() {
            return maxContextBlocks;
        }

        public void setMaxContextBlocks(int maxContextBlocks) {
            this.maxContextBlocks = maxContextBlocks;
        }

        public long getBufferTtlMillis() {
            return bufferTtlMillis;
        }

        public void setBufferTtlMillis(long bufferTtlMillis) {
            this.bufferTtlMillis = bufferTtlMillis;
        }

        public boolean isCaptureOnError() {
            return captureOnError;
        }

        public void setCaptureOnError(boolean captureOnError) {
            this.captureOnError = captureOnError;
        }

        public boolean isTopicShiftFlushEnabled() {
            return topicShiftFlushEnabled;
        }

        public void setTopicShiftFlushEnabled(boolean topicShiftFlushEnabled) {
            this.topicShiftFlushEnabled = topicShiftFlushEnabled;
        }
    }

    /**
     * Slice 4 续：outbox relay 调度参数。
     *
     * <p>{@code relay-enabled} 由 {@code @ConditionalOnProperty} 控制 bean 创建，
     * 此处保留属性以便未来下沉判断；当前仅 {@code relay-batch-size} 参与运行时绑定。
     */
    public static class Outbox {

        private int relayBatchSize = 50;

        public int getRelayBatchSize() {
            return relayBatchSize;
        }

        public void setRelayBatchSize(int relayBatchSize) {
            this.relayBatchSize = relayBatchSize;
        }
    }

    /**
     * Slice 4 续：维护任务（compaction / gc / alias / 总控）配置。
     *
     * <p>本次迁移仅落地 compaction 子段；gc / alias-resolution / maintenance enabled 旗标
     * 在后续切片继续迁移，避免一次性大改。
     */
    public static class Maintenance {

        private final Compaction compaction = new Compaction();
        private final Gc gc = new Gc();

        private boolean compactionEnabled = false;
        private boolean aliasEnabled = false;
        private boolean gcEnabled = true;

        public Compaction getCompaction() {
            return compaction;
        }

        public Gc getGc() {
            return gc;
        }

        public boolean isCompactionEnabled() {
            return compactionEnabled;
        }

        public void setCompactionEnabled(boolean compactionEnabled) {
            this.compactionEnabled = compactionEnabled;
        }

        public boolean isAliasEnabled() {
            return aliasEnabled;
        }

        public void setAliasEnabled(boolean aliasEnabled) {
            this.aliasEnabled = aliasEnabled;
        }

        public boolean isGcEnabled() {
            return gcEnabled;
        }

        public void setGcEnabled(boolean gcEnabled) {
            this.gcEnabled = gcEnabled;
        }

        public static class Compaction {

            private int scanLimit = 100;
            private int minGroupSize = 3;
            private boolean vectorIndexEnabled = true;
            private boolean keywordIndexEnabled = true;
            private boolean graphIndexEnabled = true;
            private String embeddingModel = "default";

            public int getScanLimit() {
                return scanLimit;
            }

            public void setScanLimit(int scanLimit) {
                this.scanLimit = scanLimit;
            }

            public int getMinGroupSize() {
                return minGroupSize;
            }

            public void setMinGroupSize(int minGroupSize) {
                this.minGroupSize = minGroupSize;
            }

            public boolean isVectorIndexEnabled() {
                return vectorIndexEnabled;
            }

            public void setVectorIndexEnabled(boolean vectorIndexEnabled) {
                this.vectorIndexEnabled = vectorIndexEnabled;
            }

            public boolean isKeywordIndexEnabled() {
                return keywordIndexEnabled;
            }

            public void setKeywordIndexEnabled(boolean keywordIndexEnabled) {
                this.keywordIndexEnabled = keywordIndexEnabled;
            }

            public boolean isGraphIndexEnabled() {
                return graphIndexEnabled;
            }

            public void setGraphIndexEnabled(boolean graphIndexEnabled) {
                this.graphIndexEnabled = graphIndexEnabled;
            }

            public String getEmbeddingModel() {
                return embeddingModel;
            }

            public void setEmbeddingModel(String embeddingModel) {
                this.embeddingModel = embeddingModel;
            }
        }

        public static class Gc {

            private int scanLimit = 100;
            private long retentionDays = 7L;
            private boolean dryRun = false;
            private boolean vectorIndexEnabled = true;
            private boolean keywordIndexEnabled = true;
            private boolean graphIndexEnabled = true;
            private boolean archiveEnabled = false;
            private long archiveIdleDays = 90L;
            private double archiveScoreThreshold = 0.15d;
            private boolean physicalDeleteEnabled = false;
            private long physicalDeleteRetentionDays = 30L;

            public int getScanLimit() {
                return scanLimit;
            }

            public void setScanLimit(int scanLimit) {
                this.scanLimit = scanLimit;
            }

            public long getRetentionDays() {
                return retentionDays;
            }

            public void setRetentionDays(long retentionDays) {
                this.retentionDays = retentionDays;
            }

            public boolean isDryRun() {
                return dryRun;
            }

            public void setDryRun(boolean dryRun) {
                this.dryRun = dryRun;
            }

            public boolean isVectorIndexEnabled() {
                return vectorIndexEnabled;
            }

            public void setVectorIndexEnabled(boolean vectorIndexEnabled) {
                this.vectorIndexEnabled = vectorIndexEnabled;
            }

            public boolean isKeywordIndexEnabled() {
                return keywordIndexEnabled;
            }

            public void setKeywordIndexEnabled(boolean keywordIndexEnabled) {
                this.keywordIndexEnabled = keywordIndexEnabled;
            }

            public boolean isGraphIndexEnabled() {
                return graphIndexEnabled;
            }

            public void setGraphIndexEnabled(boolean graphIndexEnabled) {
                this.graphIndexEnabled = graphIndexEnabled;
            }

            public boolean isArchiveEnabled() {
                return archiveEnabled;
            }

            public void setArchiveEnabled(boolean archiveEnabled) {
                this.archiveEnabled = archiveEnabled;
            }

            public long getArchiveIdleDays() {
                return archiveIdleDays;
            }

            public void setArchiveIdleDays(long archiveIdleDays) {
                this.archiveIdleDays = archiveIdleDays;
            }

            public double getArchiveScoreThreshold() {
                return archiveScoreThreshold;
            }

            public void setArchiveScoreThreshold(double archiveScoreThreshold) {
                this.archiveScoreThreshold = archiveScoreThreshold;
            }

            public boolean isPhysicalDeleteEnabled() {
                return physicalDeleteEnabled;
            }

            public void setPhysicalDeleteEnabled(boolean physicalDeleteEnabled) {
                this.physicalDeleteEnabled = physicalDeleteEnabled;
            }

            public long getPhysicalDeleteRetentionDays() {
                return physicalDeleteRetentionDays;
            }

            public void setPhysicalDeleteRetentionDays(long physicalDeleteRetentionDays) {
                this.physicalDeleteRetentionDays = physicalDeleteRetentionDays;
            }
        }
    }

    /**
     * Slice 4 续：refiner 引擎运行参数（fail-open / batch / sticky anchor 等）。
     */
    public static class Refiner {

        private boolean failOpen = true;
        private int maxBatchOperations = 8;
        private double maxDeleteRatio = 0.7d;
        private int readMaskPerLayerLimit = 3;
        private int targetZoneTurnCount = 3;
        private int stickyAnchorLimit = 5;
        private int feedbackExampleLimit = 3;
        private double stickyAnchorImportanceThreshold = 0.85d;
        private double stickyAnchorConfidenceThreshold = 0.90d;

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }

        public int getMaxBatchOperations() {
            return maxBatchOperations;
        }

        public void setMaxBatchOperations(int maxBatchOperations) {
            this.maxBatchOperations = maxBatchOperations;
        }

        public double getMaxDeleteRatio() {
            return maxDeleteRatio;
        }

        public void setMaxDeleteRatio(double maxDeleteRatio) {
            this.maxDeleteRatio = maxDeleteRatio;
        }

        public int getReadMaskPerLayerLimit() {
            return readMaskPerLayerLimit;
        }

        public void setReadMaskPerLayerLimit(int readMaskPerLayerLimit) {
            this.readMaskPerLayerLimit = readMaskPerLayerLimit;
        }

        public int getTargetZoneTurnCount() {
            return targetZoneTurnCount;
        }

        public void setTargetZoneTurnCount(int targetZoneTurnCount) {
            this.targetZoneTurnCount = targetZoneTurnCount;
        }

        public int getStickyAnchorLimit() {
            return stickyAnchorLimit;
        }

        public void setStickyAnchorLimit(int stickyAnchorLimit) {
            this.stickyAnchorLimit = stickyAnchorLimit;
        }

        public int getFeedbackExampleLimit() {
            return feedbackExampleLimit;
        }

        public void setFeedbackExampleLimit(int feedbackExampleLimit) {
            this.feedbackExampleLimit = feedbackExampleLimit;
        }

        public double getStickyAnchorImportanceThreshold() {
            return stickyAnchorImportanceThreshold;
        }

        public void setStickyAnchorImportanceThreshold(double stickyAnchorImportanceThreshold) {
            this.stickyAnchorImportanceThreshold = stickyAnchorImportanceThreshold;
        }

        public double getStickyAnchorConfidenceThreshold() {
            return stickyAnchorConfidenceThreshold;
        }

        public void setStickyAnchorConfidenceThreshold(double stickyAnchorConfidenceThreshold) {
            this.stickyAnchorConfidenceThreshold = stickyAnchorConfidenceThreshold;
        }
    }

    /**
     * Slice 4 续：alias-resolution 段。
     *
     * <p>保留 {@code seahorse-agent.memory.alias-resolution.dictionary} 顶层 map 形态，
     * 配合 starter bean 工厂将 {@link DictionaryEntry} 转换为内核侧 candidate；
     * Properties 自身仅作 POJO 绑定，避免内核类型反向依赖。
     */
    public static class AliasResolution {

        private int scanLimit = 100;
        private double autoResolveConfidenceThreshold = 0.95d;
        private final Map<String, DictionaryEntry> dictionary = new LinkedHashMap<>();

        public int getScanLimit() {
            return scanLimit;
        }

        public void setScanLimit(int scanLimit) {
            this.scanLimit = scanLimit;
        }

        public double getAutoResolveConfidenceThreshold() {
            return autoResolveConfidenceThreshold;
        }

        public void setAutoResolveConfidenceThreshold(double autoResolveConfidenceThreshold) {
            this.autoResolveConfidenceThreshold = autoResolveConfidenceThreshold;
        }

        public Map<String, DictionaryEntry> getDictionary() {
            return dictionary;
        }

        public static class DictionaryEntry {

            private String userId;
            private String tenantId;
            private String aliasText;
            private String canonicalEntityId;
            private String canonicalName;
            private String entityType;
            private double confidenceLevel;

            public String getUserId() {
                return userId;
            }

            public void setUserId(String userId) {
                this.userId = userId;
            }

            public String getTenantId() {
                return tenantId;
            }

            public void setTenantId(String tenantId) {
                this.tenantId = tenantId;
            }

            public String getAliasText() {
                return aliasText;
            }

            public void setAliasText(String aliasText) {
                this.aliasText = aliasText;
            }

            public String getCanonicalEntityId() {
                return canonicalEntityId;
            }

            public void setCanonicalEntityId(String canonicalEntityId) {
                this.canonicalEntityId = canonicalEntityId;
            }

            public String getCanonicalName() {
                return canonicalName;
            }

            public void setCanonicalName(String canonicalName) {
                this.canonicalName = canonicalName;
            }

            public String getEntityType() {
                return entityType;
            }

            public void setEntityType(String entityType) {
                this.entityType = entityType;
            }

            public double getConfidenceLevel() {
                return confidenceLevel;
            }

            public void setConfidenceLevel(double confidenceLevel) {
                this.confidenceLevel = confidenceLevel;
            }
        }
    }

    /**
     * Slice 4 续：trace recorder 参数。
     */
    public static class Trace {

        private int maxEvents = 1000;

        public int getMaxEvents() {
            return maxEvents;
        }

        public void setMaxEvents(int maxEvents) {
            this.maxEvents = maxEvents;
        }
    }
}
