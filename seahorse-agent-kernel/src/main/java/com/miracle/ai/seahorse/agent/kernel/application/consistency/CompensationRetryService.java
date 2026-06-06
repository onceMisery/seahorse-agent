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

package com.miracle.ai.seahorse.agent.kernel.application.consistency;

import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 补偿重试服务。
 *
 * <p>定期扫描失败的补偿日志，按操作类型分发重试逻辑。
 * 使用分布式锁保证集群环境下只有一个实例执行补偿。
 */
public class CompensationRetryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationRetryService.class);
    private static final String LOCK_KEY = "compensation:retry:lock";
    private static final int BATCH_SIZE = 50;

    private final CompensationLogPort compensationLogPort;
    private final DistributedLockPort distributedLockPort;
    private final Map<String, Function<String, Boolean>> retryHandlers;

    public CompensationRetryService(CompensationLogPort compensationLogPort,
                                    DistributedLockPort distributedLockPort,
                                    Map<String, Function<String, Boolean>> retryHandlers) {
        this.compensationLogPort = compensationLogPort;
        this.distributedLockPort = distributedLockPort;
        this.retryHandlers = retryHandlers;
    }

    /**
     * 执行补偿重试（由定时任务调用）。
     */
    public void executeRetry() {
        boolean locked = distributedLockPort.tryLock(LOCK_KEY, Duration.ofSeconds(5), Duration.ofMinutes(2));
        if (!locked) {
            LOGGER.debug("Compensation retry lock not acquired, skipping this round");
            return;
        }

        try {
            List<CompensationLog> pendingLogs = compensationLogPort.findPendingRetries(BATCH_SIZE);
            LOGGER.info("Found {} pending compensation logs to retry", pendingLogs.size());

            for (CompensationLog log : pendingLogs) {
                retryOne(log);
            }
        } finally {
            distributedLockPort.unlock(LOCK_KEY);
        }
    }

    private void retryOne(CompensationLog log) {
        Function<String, Boolean> handler = retryHandlers.get(log.getOperationType());
        if (handler == null) {
            LOGGER.warn("No retry handler registered for operation type: {}", log.getOperationType());
            compensationLogPort.updateStatus(log.getId(), CompensationLog.CompensationStatus.FAILED,
                    "No handler registered for type: " + log.getOperationType());
            return;
        }

        try {
            compensationLogPort.incrementRetryCount(log.getId());
            boolean success = handler.apply(log.getPayload());

            if (success) {
                compensationLogPort.updateStatus(log.getId(), CompensationLog.CompensationStatus.SUCCESS, null);
                LOGGER.info("Compensation retry succeeded: type={}, id={}", log.getOperationType(), log.getOperationId());
            } else if (log.getRetryCount() + 1 >= log.getMaxRetries()) {
                compensationLogPort.updateStatus(log.getId(), CompensationLog.CompensationStatus.FAILED,
                        "Max retries reached");
                LOGGER.warn("Compensation max retries reached: type={}, id={}", log.getOperationType(), log.getOperationId());
            } else {
                compensationLogPort.updateStatus(log.getId(), CompensationLog.CompensationStatus.PENDING,
                        "Retry returned false");
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (log.getRetryCount() + 1 >= log.getMaxRetries()) {
                compensationLogPort.updateStatus(log.getId(), CompensationLog.CompensationStatus.FAILED, errorMsg);
                LOGGER.error("Compensation retry FAILED permanently: type={}, id={}", log.getOperationType(), log.getOperationId(), e);
            } else {
                compensationLogPort.updateStatus(log.getId(), CompensationLog.CompensationStatus.PENDING, errorMsg);
                LOGGER.warn("Compensation retry failed (will retry): type={}, id={}", log.getOperationType(), log.getOperationId(), e);
            }
        }
    }
}
