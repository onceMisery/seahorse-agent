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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.application.agent.research.ResearchRunOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * 研究任务执行 worker：周期性 claim/执行队列中的研究步骤。
 */
public class SeahorseResearchWorkerJob {

    private static final Logger log = LoggerFactory.getLogger(SeahorseResearchWorkerJob.class);
    private static final int MAX_BATCH_PER_TICK = 10;

    private final ResearchRunOrchestrator orchestrator;

    public SeahorseResearchWorkerJob(ResearchRunOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
    }

    @Scheduled(fixedDelayString = "${seahorse-agent.research.worker.fixed-delay-ms:500}")
    public void tick() {
        try {
            int processed = 0;
            while (processed < MAX_BATCH_PER_TICK && orchestrator.pollAndExecute()) {
                processed++;
            }
        } catch (Exception ex) {
            log.warn("Research worker tick failed", ex);
        }
    }
}
