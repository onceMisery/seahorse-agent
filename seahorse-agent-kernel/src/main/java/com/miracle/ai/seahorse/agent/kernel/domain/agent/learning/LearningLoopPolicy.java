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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.learning;

import java.util.Objects;

/**
 * 自学习闭环的策略载体（纯 Java，绑定由 autoconfigure 侧完成）。
 *
 * <p>默认值一律偏保守：宁可少给建议，也不在样本不足时给噪声建议。
 * 一个错误的自动阈值调整，代价比"少调一次"高一个数量级。
 */
public final class LearningLoopPolicy {

    private static final int DEFAULT_MIN_SAMPLES = 5;
    private static final double DEFAULT_DISAPPROVAL_RATE_THRESHOLD = 0.40D;
    private static final double DEFAULT_LOOSEN_RATE_THRESHOLD = 0.10D;
    private static final int DEFAULT_MAX_PROPOSALS = 5;
    private static final double DEFAULT_CONFIDENCE_STEP = 0.05D;
    private static final double DEFAULT_EVAL_IMPROVEMENT_THRESHOLD = 0.02D;

    private final int minSamples;
    private final double disapprovalRateThreshold;
    private final double loosenRateThreshold;
    private final int maxProposals;
    private final double confidenceStep;
    private final double evalImprovementThreshold;

    private LearningLoopPolicy(Builder b) {
        this.minSamples = requirePositive(b.minSamples, "minSamples");
        this.disapprovalRateThreshold = ratio(b.disapprovalRateThreshold, "disapprovalRateThreshold");
        this.loosenRateThreshold = ratio(b.loosenRateThreshold, "loosenRateThreshold");
        if (loosenRateThreshold > disapprovalRateThreshold) {
            throw new IllegalArgumentException("loosenRateThreshold 不得高于 disapprovalRateThreshold");
        }
        this.maxProposals = requirePositive(b.maxProposals, "maxProposals");
        this.confidenceStep = ratio(b.confidenceStep, "confidenceStep");
        this.evalImprovementThreshold = ratio(b.evalImprovementThreshold, "evalImprovementThreshold");
    }

    public int minSamples() {
        return minSamples;
    }

    public double disapprovalRateThreshold() {
        return disapprovalRateThreshold;
    }

    public double loosenRateThreshold() {
        return loosenRateThreshold;
    }

    public int maxProposals() {
        return maxProposals;
    }

    public double confidenceStep() {
        return confidenceStep;
    }

    public double evalImprovementThreshold() {
        return evalImprovementThreshold;
    }

    public static LearningLoopPolicy defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必须 > 0");
        }
        return value;
    }

    private static double ratio(double value, String name) {
        if (value < 0D || value > 1D) {
            throw new IllegalArgumentException(name + " 必须落在 [0,1]");
        }
        return value;
    }

    public static final class Builder {
        private int minSamples = DEFAULT_MIN_SAMPLES;
        private double disapprovalRateThreshold = DEFAULT_DISAPPROVAL_RATE_THRESHOLD;
        private double loosenRateThreshold = DEFAULT_LOOSEN_RATE_THRESHOLD;
        private int maxProposals = DEFAULT_MAX_PROPOSALS;
        private double confidenceStep = DEFAULT_CONFIDENCE_STEP;
        private double evalImprovementThreshold = DEFAULT_EVAL_IMPROVEMENT_THRESHOLD;

        public Builder minSamples(int minSamples) {
            this.minSamples = minSamples;
            return this;
        }

        public Builder disapprovalRateThreshold(double disapprovalRateThreshold) {
            this.disapprovalRateThreshold = disapprovalRateThreshold;
            return this;
        }

        public Builder loosenRateThreshold(double loosenRateThreshold) {
            this.loosenRateThreshold = loosenRateThreshold;
            return this;
        }

        public Builder maxProposals(int maxProposals) {
            this.maxProposals = maxProposals;
            return this;
        }

        public Builder confidenceStep(double confidenceStep) {
            this.confidenceStep = confidenceStep;
            return this;
        }

        public Builder evalImprovementThreshold(double evalImprovementThreshold) {
            this.evalImprovementThreshold = evalImprovementThreshold;
            return this;
        }

        public LearningLoopPolicy build() {
            return new LearningLoopPolicy(this);
        }
    }
}
