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

import java.util.List;
import java.util.Locale;

/**
 * 把各臂的对比结果渲染成 markdown。
 *
 * <p>报告里会明确标注哪些臂<b>只能验管道</b>。这不是免责声明，而是防止把脚本桩的
 * 结果当成"LLM 规划器有效"或"并行更快"的证据——桩不理解提示词，也没有网络等待，
 * 这两件事它测不出来。
 */
final class AgentLoopBenchmarkReportWriter {

    private AgentLoopBenchmarkReportWriter() {
    }

    static String render(List<AgentLoopBenchmarkHarness.ArmResult> results,
                         List<AgentLoopBenchmarkHarness.Verdict> verdicts,
                         int caseCount) {
        StringBuilder out = new StringBuilder();
        out.append("# AgentLoop 开关收益基准\n\n");
        out.append("用例数：").append(caseCount).append("\n\n");

        out.append("## 各臂指标\n\n");
        out.append("| 臂 | 完成率 | 平均步数 | 平均工具调用 | 输入字符合计 | token 增幅 | 平均耗时(ms) | 最大并发轮次 |\n");
        out.append("|---|---|---|---|---|---|---|---|\n");
        long baselineChars = results.isEmpty() ? 0L : results.get(0).totalInputChars();
        for (AgentLoopBenchmarkHarness.ArmResult result : results) {
            double growth = baselineChars == 0L
                    ? 0.0D
                    : (result.totalInputChars() - baselineChars) / (double) baselineChars;
            out.append(String.format(Locale.ROOT,
                    "| %s | %.1f%% | %.2f | %.2f | %d | %+.1f%% | %.1f | %d |%n",
                    result.arm().label(),
                    result.completionRate() * 100,
                    result.averageSteps(),
                    result.averageToolCalls(),
                    result.totalInputChars(),
                    growth * 100,
                    result.averageDurationMs(),
                    result.maxConcurrentTurns()));
        }

        out.append("\n## 判定（对比 A0 基线）\n\n");
        if (verdicts.isEmpty()) {
            out.append("_无_\n");
        }
        for (AgentLoopBenchmarkHarness.Verdict verdict : verdicts) {
            out.append("- **").append(verdict.arm().label()).append("**：")
                    .append(verdict.clearWin() ? "收益明显" : "未达阈值").append('\n');
            out.append("  - ").append(verdict.detail()).append('\n');
            if (!verdict.scriptedEvaluable()) {
                out.append("  - 脚本桩下仅验管道正确性与可重现性，收益结论需真实模型验证\n");
            }
        }

        out.append("\n## 读数须知\n\n");
        out.append("- **A4 的「平均步数 / 平均工具调用」是单次调用的值，不可与其他臂横向比较。**\n");
        out.append("  续跑臂每次调用只给 1 步预算，返回的 steps 只反映最后一次调用；\n");
        out.append("  它的可比指标是完成率与 token，不是步数。\n");
        out.append("- token 用「输入字符合计」近似，只能用于同桩横向比较。\n\n");
        out.append("## 判据\n\n");
        out.append("1. 完成率提升 ≥ 5 个百分点\n");
        out.append("2. token（输入字符近似）增幅 ≤ 30%\n");
        out.append("3. 步数、工具调用次数不劣化 ≤ 10%\n");
        out.append("4. 配对 McNemar 检验 p < 0.05，且不一致样本 ≥ 10\n\n");
        out.append("四条须同时满足才判“收益明显”，否则维持开关默认关闭。\n\n");
        out.append("## 评估边界\n\n");
        out.append("- 脚本桩对任意提示词返回固定内容，**测不出 LLM 语义分解的收益**\n");
        out.append("- 脚本桩无网络等待，**测不出并行的真实延迟收益**；耗时仅记录、不作判据\n");
        return out.toString();
    }
}
