/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.bench;

import com.miracle.ai.seahorse.agent.kernel.application.agent.AgentLoopDependencies;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ModelContextEnvelopeOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.policy.TaskPlanningPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 四个行为开关的对比基准驱动器。
 *
 * <p><b>为什么要自己构造而不是走 Spring。</b>四个开关在
 * {@code SeahorseAgentKernelAgentAutoConfiguration} 里是全局单例，同一个 ApplicationContext
 * 内无法分臂。这里每臂直接构造一个 {@link KernelAgentLoopOptions} 与一个
 * {@link KernelAgentLoop}，天然绕开单例限制，也让整个基准不依赖 Spring。
 *
 * <p><b>各臂是阶梯式而非正交。</b>{@code parallel-steps} 在 plan 为空时直接返回空波次，
 * 所以 A3 必须建立在 A1（规划开启）之上；这不是可以随意组合的 2⁴ 因子设计。
 */
final class AgentLoopBenchmarkHarness {

    /** 续跑臂的安全上限：防止脚本桩永远不收敛时把测试跑死。 */
    private static final int MAX_RESUME_CALLS = 24;

    private AgentLoopBenchmarkHarness() {
    }

    enum Arm {
        /**
         * 四开关全关——生产默认基线。
         */
        A0_BASE("A0 基线（四开关全关）", true),
        /**
         * 仅开启确定性规划。收益是结构性的（是否改变完成率、付出多少 token），
         * 脚本桩足以给出结论。
         */
        A1_PLANNING("A1 开启任务规划", true),
        /**
         * 规划 + LLM 语义分解。<b>脚本桩评不出收益</b>：桩对任意提示词返回固定分解，
         * 语义好坏不体现在输出里。只能验管道正确性。
         */
        A2_LLM_PLANNER("A2 + LLM 语义分解", false),
        /**
         * 规划 + 并行波次。<b>脚本桩评不出延迟收益</b>：桩没有网络等待，
         * 并行与串行耗时几乎相同。只能验"并行确实发生"与结果可重现。
         */
        A3_PARALLEL("A3 + 并行波次", false),
        /**
         * 仅开启 checkpoint 续跑，每调用预算 1 步。
         * 它的收益是结构性的（长时程任务能否跑完），脚本桩可以给出结论。
         */
        A4_RESUME("A4 checkpoint 续跑", true);

        private final String label;
        private final boolean scriptedEvaluable;

        Arm(String label, boolean scriptedEvaluable) {
            this.label = label;
            this.scriptedEvaluable = scriptedEvaluable;
        }

        String label() {
            return label;
        }

        /**
         * @return 脚本桩能否给出<b>收益</b>结论；false 表示只能验管道正确性与可重现性
         */
        boolean scriptedEvaluable() {
            return scriptedEvaluable;
        }
    }

    record CaseResult(String caseId,
                      boolean completed,
                      int stepCount,
                      int toolCallCount,
                      int modelRequests,
                      long inputChars,
                      long outputChars,
                      long durationMs,
                      int maxConcurrentTurns,
                      int resumeCalls) {

        static CaseResult of(String caseId, AgentLoopResult result, ScriptedModel model, long durationMs,
                             int maxConcurrentTurns, int resumeCalls) {
            int toolCalls = result.steps().stream()
                    .mapToInt(step -> step.toolCalls().size())
                    .sum();
            return new CaseResult(
                    caseId,
                    !result.truncated(),
                    result.steps().size(),
                    toolCalls,
                    model.requestCount(),
                    model.inputChars(),
                    model.outputChars(),
                    durationMs,
                    maxConcurrentTurns,
                    resumeCalls);
        }
    }

    record ArmResult(Arm arm, List<CaseResult> cases) {

        double completionRate() {
            if (cases.isEmpty()) {
                return 0.0D;
            }
            return cases.stream().filter(CaseResult::completed).count() / (double) cases.size();
        }

        double averageSteps() {
            return cases.isEmpty()
                    ? 0.0D
                    : cases.stream().mapToInt(CaseResult::stepCount).average().orElse(0.0D);
        }

        double averageToolCalls() {
            return cases.isEmpty()
                    ? 0.0D
                    : cases.stream().mapToInt(CaseResult::toolCallCount).average().orElse(0.0D);
        }

        long totalInputChars() {
            return cases.stream().mapToLong(CaseResult::inputChars).sum();
        }

