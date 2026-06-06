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
import java.util.function.Supplier;

/**
 * 并发控制工具服务。
 *
 * <p>提供三种并发控制策略：
 * <ul>
 *     <li>分布式锁：适用于跨实例的关键操作（如支付扣款）</li>
 *     <li>乐观锁：通过 version 字段实现 CAS，适用于低冲突场景</li>
 *     <li>悲观锁：通过 SELECT FOR UPDATE 实现行锁，适用于高冲突场景（如配额扣减）</li>
 * </ul>
 */
public class ConcurrencyControlService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrencyControlService.class);

    private final DistributedLockPort distributedLockPort;

    public ConcurrencyControlService(DistributedLockPort distributedLockPort) {
        this.distributedLockPort = distributedLockPort;
    }

    /**
     * 使用分布式锁保护操作。
     *
     * @param lockKey    锁名称
     * @param waitTime   等待获取锁的最大时间
     * @param leaseTime  锁持有最大时间（自动释放）
     * @param operation  受保护的操作
     * @return 操作结果
     * @throws ConcurrencyException 如果获取锁超时
     */
    public <T> T executeWithDistributedLock(String lockKey, Duration waitTime,
                                            Duration leaseTime, Supplier<T> operation) {
        boolean acquired = distributedLockPort.tryLock(lockKey, waitTime, leaseTime);
        if (!acquired) {
            LOGGER.warn("Failed to acquire distributed lock: {}", lockKey);
            throw new ConcurrencyException("Failed to acquire lock: " + lockKey +
                    ", resource is being processed by another request");
        }
        try {
            return operation.get();
        } finally {
            distributedLockPort.unlock(lockKey);
        }
    }

    /**
     * 使用分布式锁保护操作（无返回值版本）。
     */
    public void executeWithDistributedLock(String lockKey, Duration waitTime,
                                           Duration leaseTime, Runnable operation) {
        executeWithDistributedLock(lockKey, waitTime, leaseTime, () -> {
            operation.run();
            return null;
        });
    }

    /**
     * 乐观锁冲突异常。
     */
    public static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String entityType, Object id) {
            super("Optimistic lock conflict on " + entityType + " with id=" + id +
                    ". The record was modified by another transaction. Please retry.");
        }
    }

    /**
     * 并发控制异常（锁获取失败等）。
     */
    public static class ConcurrencyException extends RuntimeException {
        public ConcurrencyException(String message) {
            super(message);
        }
    }
}
