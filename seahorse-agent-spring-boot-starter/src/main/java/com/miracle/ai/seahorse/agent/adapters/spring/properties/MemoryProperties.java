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
     * 占位：outbox relay batch / handler 配置，迁移待后续切片。
     */
    public static class Outbox {
    }

    /**
     * 占位：maintenance / compaction / gc / alias 等参数，迁移待后续切片。
     */
    public static class Maintenance {
    }

    /**
     * 占位：refiner fail-open / batch 限额、sticky anchor 等参数，迁移待后续切片。
     */
    public static class Refiner {
    }
}
