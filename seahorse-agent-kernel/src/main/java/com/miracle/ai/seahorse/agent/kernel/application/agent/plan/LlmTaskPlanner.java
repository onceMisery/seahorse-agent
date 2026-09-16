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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlan;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlanningRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.policy.TaskPlanningPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.TaskPlannerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * LLM 任务规划器：让模型把目标改写成编号步骤，再用确定性切分解析模型输出。
 *
 * <p>设计要点是<b>不让模型输出直接成为规划</b>：模型只负责"改写"，解析仍然走
 * {@link DefaultTaskPlanner} 那套确定性规则。这样自由文本、多余解释、markdown 装饰
 * 都不会污染步骤结构，而模型输出格式漂移时也只影响切分质量，不会产生非法规划。
 *
 * <p>失败一律回退：模型不可用、超时、输出为空、或解析不出多步时，都委托给
 * {@code fallback}（默认 {@link DefaultTaskPlanner}）对<b>原始目标</b>重新规划。
 * 规划是可选能力，绝不能因为它让任务失败。
 */
public final class LlmTaskPlanner implements TaskPlannerPort {

    private static final Logger LOG = LoggerFactory.getLogger(LlmTaskPlanner.class);

    public static final String STRATEGY = "llm-decomposition";

    private static final double PLANNING_TEMPERATURE = 0.0D;

    private final StreamingChatModelPort modelPort;
    private final TaskPlanningPolicy policy;
    private final TaskPlannerPort fallback;

    public LlmTaskPlanner(StreamingChatModelPort modelPort, TaskPlanningPolicy policy) {
        this(modelPort, policy, null);
    }

    public LlmTaskPlanner(StreamingChatModelPort modelPort,
                          TaskPlanningPolicy policy,
                          TaskPlannerPort fallback) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        this.policy = Objects.requireNonNullElseGet(policy, TaskPlanningPolicy::defaults);
        this.fallback = fallback != null ? fallback : new DefaultTaskPlanner(this.policy);
    }

    @Override
    public TaskPlan plan(TaskPlanningRequest request) {
        TaskPlanningRequest safe = request == null
                ? TaskPlanningRequest.of("", null, 0)
                : request;
        String goal = safe.goal() == null ? "" : safe.goal().trim();
        if (goal.isEmpty()) {
            return fallback.plan(safe);
        }
        int maxSteps = safe.maxSteps() > 0 ? safe.maxSteps() : policy.maxSteps();
        String modelOutput;
        try {
            modelOutput = requestDecomposition(goal, maxSteps, safe.modelId());
        } catch (RuntimeException ex) {
            LOG.warn("LLM task planning failed; falling back to heuristic decomposition: runId={}",
                    safe.runId(), ex);
            return fallback.plan(safe);
        }
        if (modelOutput.isBlank()) {
            LOG.debug("LLM planner returned no text; falling back: runId={}", safe.runId());
            return fallback.plan(safe);
        }
        TaskPlan parsed = new DefaultTaskPlanner(policy)
                .plan(TaskPlanningRequest.of(modelOutput, safe.runId(), maxSteps));
        if (parsed.stepCount() <= 1) {
            LOG.debug("LLM planner output was not decomposable ({} chars); falling back: runId={}",
                    modelOutput.length(), safe.runId());
            return fallback.plan(safe);
        }
        return new TaskPlan(
                TaskPlan.newPlanId(),
                goal,
                STRATEGY,
                parsed.steps(),
                Instant.now());
    }

    private String requestDecomposition(String goal, int maxSteps, String modelId) {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(policy.renderPlannerPrompt(goal, maxSteps)),
                        ChatMessage.user(goal)))
                .modelId(modelId)
                .samplingOptions(ChatSamplingOptions.builder()
                        .temperature(PLANNING_TEMPERATURE)
                        .build())
                .tools(List.of())
                .enableTools(Boolean.FALSE)
                .build();
        Collector collector = new Collector();
        StreamCancellationHandle handle = modelPort.streamChat(request, collector);
        if (!collector.await(policy.plannerTimeout())) {
            cancelQuietly(handle);
            throw new IllegalStateException("LLM planner timed out after " + policy.plannerTimeout());
        }
        Throwable error = collector.error();
        if (error != null) {
            throw error instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("LLM planner failed", error);
        }
        return collector.content();
    }

    private static void cancelQuietly(StreamCancellationHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.cancel();
        } catch (RuntimeException ignored) {
            // 取消失败不改变规划语义：超时后本就会回退到确定性规划。
        }
    }

    /**
     * 把流式回调收敛成一次阻塞读取。规划发生在循环开始之前，还没有流式通道可用，
     * 因此这里必须等模型说完再解析。
     */
    private static final class Collector implements StreamCallback {

        private final StringBuilder content = new StringBuilder();
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile Throwable error;

        @Override
        public void onContent(String chunk) {
            if (chunk != null) {
                content.append(chunk);
            }
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            done.countDown();
        }

        private String content() {
            return content.toString();
        }

        private Throwable error() {
            return error;
        }

        private boolean await(java.time.Duration timeout) {
            try {
                return done.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("LLM planner interrupted", ex);
            }
        }
    }
}
