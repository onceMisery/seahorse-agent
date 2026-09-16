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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 四个行为开关的 A/B 基准入口。
 *
 * <p>它断言的是<b>harness 本身可信</b>（用例量足够、基线确实会截断、续跑确实发生了、
 * 并行确实并发了），而不是"某臂一定赢"——后者是实验结论，每次跑出来可能不同，
 * 写死成断言只会把基准变成摆设。真实结论请读 {@code target/agentloop-benchmark-report.md}。
 */
class AgentLoopBenchmarkTests {

    private static final int MIN_CASES = 30;
    private static final Path REPORT = Path.of("target", "agentloop-benchmark-report.md");

    @Test
    void shouldRunAllArmsAndWriteAComparisonReport() {
        List<AgentLoopBenchmarkCase> cases = AgentLoopBenchmarkCaseRepository.loadAll();
        assertTrue(cases.size() >= MIN_CASES,
                "基准集至少需要 " + MIN_CASES + " 条用例才能做配对检验，当前 " + cases.size());

        List<AgentLoopBenchmarkHarness.ArmResult> results = new ArrayList<>();
        for (AgentLoopBenchmarkHarness.Arm arm : AgentLoopBenchmarkHarness.Arm.values()) {
            results.add(AgentLoopBenchmarkHarness.runArm(arm, cases));
        }
        AgentLoopBenchmarkHarness.ArmResult baseline = results.get(0);

        List<AgentLoopBenchmarkHarness.Verdict> verdicts = new ArrayList<>();
        for (int i = 1; i < results.size(); i++) {
            verdicts.add(AgentLoopBenchmarkHarness.judge(baseline, results.get(i)));
        }

        writeReport(AgentLoopBenchmarkReportWriter.render(results, verdicts, cases.size()));

        // 基线必须真的触及天花板，否则整个实验没有可观测的落差
        assertTrue(baseline.completionRate() < 1.0D, "基线必须有一部分长时程用例被截断，否则测不出收益");
        assertEquals(AgentLoopBenchmarkHarness.Arm.A0_BASE, baseline.arm());
    }

    @Test
    void shouldResumeLongHorizonCasesInsteadOfTruncatingThem() {
        List<AgentLoopBenchmarkCase> cases = longHorizonCases();
        assertFalse(cases.isEmpty(), "需要至少一条长时程用例");

        AgentLoopBenchmarkHarness.ArmResult resume =
                AgentLoopBenchmarkHarness.runArm(AgentLoopBenchmarkHarness.Arm.A4_RESUME, cases);

        for (AgentLoopBenchmarkHarness.CaseResult result : resume.cases()) {
            assertTrue(result.resumeCalls() > 1,
                    "续跑臂必须真的发生了多次调用，否则说明检查点没生效: " + result.caseId());
            assertTrue(result.completed(),
                    "解除长时程天花板后，长时程用例应当能跑完: " + result.caseId());
        }
    }

    @Test
    void shouldRunWaveTurnsConcurrentlyAndStayReproducible() {
        List<AgentLoopBenchmarkCase> cases = AgentLoopBenchmarkCaseRepository.loadAll();

        AgentLoopBenchmarkHarness.ArmResult serial =
                AgentLoopBenchmarkHarness.runArm(AgentLoopBenchmarkHarness.Arm.A2_LLM_PLANNER, cases);
        AgentLoopBenchmarkHarness.ArmResult parallel =
                AgentLoopBenchmarkHarness.runArm(AgentLoopBenchmarkHarness.Arm.A3_PARALLEL, cases);

        assertTrue(parallel.maxConcurrentTurns() >= 2,
                "并行臂必须观测到同时飞行 &gt;= 2 个模型轮次，实际 " + parallel.maxConcurrentTurns());
        assertEquals(1, serial.maxConcurrentTurns(),
                "串行臂不应出现并发轮次");

        Set<String> serialCompleted = completedIds(serial);
        Set<String> parallelCompleted = completedIds(parallel);
        assertEquals(serialCompleted, parallelCompleted,
                "并行只应是加速手段，不能改变完成/截断的结果集——否则就成了新语义");
    }

    private static Set<String> completedIds(AgentLoopBenchmarkHarness.ArmResult result) {
        Set<String> ids = new LinkedHashSet<>();
        result.cases().stream().filter(AgentLoopBenchmarkHarness.CaseResult::completed)
                .forEach(item -> ids.add(item.caseId()));
        return ids;
    }

    private static List<AgentLoopBenchmarkCase> longHorizonCases() {
        return AgentLoopBenchmarkCaseRepository.loadAll().stream()
                .filter(AgentLoopBenchmarkCase::expectedToTruncate)
                .toList();
    }

    private static void writeReport(String markdown) {
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, markdown, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to write benchmark report", ex);
        }
    }
}
