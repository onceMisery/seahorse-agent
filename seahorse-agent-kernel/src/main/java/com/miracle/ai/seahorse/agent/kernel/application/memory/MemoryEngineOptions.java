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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

/**
 * 默认记忆引擎的可配置策略。
 *
 * <p>该对象只表达内核可理解的策略值，Spring 属性解析留在 starter 自动配置中，保持 kernel
 * 对外部框架无感知。</p>
 */
public record MemoryEngineOptions(int shortTermLimit,
                                  int longTermLimit,
                                  int semanticLimit,
                                  boolean captureEnabled,
                                  boolean refinerEnabled,
                                  boolean refinerFailOpen,
                                  boolean keywordIndexOutboxEnabled,
                                  boolean graphIndexOutboxEnabled,
                                  int maxRefinerBatchOperations,
                                  double maxRefinerDeleteRatio,
                                  int refinerReadMaskPerLayerLimit,
                                  int refinerTargetZoneTurnCount,
                                  int refinerStickyAnchorLimit,
                                  int refinerFeedbackExampleLimit,
                                  double refinerStickyAnchorImportanceThreshold,
                                  double refinerStickyAnchorConfidenceThreshold) {

    public static final int DEFAULT_SHORT_TERM_LIMIT = 5;
    public static final int DEFAULT_LONG_TERM_LIMIT = 3;
    public static final int DEFAULT_SEMANTIC_LIMIT = 10;
    public static final int DEFAULT_MAX_REFINER_BATCH_OPERATIONS = 8;
    public static final double DEFAULT_MAX_REFINER_DELETE_RATIO = 0.7D;
    public static final int DEFAULT_REFINER_READ_MASK_PER_LAYER_LIMIT = 3;
    public static final int DEFAULT_REFINER_TARGET_ZONE_TURN_COUNT = 3;
    public static final int DEFAULT_REFINER_STICKY_ANCHOR_LIMIT = 5;
    public static final int DEFAULT_REFINER_FEEDBACK_EXAMPLE_LIMIT = 3;
    public static final double DEFAULT_REFINER_STICKY_ANCHOR_IMPORTANCE_THRESHOLD = 0.85D;
    public static final double DEFAULT_REFINER_STICKY_ANCHOR_CONFIDENCE_THRESHOLD = 0.90D;

    public MemoryEngineOptions {
        shortTermLimit = positive(shortTermLimit, DEFAULT_SHORT_TERM_LIMIT);
        longTermLimit = positive(longTermLimit, DEFAULT_LONG_TERM_LIMIT);
        semanticLimit = positive(semanticLimit, DEFAULT_SEMANTIC_LIMIT);
        maxRefinerBatchOperations = positive(maxRefinerBatchOperations, DEFAULT_MAX_REFINER_BATCH_OPERATIONS);
        maxRefinerDeleteRatio = maxRefinerDeleteRatio <= 0D
                ? DEFAULT_MAX_REFINER_DELETE_RATIO
                : Math.min(1D, maxRefinerDeleteRatio);
        refinerReadMaskPerLayerLimit = positive(refinerReadMaskPerLayerLimit,
                DEFAULT_REFINER_READ_MASK_PER_LAYER_LIMIT);
        refinerTargetZoneTurnCount = positive(refinerTargetZoneTurnCount, DEFAULT_REFINER_TARGET_ZONE_TURN_COUNT);
        refinerStickyAnchorLimit = positive(refinerStickyAnchorLimit, DEFAULT_REFINER_STICKY_ANCHOR_LIMIT);
        refinerFeedbackExampleLimit = positive(refinerFeedbackExampleLimit, DEFAULT_REFINER_FEEDBACK_EXAMPLE_LIMIT);
        refinerStickyAnchorImportanceThreshold = ratio(refinerStickyAnchorImportanceThreshold,
                DEFAULT_REFINER_STICKY_ANCHOR_IMPORTANCE_THRESHOLD);
        refinerStickyAnchorConfidenceThreshold = ratio(refinerStickyAnchorConfidenceThreshold,
                DEFAULT_REFINER_STICKY_ANCHOR_CONFIDENCE_THRESHOLD);
    }

    public MemoryEngineOptions(int shortTermLimit,
                               int longTermLimit,
                               int semanticLimit,
                               boolean captureEnabled,
                               boolean refinerEnabled,
                               boolean refinerFailOpen,
                               boolean keywordIndexOutboxEnabled,
                               boolean graphIndexOutboxEnabled,
                               int maxRefinerBatchOperations,
                               double maxRefinerDeleteRatio) {
        this(shortTermLimit,
                longTermLimit,
                semanticLimit,
                captureEnabled,
                refinerEnabled,
                refinerFailOpen,
                keywordIndexOutboxEnabled,
                graphIndexOutboxEnabled,
                maxRefinerBatchOperations,
                maxRefinerDeleteRatio,
                DEFAULT_REFINER_READ_MASK_PER_LAYER_LIMIT,
                DEFAULT_REFINER_TARGET_ZONE_TURN_COUNT,
                DEFAULT_REFINER_STICKY_ANCHOR_LIMIT,
                DEFAULT_REFINER_FEEDBACK_EXAMPLE_LIMIT,
                DEFAULT_REFINER_STICKY_ANCHOR_IMPORTANCE_THRESHOLD,
                DEFAULT_REFINER_STICKY_ANCHOR_CONFIDENCE_THRESHOLD);
    }

    public MemoryEngineOptions(int shortTermLimit,
                               int longTermLimit,
                               int semanticLimit,
                               boolean captureEnabled,
                               boolean refinerEnabled,
                               boolean refinerFailOpen,
                               boolean keywordIndexOutboxEnabled,
                               boolean graphIndexOutboxEnabled) {
        this(shortTermLimit,
                longTermLimit,
                semanticLimit,
                captureEnabled,
                refinerEnabled,
                refinerFailOpen,
                keywordIndexOutboxEnabled,
                graphIndexOutboxEnabled,
                DEFAULT_MAX_REFINER_BATCH_OPERATIONS,
                DEFAULT_MAX_REFINER_DELETE_RATIO);
    }

    public MemoryEngineOptions(int shortTermLimit,
                               int longTermLimit,
                               int semanticLimit,
                               boolean captureEnabled,
                               boolean refinerEnabled,
                               boolean refinerFailOpen) {
        this(shortTermLimit, longTermLimit, semanticLimit, captureEnabled, refinerEnabled, refinerFailOpen,
                false, false);
    }

    public MemoryEngineOptions(int shortTermLimit,
                               int longTermLimit,
                               int semanticLimit,
                               boolean captureEnabled) {
        this(shortTermLimit, longTermLimit, semanticLimit, captureEnabled, false, true, false, false);
    }

    public static MemoryEngineOptions defaults() {
        return new MemoryEngineOptions(
                DEFAULT_SHORT_TERM_LIMIT,
                DEFAULT_LONG_TERM_LIMIT,
                DEFAULT_SEMANTIC_LIMIT,
                true,
                false,
                true,
                false,
                false);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double ratio(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0D) {
            return fallback;
        }
        return Math.min(1D, value);
    }
}