        double averageDurationMs() {
            return cases.isEmpty()
                    ? 0.0D
                    : cases.stream().mapToLong(CaseResult::durationMs).average().orElse(0.0D);
        }

        int maxConcurrentTurns() {
            return cases.stream().mapToInt(CaseResult::maxConcurrentTurns).max().orElse(0);
        }
    }

    static ArmResult runArm(Arm arm, List<AgentLoopBenchmarkCase> cases) {
        List<CaseResult> results = new ArrayList<>(cases.size());
        for (AgentLoopBenchmarkCase benchmarkCase : cases) {
            results.add(runCase(arm, benchmarkCase));
        }
        return new ArmResult(arm, List.copyOf(results));
    }

    private static CaseResult runCase(Arm arm, AgentLoopBenchmarkCase benchmarkCase) {
        BenchCheckpoints checkpoints = new BenchCheckpoints();
        ScriptedModel model = new ScriptedModel(benchmarkCase.workTurns(), "最终答案:" + benchmarkCase.caseId());
        KernelAgentLoop loop = new KernelAgentLoop(new AgentLoopDependencies(
                model,
                new BenchToolRegistry(),
                null,
                options(arm, benchmarkCase),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                checkpoints));

        long startedAt = System.nanoTime();
        AgentLoopResult result = loop.execute(request(benchmarkCase));
        int resumeCalls = 1;
        // 内核不自循环：续跑必须由调用方"同一 runId 再执行一次"。
        // 只有 A4 会重复唤起——其他臂重复执行只是从零重跑，会把"截断"这个
        // 关键观测值抹掉（脚本桩的 turnIndex 跨调用累积，看起来就像自己续跑了）。
        while (arm == Arm.A4_RESUME && result.truncated() && resumeCalls < MAX_RESUME_CALLS) {
            result = loop.execute(request(benchmarkCase));
            resumeCalls++;
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        return CaseResult.of(benchmarkCase.caseId(), result, model, durationMs,
                model.maxConcurrentTurns(), arm == Arm.A4_RESUME ? resumeCalls : 1);
    }

    private static KernelAgentLoopOptions options(Arm arm, AgentLoopBenchmarkCase benchmarkCase) {
        KernelAgentLoopOptions.Builder builder = KernelAgentLoopOptions.builder()
                .contextEnvelope(ModelContextEnvelopeOptions.defaults()
                        .withMode(ModelContextEnvelopeOptions.Mode.DISABLED));
        switch (arm) {
            case A0_BASE -> builder.maxSteps(benchmarkCase.maxSteps());
            case A1_PLANNING -> builder
                    .maxSteps(benchmarkCase.maxSteps())
                    .taskPlanning(TaskPlanningPolicy.builder().enabled(true).build());
            case A2_LLM_PLANNER -> builder
                    .maxSteps(benchmarkCase.maxSteps())
                    .taskPlanning(TaskPlanningPolicy.builder()
                            .enabled(true)
                            .plannerMode(TaskPlanningPolicy.PlannerMode.LLM_WITH_HEURISTIC_FALLBACK)
                            .build());
            case A3_PARALLEL -> builder
                    .maxSteps(benchmarkCase.maxSteps())
                    .taskPlanning(TaskPlanningPolicy.builder()
                            .enabled(true)
                            .plannerMode(TaskPlanningPolicy.PlannerMode.LLM_WITH_HEURISTIC_FALLBACK)
                            .build())
                    .parallelSteps(true)
                    .maxParallelSteps(2);
            case A4_RESUME -> builder
                    // 每次调用只给 1 步预算，剩下的靠续跑补齐
                    .maxSteps(1)
                    .checkpointResume(true);
        }
        return builder.build();
    }

    private static AgentLoopRequest request(AgentLoopBenchmarkCase benchmarkCase) {
        return AgentLoopRequest.builder()
                .question(benchmarkCase.goal())
                .runId("bench-" + benchmarkCase.caseId())
                .allowedToolIds(List.of(ScriptedModel.WORK_TOOL_ID))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.0D).build())
                .maxSteps(benchmarkCase.maxSteps())
                .build();
    }

    /**
     * 判定"收益明显"。四条判据必须同时满足，否则维持开关默认关闭。
     */
    static Verdict judge(ArmResult baseline, ArmResult candidate) {
        double completionGain = candidate.completionRate() - baseline.completionRate();
        double tokenGrowth = baseline.totalInputChars() == 0L
                ? 0.0D
                : (candidate.totalInputChars() - baseline.totalInputChars())
                        / (double) baseline.totalInputChars();
        double stepGrowth = baseline.averageSteps() == 0.0D
                ? 0.0D
                : (candidate.averageSteps() - baseline.averageSteps()) / baseline.averageSteps();
        double toolGrowth = baseline.averageToolCalls() == 0.0D
                ? 0.0D
                : (candidate.averageToolCalls() - baseline.averageToolCalls()) / baseline.averageToolCalls();

        boolean completionOk = completionGain >= 0.05D;
        boolean costOk = tokenGrowth <= 0.30D;
        boolean notWorse = stepGrowth <= 0.10D && toolGrowth <= 0.10D;
        PairedTest test = mcnemar(baseline, candidate);
        boolean significant = test.significant();

        boolean clearWin = completionOk && costOk && notWorse && significant;
        String detail = String.format(Locale.ROOT,
                "完成率 %+.1fpp（需 ≥+5.0pp）｜token %+.1f%%（需 ≤+30%%）｜步数 %+.1f%%、工具调用 %+.1f%%（需 ≤+10%%）"
                        + "｜McNemar b=%d c=%d p=%.4f（需 <0.05）%s",
                completionGain * 100, tokenGrowth * 100, stepGrowth * 100, toolGrowth * 100,
                test.onlyCandidatePassed(), test.onlyBaselinePassed(), test.pValue(),
                test.enough ? "" : "｜不一致样本不足 10，统计检验无力");
        return new Verdict(candidate.arm(), clearWin, detail, candidate.arm().scriptedEvaluable());
    }

    record Verdict(Arm arm, boolean clearWin, String detail, boolean scriptedEvaluable) {
    }

    record PairedTest(int onlyCandidatePassed, int onlyBaselinePassed, double pValue, boolean enough) {

        boolean significant() {
            return enough && pValue() < 0.05D;
        }
    }

    /**
     * 配对 McNemar 检验（精确二项、双侧），用于比较两个臂在同一批用例上的完成/未完成。
     */
    static PairedTest mcnemar(ArmResult baseline, ArmResult candidate) {
        int onlyCandidate = 0;
        int onlyBaseline = 0;
        List<CaseResult> baselineCases = baseline.cases();
        List<CaseResult> candidateCases = candidate.cases();
        int size = Math.min(baselineCases.size(), candidateCases.size());
        for (int i = 0; i < size; i++) {
            boolean baseOk = baselineCases.get(i).completed();
            boolean candOk = candidateCases.get(i).completed();
            if (!baseOk && candOk) {
                onlyCandidate++;
            } else if (baseOk && !candOk) {
                onlyBaseline++;
            }
        }
        int discordant = onlyCandidate + onlyBaseline;
        // 不一致对少于 10 时检验基本没有分辨力，明确标注出来，避免拿噪声当结论
        boolean enough = discordant >= 10;
        return new PairedTest(onlyCandidate, onlyBaseline, exactBinomialTwoSided(onlyCandidate, discordant), enough);
    }

    private static double exactBinomialTwoSided(int successes, int trials) {
        if (trials == 0) {
            return 1.0D;
        }
        double alpha = 0.5D;
        int k = Math.min(successes, trials - successes);
        double cumulative = 0.0D;
        for (int i = 0; i <= k; i++) {
            cumulative += binomial(trials, i) * Math.pow(alpha, i) * Math.pow(1 - alpha, trials - i);
        }
        return Math.min(1.0D, 2 * cumulative);
    }

    private static double binomial(int n, int k) {
        double result = 1.0D;
        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
        }
        return result;
    }

    /** 内存 checkpoint 仓储：续跑臂必须显式注入，否则内核会静默降级为"不落盘"。 */
    private static final class BenchCheckpoints implements AgentCheckpointRepositoryPort {

        private final List<AgentCheckpoint> saved = new ArrayList<>();

        @Override
        public void save(AgentCheckpoint checkpoint) {
            saved.add(checkpoint);
        }

        @Override
        public java.util.Optional<AgentCheckpoint> findLatestByRunId(String runId) {
            return saved.stream()
                    .filter(item -> item.runId().equals(runId))
                    .reduce((first, second) -> second);
        }

        @Override
        public List<AgentCheckpoint> listByRunId(String runId) {
            return saved.stream().filter(item -> item.runId().equals(runId)).toList();
        }
    }
}
