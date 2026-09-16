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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 步骤序号是"读当前条数 + 1"的 read-modify-write。
 *
 * <p>今天所有记账都发生在主线程，看不出问题；但这些测试锁定的是"记账被移到 worker 线程后
 * 仍然成立"——工具并行度调大、或波次内并行记账，都会让多个记账同时发生。
 */
class RepositoryAgentRunStepRecorderConcurrencyTests {

    private static final String RUN_ID = "run-concurrent";
    private static final int THREADS = 8;
    private static final int PER_THREAD = 25;

    @Test
    void shouldAssignUniqueStepNumbersWhenRecordingConcurrently() throws Exception {
        MemoryAgentRunRepository repository = new MemoryAgentRunRepository();
        RepositoryAgentRunStepRecorder recorder =
                new RepositoryAgentRunStepRecorder(repository, java.time.Clock.systemUTC());

        runConcurrently(recorder);

        assertEquals(THREADS * PER_THREAD, repository.steps.size(), "每条记账都要落库，一条都不能丢");
        Set<Integer> distinct = repository.steps.stream()
                .map(AgentStep::stepNo)
                .collect(Collectors.toSet());
        assertEquals(repository.steps.size(), distinct.size(),
                "步骤序号必须唯一——撞号会让运行回放的顺序不可信");
        assertEquals(1, repository.steps.stream().mapToInt(AgentStep::stepNo).min().orElseThrow());
        assertEquals(repository.steps.size(),
                repository.steps.stream().mapToInt(AgentStep::stepNo).max().orElseThrow(),
                "序号应连续落在 1..N，而不是只保证互不相同");
    }

    @Test
    void shouldKeepStepIdsUniqueWhenRecordingConcurrently() throws Exception {
        MemoryAgentRunRepository repository = new MemoryAgentRunRepository();
        RepositoryAgentRunStepRecorder recorder =
                new RepositoryAgentRunStepRecorder(repository, java.time.Clock.systemUTC());

        runConcurrently(recorder);

        Set<String> distinctIds = repository.steps.stream()
                .map(AgentStep::stepId)
                .collect(Collectors.toSet());
        assertEquals(repository.steps.size(), distinctIds.size(), "步骤 ID 由雪花算法生成，也不能撞");
    }

    private static void runConcurrently(RepositoryAgentRunStepRecorder recorder) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch done = new CountDownLatch(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                final int index = i;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int j = 0; j < PER_THREAD; j++) {
                        if (index % 2 == 0) {
                            recorder.recordModelTurn(RUN_ID, "{\"input\":" + j + "}", "{\"ok\":true}", null);
                        } else {
                            recorder.recordToolCall(RUN_ID,
                                    com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall
                                            .of("call-" + index + "-" + j, "web_search", java.util.Map.of()),
                                    com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation
                                            .ok("call-" + index + "-" + j, "done"));
                        }
                    }
                    done.countDown();
                });
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发记账应在超时前完成");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 故意不加锁：用来验证"序号唯一"是靠记录器的锁保证的，而不是靠仓储恰好串行。
     * listSteps 返回的是快照，本身就要求调用方自己保证 read-modify-write 的原子性。
     */
    private static final class MemoryAgentRunRepository implements AgentRunRepositoryPort {

        private final List<AgentStep> steps = new CopyOnWriteArrayList<>();

        @Override
        public void createRun(AgentRun run) {
            // 该测试不涉及运行记录本身
        }

        @Override
        public void updateRun(AgentRun run) {
            // 该测试不涉及运行记录本身
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.empty();
        }

        @Override
        public void appendStep(AgentStep step) {
            steps.add(step);
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            List<AgentStep> matched = new ArrayList<>();
            for (AgentStep step : steps) {
                if (step.runId().equals(runId)) {
                    matched.add(step);
                }
            }
            return matched;
        }
    }
}
