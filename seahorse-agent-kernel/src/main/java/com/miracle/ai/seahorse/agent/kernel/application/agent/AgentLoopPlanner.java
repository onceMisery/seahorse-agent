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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.application.agent.plan.DefaultTaskPlanner;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.PlanStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlan;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlanningRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.policy.TaskPlanningPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.TaskPlannerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * 主循环的任务规划协作类（P3-1）。
 *
 * <p>与 {@link AgentLoopModelTurns}、{@link AgentLoopToolExecutor} 同构：把"规划"这块横切关注点
 * 从 {@code KernelAgentLoop} 里搬出来，避免主循环继续膨胀（它有单构造函数与 700 行的架构约束）。
 *
 * <p>三条硬性行为约定：
 * <ol>
 *   <li>策略关闭时完全不介入——不建 trace 节点、不记录运行步骤、不注入任何消息；</li>
 *   <li>规划失败（抛异常）或规划为空时退化为"没有规划"，绝不影响任务本身；</li>
 *   <li>所有记账动作（trace / 运行步骤）自身再出异常时只记日志，不改变循环语义。</li>
 * </ol>
 */
class AgentLoopPlanner {

    private static final Logger LOG = LoggerFactory.getLogger(AgentLoopPlanner.class);

    private static final String TRACE_TYPE_AGENT_PLAN = "AGENT_PLAN";
    private static final String TRACE_CLASS_NAME =
            "com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop";

    private final TaskPlanningPolicy policy;
    private final TaskPlannerPort planner;
    private final KernelRagTraceRecorder traceRecorder;
    private final AgentRunStepRecorder runStepRecorder;

    AgentLoopPlanner(TaskPlanningPolicy policy,
                     KernelRagTraceRecorder traceRecorder,
                     AgentRunStepRecorder runStepRecorder) {
        this(policy, traceRecorder, runStepRecorder, null);
    }

    /**
     * @param planner 显式指定规划器，为 {@code null} 时使用内核默认的确定性规划器。
     *                仅测试与后续接入 LLM 规划器时使用。
     */
    AgentLoopPlanner(TaskPlanningPolicy policy,
                     KernelRagTraceRecorder traceRecorder,
                     AgentRunStepRecorder runStepRecorder,
                     TaskPlannerPort planner) {
        this.policy = Objects.requireNonNullElseGet(policy, TaskPlanningPolicy::defaults);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        this.runStepRecorder = Objects.requireNonNullElseGet(runStepRecorder, AgentRunStepRecorder::noop);
        this.planner = planner != null ? planner : new DefaultTaskPlanner(this.policy);
    }

    /**
     * 生成任务规划；未启用或失败时返回 {@code null}。
     */
    TaskPlan planRun(AgentLoopRequest request, TraceRunScope traceRunScope, int maxSteps) {
        if (!policy.enabled()) {
            return null;
        }
        TraceNodeScope planScope = traceRecorder.startNode(traceRunScope, planCommand());
        try {
            TaskPlan plan = planner.plan(new TaskPlanningRequest(
                    request.question(),
                    request.runId(),
                    request.agentId(),
                    request.tenantId(),
                    request.userId(),
                    maxSteps));
            if (plan == null || plan.stepCount() == 0) {
                traceRecorder.finishNode(planScope);
                return null;
            }
            traceRecorder.finishNode(planScope, null, plan.toJson());
            recordQuietly(request, plan, null);
            LOG.info("Task plan created: runId={}, planId={}, strategy={}, stepCount={}",
                    request.runId(), plan.planId(), plan.strategy(), plan.stepCount());
            return plan;
        } catch (RuntimeException ex) {
            traceRecorder.finishNode(planScope, ex);
            recordQuietly(request, null, ex);
            LOG.warn("Task planning failed; continuing without a plan: runId={}", request.runId(), ex);
            return null;
        }
    }

    /**
     * 把规划大纲注入模型上下文。未启用、无规划或配置为不注入时不做任何事。
     */
    void appendPlan(TaskPlan plan, List<ChatMessage> messages) {
        if (!policy.enabled() || plan == null || !policy.includeOutlineInPrompt()) {
            return;
        }
        String outline = plan.outline();
        if (!outline.isEmpty()) {
            messages.add(ChatMessage.system(outline));
        }
    }

    TaskPlan startStep(TaskPlan plan, AgentLoopRequest request, int step) {
        return advance(plan, request, step, PlanStepStatus.IN_PROGRESS);
    }

    TaskPlan completeStep(TaskPlan plan, AgentLoopRequest request, int step) {
        return advance(plan, request, step, PlanStepStatus.COMPLETED);
    }

    TaskPlan failStep(TaskPlan plan, AgentLoopRequest request, int step) {
        return advance(plan, request, step, PlanStepStatus.FAILED);
    }

    /**
     * 把循环的第 {@code step} 轮映射到规划的第 {@code step} 个步骤并回写状态。
     *
     * <p>轮次多于步骤数时复用最后一个步骤：规划只是对目标的粗略切分，
     * 不能假定"一轮恰好对应一步"。负的轮次是调用方出错，按"不改动规划"处理，
     * 而不是悄悄改到第 0 步。
     */
    private TaskPlan advance(TaskPlan plan,
                             AgentLoopRequest request,
                             int step,
                             PlanStepStatus status) {
        if (plan == null || plan.stepCount() == 0 || step < 0) {
            return plan;
        }
        int index = Math.min(step, plan.stepCount() - 1);
        TaskPlan updated = plan.withStepStatus(index, status);
        if (updated != plan) {
            recordQuietly(request, updated, null);
        }
        return updated;
    }

    private void recordQuietly(AgentLoopRequest request, TaskPlan plan, Throwable error) {
        try {
            runStepRecorder.recordTaskPlan(request.runId(), plan, error);
        } catch (RuntimeException recorderError) {
            LOG.warn("Failed to record task plan without changing loop semantics: runId={}",
                    request.runId(), recorderError);
        }
    }

    private TraceNodeStartCommand planCommand() {
        return new TraceNodeStartCommand(
                "agent-plan",
                TRACE_TYPE_AGENT_PLAN,
                TRACE_CLASS_NAME,
                "plan",
                null,
                0);
    }
}
