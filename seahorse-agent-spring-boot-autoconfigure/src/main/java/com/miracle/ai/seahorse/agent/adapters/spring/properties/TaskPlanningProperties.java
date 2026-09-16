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

import com.miracle.ai.seahorse.agent.kernel.policy.TaskPlanningPolicy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 任务规划策略配置（前缀 {@code seahorse-agent.task-planning}）。
 *
 * <p>与 {@link SkillMatchProperties} 一致：字段一律允许为 {@code null}，未配置的项沿用内核默认值，
 * 默认值只在 {@link TaskPlanningPolicy#defaults()} 里定义一处。
 *
 * <p>默认关闭。开启后循环会先产出任务规划并把规划大纲注入模型上下文，属于行为变更；
 * 建议先在单个 Agent 上验证长时程任务的实际收益再放开。
 */
@ConfigurationProperties(prefix = "seahorse-agent.task-planning")
public class TaskPlanningProperties {

    /** 是否启用任务规划，默认 false。 */
    private Boolean enabled;
    /** 是否把规划大纲作为 system 消息注入模型上下文，默认 true。 */
    private Boolean includeOutlineInPrompt;
    /** 规划步数上限，默认 5。 */
    private Integer maxSteps;
    /** 步骤标题最大字符数，默认 80。 */
    private Integer maxStepTitleChars;
    /** 顺序标记词（中文），覆盖内核默认值。 */
    private List<String> sequentialMarkers;
    /** 顺序标记词（拉丁），覆盖内核默认值。 */
    private List<String> sequentialMarkersLatin;
    /** 并行标记词（中文），覆盖内核默认值。 */
    private List<String> parallelMarkers;
    /** 并行标记词（拉丁），覆盖内核默认值。 */
    private List<String> parallelMarkersLatin;
    /**
     * 规划器选择：{@code heuristic}（默认，确定性切分）或
     * {@code llm-with-heuristic-fallback}（模型改写目标后再确定性解析，失败自动回退）。
     */
    private String plannerMode;
    /** LLM 规划的等待上限，默认 30s；超时即回退到确定性切分。 */
    private Duration plannerTimeout;
    /** 自定义规划提示词模板，必须包含 {@code {goal}} 占位符。 */
    private String plannerPrompt;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getIncludeOutlineInPrompt() {
        return includeOutlineInPrompt;
    }

    public void setIncludeOutlineInPrompt(Boolean includeOutlineInPrompt) {
        this.includeOutlineInPrompt = includeOutlineInPrompt;
    }

    public Integer getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(Integer maxSteps) {
        this.maxSteps = maxSteps;
    }

    public Integer getMaxStepTitleChars() {
        return maxStepTitleChars;
    }

    public void setMaxStepTitleChars(Integer maxStepTitleChars) {
        this.maxStepTitleChars = maxStepTitleChars;
    }

    public List<String> getSequentialMarkers() {
        return sequentialMarkers;
    }

    public void setSequentialMarkers(List<String> sequentialMarkers) {
        this.sequentialMarkers = sequentialMarkers;
    }

    public List<String> getSequentialMarkersLatin() {
        return sequentialMarkersLatin;
    }

    public void setSequentialMarkersLatin(List<String> sequentialMarkersLatin) {
        this.sequentialMarkersLatin = sequentialMarkersLatin;
    }

    public List<String> getParallelMarkers() {
        return parallelMarkers;
    }

    public void setParallelMarkers(List<String> parallelMarkers) {
        this.parallelMarkers = parallelMarkers;
    }

    public List<String> getParallelMarkersLatin() {
        return parallelMarkersLatin;
    }

    public void setParallelMarkersLatin(List<String> parallelMarkersLatin) {
        this.parallelMarkersLatin = parallelMarkersLatin;
    }

    public String getPlannerMode() {
        return plannerMode;
    }

    public void setPlannerMode(String plannerMode) {
        this.plannerMode = plannerMode;
    }

    public Duration getPlannerTimeout() {
        return plannerTimeout;
    }

    public void setPlannerTimeout(Duration plannerTimeout) {
        this.plannerTimeout = plannerTimeout;
    }

    public String getPlannerPrompt() {
        return plannerPrompt;
    }

    public void setPlannerPrompt(String plannerPrompt) {
        this.plannerPrompt = plannerPrompt;
    }

    public TaskPlanningPolicy toPolicy() {
        TaskPlanningPolicy.Builder builder = TaskPlanningPolicy.builder();
        if (enabled != null) {
            builder.enabled(enabled);
        }
        if (includeOutlineInPrompt != null) {
            builder.includeOutlineInPrompt(includeOutlineInPrompt);
        }
        if (maxSteps != null) {
            builder.maxSteps(maxSteps);
        }
        if (maxStepTitleChars != null) {
            builder.maxStepTitleChars(maxStepTitleChars);
        }
        if (sequentialMarkers != null) {
            builder.sequentialMarkers(sequentialMarkers);
        }
        if (sequentialMarkersLatin != null) {
            builder.sequentialMarkersLatin(sequentialMarkersLatin);
        }
        if (parallelMarkers != null) {
            builder.parallelMarkers(parallelMarkers);
        }
        if (parallelMarkersLatin != null) {
            builder.parallelMarkersLatin(parallelMarkersLatin);
        }
        if (plannerMode != null) {
            builder.plannerMode(parsePlannerMode(plannerMode));
        }
        if (plannerTimeout != null) {
            builder.plannerTimeout(plannerTimeout);
        }
        if (plannerPrompt != null) {
            builder.plannerPrompt(plannerPrompt);
        }
        return builder.build();
    }

    /**
     * 兼容 kebab-case（{@code llm-with-heuristic-fallback}）、枚举原名与大小写混写。
     * 非法值直接抛错——规划器选错只会静默降低规划质量，不如在启动时暴露。
     */
    private static TaskPlanningPolicy.PlannerMode parsePlannerMode(String raw) {
        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return TaskPlanningPolicy.PlannerMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "seahorse-agent.task-planning.planner-mode 非法：" + raw
                            + "，可选值：" + Arrays.toString(TaskPlanningPolicy.PlannerMode.values()), ex);
        }
    }
}
