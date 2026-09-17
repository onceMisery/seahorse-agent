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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopPolicy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 自学习闭环策略配置（前缀 {@code seahorse-agent.learning-loop}）。
 *
 * <p>字段一律允许为 {@code null}，未配置的项沿用内核默认值；默认值只在
 * {@link LearningLoopPolicy#defaults()} 里定义一处。
 */
@ConfigurationProperties(prefix = "seahorse-agent.learning-loop")
public class LearningLoopProperties {

    /** 触发建议所需的最小样本量，默认 5。 */
    private Integer minSamples;
    /** 不认可率超过该值即认为需要收紧，默认 0.4。 */
    private Double disapprovalRateThreshold;
    /** 不认可率低于该值才考虑放宽，默认 0.1。 */
    private Double loosenRateThreshold;
    /** 单次运行最多产出多少条建议，默认 5。 */
    private Integer maxProposals;
    /** 阈值建议的单步调整量，默认 0.05。 */
    private Double confidenceStep;
    /** 判定"改进/回归"的通过率变化阈值，默认 0.02。 */
    private Double evalImprovementThreshold;
    /** 同一张已落地工单两次回访之间的最小间隔，默认 12h。 */
    private java.time.Duration reviewCooldown;
    /**
     * 回访定时任务的 cron。<b>留空即不装配定时任务</b>——回访默认由"跑一次闭环"顺带触发
     * （事件驱动），定时只是给"很久没人点运行闭环"的兜底。
     */
    private String reviewCron;
    /** 定时任务回访哪个租户，默认 default。 */
    private String reviewTenantId;
    /**
     * 定时回访取"当前通过率"所用的评测数据集。
     *
     * <p>留空则默认测量源判定为"不可测量"，回访会被跳过而不是写出无信息量的记录。
     * 想让定时回访真的能得出结论，这里与 {@link #reviewModelId} 都要配。
     */
    private String reviewDatasetId;
    /** 定时回访跑评测所用的模型。留空同上。 */
    private String reviewModelId;
    /**
     * 定时回访的基线通过率。
     *
     * <p>留空时默认测量源会尝试用它拿不到的其他来源；都没有就判定不可测量——
     * 没有基线的"当前通过率"无法判断到底是改进还是回归。
     */
    private Double reviewBaselinePassRate;

    public Integer getMinSamples() {
        return minSamples;
    }

    public void setMinSamples(Integer minSamples) {
        this.minSamples = minSamples;
    }

    public Double getDisapprovalRateThreshold() {
        return disapprovalRateThreshold;
    }

    public void setDisapprovalRateThreshold(Double disapprovalRateThreshold) {
        this.disapprovalRateThreshold = disapprovalRateThreshold;
    }

    public Double getLoosenRateThreshold() {
        return loosenRateThreshold;
    }

    public void setLoosenRateThreshold(Double loosenRateThreshold) {
        this.loosenRateThreshold = loosenRateThreshold;
    }

    public Integer getMaxProposals() {
        return maxProposals;
    }

    public void setMaxProposals(Integer maxProposals) {
        this.maxProposals = maxProposals;
    }

    public Double getConfidenceStep() {
        return confidenceStep;
    }

    public void setConfidenceStep(Double confidenceStep) {
        this.confidenceStep = confidenceStep;
    }

    public Double getEvalImprovementThreshold() {
        return evalImprovementThreshold;
    }

    public void setEvalImprovementThreshold(Double evalImprovementThreshold) {
        this.evalImprovementThreshold = evalImprovementThreshold;
    }

    public java.time.Duration getReviewCooldown() {
        return reviewCooldown;
    }

    public void setReviewCooldown(java.time.Duration reviewCooldown) {
        this.reviewCooldown = reviewCooldown;
    }

    public String getReviewCron() {
        return reviewCron;
    }

    public void setReviewCron(String reviewCron) {
        this.reviewCron = reviewCron;
    }

    public String getReviewTenantId() {
        return reviewTenantId;
    }

    public void setReviewTenantId(String reviewTenantId) {
        this.reviewTenantId = reviewTenantId;
    }

    public String getReviewDatasetId() {
        return reviewDatasetId;
    }

    public void setReviewDatasetId(String reviewDatasetId) {
        this.reviewDatasetId = reviewDatasetId;
    }

    public String getReviewModelId() {
        return reviewModelId;
    }

    public void setReviewModelId(String reviewModelId) {
        this.reviewModelId = reviewModelId;
    }

    public Double getReviewBaselinePassRate() {
        return reviewBaselinePassRate;
    }

    public void setReviewBaselinePassRate(Double reviewBaselinePassRate) {
        this.reviewBaselinePassRate = reviewBaselinePassRate;
    }

    public LearningLoopPolicy toPolicy() {
        LearningLoopPolicy.Builder builder = LearningLoopPolicy.builder();
        if (minSamples != null) {
            builder.minSamples(minSamples);
        }
        if (disapprovalRateThreshold != null) {
            builder.disapprovalRateThreshold(disapprovalRateThreshold);
        }
        if (loosenRateThreshold != null) {
            builder.loosenRateThreshold(loosenRateThreshold);
        }
        if (maxProposals != null) {
            builder.maxProposals(maxProposals);
        }
        if (confidenceStep != null) {
            builder.confidenceStep(confidenceStep);
        }
        if (evalImprovementThreshold != null) {
            builder.evalImprovementThreshold(evalImprovementThreshold);
        }
        if (reviewCooldown != null) {
            builder.reviewCooldown(reviewCooldown);
        }
        return builder.build();
    }
}
