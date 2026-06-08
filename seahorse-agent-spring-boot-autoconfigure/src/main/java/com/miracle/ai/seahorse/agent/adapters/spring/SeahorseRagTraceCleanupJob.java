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

import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class SeahorseRagTraceCleanupJob {

    private static final String LOCK_NAME = "job:rag-trace-cleanup";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(30);
    private static final int DEFAULT_TTL_DAYS = 30;
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final RagTraceRepositoryPort traceRepositoryPort;
    private final DistributedLockPort lockPort;
    private final int ttlDays;
    private final int batchSize;

    public SeahorseRagTraceCleanupJob(RagTraceRepositoryPort traceRepositoryPort,
                                      DistributedLockPort lockPort,
                                      int ttlDays,
                                      int batchSize) {
        this.traceRepositoryPort = Objects.requireNonNull(traceRepositoryPort,
                "traceRepositoryPort must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.ttlDays = ttlDays <= 0 ? DEFAULT_TTL_DAYS : ttlDays;
        this.batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
    }

    @Scheduled(cron = "${seahorse-agent.rag-trace.cleanup-cron:0 0 3 * * ?}")
    public void cleanupExpiredRuns() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            // TTL 清理属于后台治理职责，保持 recorder 只负责采样与写入链路。
            traceRepositoryPort.deleteRunsBefore(Instant.now().minus(ttlDays, ChronoUnit.DAYS), batchSize);
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }
}
