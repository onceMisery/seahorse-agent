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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Seahorse 原生文档刷新调度入口。
 */
public class SeahorseDocumentRefreshJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeahorseDocumentRefreshJob.class);
    private static final String SYSTEM_OPERATOR = "system";
    private static final String LOCK_NAME = "job:document-refresh";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(10);

    private final DocumentRefreshInboundPort refreshPort;
    private final DistributedLockPort lockPort;
    private final int batchSize;

    public SeahorseDocumentRefreshJob(DocumentRefreshInboundPort refreshPort, int batchSize) {
        this(refreshPort, DistributedLockPort.noop(), batchSize);
    }

    public SeahorseDocumentRefreshJob(DocumentRefreshInboundPort refreshPort,
                                      DistributedLockPort lockPort,
                                      int batchSize) {
        this.refreshPort = Objects.requireNonNull(refreshPort, "refreshPort must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.batchSize = Math.max(batchSize, 1);
    }

    @Scheduled(fixedDelayString = "${seahorse.agent.document-refresh.scan-delay-ms:10000}")
    public void scanDueSchedules() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            refreshPort.refreshDueSchedules(Instant.now(), batchSize, SYSTEM_OPERATOR);
        } catch (Exception ex) {
            LOGGER.warn("Seahorse document refresh scan failed", ex);
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }
}
