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

import com.miracle.ai.seahorse.agent.kernel.application.consistency.CompensationRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 补偿重试定时任务。
 *
 * <p>每 60 秒扫描一次待重试的补偿日志，分发到对应的重试处理器执行。
 * 集群环境下通过分布式锁保证只有一个实例执行。
 */
public class CompensationRetryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationRetryJob.class);

    private final CompensationRetryService compensationRetryService;

    public CompensationRetryJob(CompensationRetryService compensationRetryService) {
        this.compensationRetryService = compensationRetryService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void executeRetry() {
        try {
            compensationRetryService.executeRetry();
        } catch (Exception e) {
            LOGGER.error("Compensation retry job failed unexpectedly", e);
        }
    }
}
