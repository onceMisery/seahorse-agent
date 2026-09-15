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

package com.miracle.ai.seahorse.agent.kernel.policy;

import java.util.List;
import java.util.Objects;

/**
 * 任务规划策略：是否启用规划、步数上限、以及从目标文本里切分步骤时使用的标记词。
 *
 * <p>与 {@link SkillMatchPolicy} / {@link MemoryRoutingPolicy} 一样是纯 Java 载体：
 * 内核不依赖任何配置框架，绑定由 autoconfigure 侧完成。
 *
 * <p><b>默认关闭</b>（{@code enabled = false}）。规划会往模型上下文里注入一条 system 消息，
 * 属于行为变更；在拿到长时程任务的实际效果证据之前，默认路径必须保持与引入前完全一致。
 *
 * <p>中文标记按原文匹配，拉丁标记按小写后的文本匹配，因此两组分开存放。
 */
public final class TaskPlanningPolicy {

    // 只收多字标记：单字的"先""再"会命中"优先""再来"等普通用词，误切代价高于收益。
    // "先…然后…最后"这类目标仍能被正确切分——前置的"先"只是留在第一段里，不影响步骤数。
    private static final List<String> DEFAULT_SEQUENTIAL_MARKERS =
            List.of("首先", "第一步", "然后", "接着", "其次", "随后", "之后", "最后", "最终");
    private static final List<String> DEFAULT_SEQUENTIAL_MARKERS_LATIN =
            List.of("first", "firstly", "then", "next", "after that", "afterwards", "finally", "lastly");

    private static final List<String> DEFAULT_PARALLEL_MARKERS =
            List.of("同时", "与此同时", "并行", "另外");
    private static final List<String> DEFAULT_PARALLEL_MARKERS_LATIN =
            List.of("meanwhile", "in parallel", "at the same time");

    private final boolean enabled;
    private final boolean includeOutlineInPrompt;
    private final int maxSteps;
    private final int maxStepTitleChars;
    private final List<String> sequentialMarkers;
    private final List<String> sequentialMarkersLatin;
    private final List<String> parallelMarkers;
    private final List<String> parallelMarkersLatin;

    private TaskPlanningPolicy(Builder b) {
        this.enabled = b.enabled;
        this.includeOutlineInPrompt = b.includeOutlineInPrompt;
        this.maxSteps = requirePositive(b.maxSteps, "maxSteps");
        this.maxStepTitleChars = requirePositive(b.maxStepTitleChars, "maxStepTitleChars");
        this.sequentialMarkers = copyOf(b.sequentialMarkers, "sequentialMarkers");
        this.sequentialMarkersLatin = copyOf(b.sequentialMarkersLatin, "sequentialMarkersLatin");
        this.parallelMarkers = copyOf(b.parallelMarkers, "parallelMarkers");
        this.parallelMarkersLatin = copyOf(b.parallelMarkersLatin, "parallelMarkersLatin");
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean includeOutlineInPrompt() {
        return includeOutlineInPrompt;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public int maxStepTitleChars() {
        return maxStepTitleChars;
    }

    public List<String> sequentialMarkers() {
        return sequentialMarkers;
    }

    public List<String> sequentialMarkersLatin() {
        return sequentialMarkersLatin;
    }

    public List<String> parallelMarkers() {
        return parallelMarkers;
    }

    public List<String> parallelMarkersLatin() {
        return parallelMarkersLatin;
    }

    public static TaskPlanningPolicy defaults() {
        return builder().build();
    }

    public static TaskPlanningPolicy disabled() {
        return builder().enabled(false).build();
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

    private static List<String> copyOf(List<String> values, String name) {
        return List.copyOf(Objects.requireNonNull(values, name + " must not be null"));
    }

    public static final class Builder {
        private boolean enabled = false;
        private boolean includeOutlineInPrompt = true;
        private int maxSteps = 5;
        private int maxStepTitleChars = 80;
        private List<String> sequentialMarkers = DEFAULT_SEQUENTIAL_MARKERS;
        private List<String> sequentialMarkersLatin = DEFAULT_SEQUENTIAL_MARKERS_LATIN;
        private List<String> parallelMarkers = DEFAULT_PARALLEL_MARKERS;
        private List<String> parallelMarkersLatin = DEFAULT_PARALLEL_MARKERS_LATIN;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder includeOutlineInPrompt(boolean includeOutlineInPrompt) {
            this.includeOutlineInPrompt = includeOutlineInPrompt;
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder maxStepTitleChars(int maxStepTitleChars) {
            this.maxStepTitleChars = maxStepTitleChars;
            return this;
        }

        public Builder sequentialMarkers(List<String> sequentialMarkers) {
            this.sequentialMarkers = sequentialMarkers;
            return this;
        }

        public Builder sequentialMarkersLatin(List<String> sequentialMarkersLatin) {
            this.sequentialMarkersLatin = sequentialMarkersLatin;
            return this;
        }

        public Builder parallelMarkers(List<String> parallelMarkers) {
            this.parallelMarkers = parallelMarkers;
            return this;
        }

        public Builder parallelMarkersLatin(List<String> parallelMarkersLatin) {
            this.parallelMarkersLatin = parallelMarkersLatin;
            return this;
        }

        public TaskPlanningPolicy build() {
            return new TaskPlanningPolicy(this);
        }
    }
}
