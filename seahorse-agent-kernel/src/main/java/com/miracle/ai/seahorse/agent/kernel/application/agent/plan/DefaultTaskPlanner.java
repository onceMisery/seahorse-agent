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

package com.miracle.ai.seahorse.agent.kernel.application.agent.plan;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.PlanStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlan;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlanningRequest;
import com.miracle.ai.seahorse.agent.kernel.policy.TaskPlanningPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.TaskPlannerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 确定性的默认任务规划器：按标记词把目标切分为有序步骤，不调用模型。
 *
 * <p>切分优先级：
 * <ol>
 *   <li>显式编号（{@code 1. / 2、/ 3)}）——用户已经把步骤写出来了，直接沿用；</li>
 *   <li>顺序标记（首先 / 然后 / 接着 / 最后 ...）——按出现位置切分并丢弃标记词本身；</li>
 *   <li>强分隔符（{@code ；; 换行}）——退而求其次；</li>
 *   <li>以上都不命中，则视为不可分解，返回单步规划。</li>
 * </ol>
 *
 * <p>依赖推导：默认串行，第 {@code i} 步依赖第 {@code i-1} 步；若某段命中并行标记
 * （同时 / 与此同时 / 并行 ...），则继承前一步的依赖，表示它与前一步之间没有先后约束。
 *
 * <p>这是一条刻意保守的基线：它不试图理解语义，只把"用户已经写在目标里的结构"提取出来。
 * 真正的语义分解应由后续接入的 LLM 规划器承担，届时本实现降级为兜底。
 */
public final class DefaultTaskPlanner implements TaskPlannerPort {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTaskPlanner.class);

    public static final String STRATEGY = "heuristic-decomposition";

    // 编号分隔符后不允许紧跟数字，否则 "Redis 7.0 与 6.2" 会被当成三条步骤切开。
    // 同时支持中文序号 "第一步/第二步/第三步"——这在中文任务描述里和 "1. 2. 3." 一样常见。
    private static final Pattern ENUMERATION_DELIMITER =
            Pattern.compile("(?:^|[\\s;；。！？!?，,])(?:\\d{1,2}[.、)]|第[一二三四五六七八九十\\d]{1,3}步)(?!\\d)");
    private static final Pattern STRONG_SEPARATOR = Pattern.compile("[；;\\n]+");
    private static final String TAIL_JOINER = "；";

    private final TaskPlanningPolicy policy;

    public DefaultTaskPlanner() {
        this(TaskPlanningPolicy.defaults());
    }

    public DefaultTaskPlanner(TaskPlanningPolicy policy) {
        this.policy = Objects.requireNonNullElseGet(policy, TaskPlanningPolicy::defaults);
    }

    @Override
    public TaskPlan plan(TaskPlanningRequest request) {
        TaskPlanningRequest safe = request == null
                ? TaskPlanningRequest.of("", null, 0)
                : request;
        String goal = safe.goal() == null ? "" : safe.goal().trim();
        if (goal.isEmpty()) {
            return TaskPlan.empty(goal, STRATEGY);
        }
        List<String> segments = split(goal);
        if (segments.size() <= 1) {
            LOG.debug("Task goal is not decomposable; using a single step: runId={}", safe.runId());
            return TaskPlan.singleStep(goal, STRATEGY);
        }
        int maxSteps = effectiveMaxSteps(safe);
        boolean capped = segments.size() > maxSteps;
        List<String> bounded = capped ? mergeTail(segments, maxSteps) : segments;
        TaskPlan plan = TaskPlan.of(goal, STRATEGY, buildSteps(bounded));
        LOG.debug("Task goal decomposed: runId={}, segments={}, capped={}, maxSteps={}",
                safe.runId(), bounded.size(), capped, maxSteps);
        return plan;
    }

    private int effectiveMaxSteps(TaskPlanningRequest request) {
        int limit = policy.maxSteps();
        if (request.maxSteps() > 0) {
            limit = Math.min(limit, request.maxSteps());
        }
        return Math.max(1, limit);
    }

    private List<String> split(String goal) {
        List<String> segments = nonBlank(splitBy(ENUMERATION_DELIMITER, goal));
        if (segments.size() > 1) {
            return segments;
        }
        Pattern sequential = markersPattern(policy.sequentialMarkers(), policy.sequentialMarkersLatin());
        segments = nonBlank(splitBy(sequential, goal));
        if (segments.size() > 1) {
            return segments;
        }
        return nonBlank(splitBy(STRONG_SEPARATOR, goal));
    }

    private static List<String> splitBy(Pattern delimiter, String goal) {
        List<String> parts = new ArrayList<>();
        for (String part : delimiter.split(goal)) {
            parts.add(part);
        }
        return parts;
    }

    private static List<String> nonBlank(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String trimmed = trimEdges(value);
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 去掉段首段尾的标点与空白，让标题更干净；不影响步骤正文的语义。
     */
    private static String trimEdges(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        int start = 0;
        int end = trimmed.length();
        while (start < end && isSkippable(trimmed.charAt(start))) {
            start++;
        }
        while (end > start && isSkippable(trimmed.charAt(end - 1))) {
            end--;
        }
        return trimmed.substring(start, end).trim();
    }

    private static boolean isSkippable(char c) {
        return c == '，' || c == ',' || c == '。' || c == '；' || c == ';' || c == '、' || c == '：' || c == ':';
    }

    /**
     * 超出步数上限时把多余的尾部合并进最后一步，而不是直接丢弃——
     * 截断规划会让目标的一部分凭空消失，比"最后一步变粗"危险得多。
     */
    private static List<String> mergeTail(List<String> segments, int maxSteps) {
        if (maxSteps <= 0 || segments.size() <= maxSteps) {
            return segments;
        }
        List<String> result = new ArrayList<>(segments.subList(0, maxSteps - 1));
        StringBuilder tail = new StringBuilder();
        for (int i = maxSteps - 1; i < segments.size(); i++) {
            if (tail.length() > 0) {
                tail.append(TAIL_JOINER);
            }
            tail.append(segments.get(i));
        }
        result.add(tail.toString());
        return result;
    }

    private List<PlanStep> buildSteps(List<String> segments) {
        Pattern parallel = markersPattern(policy.parallelMarkers(), policy.parallelMarkersLatin());
        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            String title = truncate(segment, policy.maxStepTitleChars());
            List<String> dependsOn;
            if (i == 0) {
                dependsOn = List.of();
            } else if (parallel.matcher(segment).find()) {
                dependsOn = steps.get(i - 1).dependsOn();
            } else {
                dependsOn = List.of(steps.get(i - 1).id());
            }
            steps.add(PlanStep.pending(i, title, segment, dependsOn));
        }
        return List.copyOf(steps);
    }

    private static Pattern markersPattern(List<String> markers, List<String> markersLatin) {
        List<String> all = new ArrayList<>(Objects.requireNonNullElse(markers, List.of()));
        all.addAll(Objects.requireNonNullElse(markersLatin, List.of()));
        all.removeIf(Objects::isNull);
        if (all.isEmpty()) {
            // 空词表时用不可能匹配的模式，保证 split 行为稳定（返回原串）
            return Pattern.compile("(?!)");
        }
        all.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) {
                pattern.append('|');
            }
            pattern.append(Pattern.quote(all.get(i)));
        }
        return Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static String truncate(String value, int maxChars) {
        String safe = Objects.requireNonNullElse(value, "").trim();
        return safe.length() <= maxChars ? safe : safe.substring(0, maxChars) + "...";
    }
}
