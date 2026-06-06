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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 统一线程池配置。
 *
 * <p>为不同场景提供隔离的线程池，避免相互影响：
 * <ul>
 *     <li>aiExecutor: AI 模型调用（长耗时）</li>
 *     <li>notificationExecutor: 通知发送（中等耗时）</li>
 *     <li>taskExecutor: 通用异步任务</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncExecutorConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncExecutorConfiguration.class);

    @Bean("aiExecutor")
    @ConditionalOnMissingBean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy("aiExecutor"));
        executor.initialize();
        return executor;
    }

    @Bean("notificationExecutor")
    @ConditionalOnMissingBean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notify-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy("notificationExecutor"));
        executor.initialize();
        return executor;
    }

    @Bean("taskExecutor")
    @ConditionalOnMissingBean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("task-");
        executor.setKeepAliveSeconds(120);
        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy("taskExecutor"));
        executor.initialize();
        return executor;
    }

    /**
     * CallerRunsPolicy 的增强版本，拒绝时输出警告日志。
     */
    private static class LoggingCallerRunsPolicy implements RejectedExecutionHandler {
        private final String poolName;

        LoggingCallerRunsPolicy(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            LOGGER.warn("Thread pool '{}' exhausted (active={}, queue={}), running in caller thread",
                    poolName, executor.getActiveCount(), executor.getQueue().size());
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    }
}
