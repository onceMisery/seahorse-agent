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

import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Seahorse 关键词索引计划型维护任务。
 *
 * <p>该任务默认不注册；只有显式配置目标文档或知识库后才会调用 kernel 入站端口执行补偿重建。
 */
public class SeahorseKeywordIndexMaintenanceJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeahorseKeywordIndexMaintenanceJob.class);
    private static final String LOCK_NAME = "job:keyword-index-maintenance";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(30);
    private static final int DEFAULT_BATCH_SIZE = 50;

    private final KeywordIndexMaintenanceInboundPort maintenancePort;
    private final DistributedLockPort lockPort;
    private final List<String> docIds;
    private final List<String> kbIds;
    private final int batchSize;

    public SeahorseKeywordIndexMaintenanceJob(KeywordIndexMaintenanceInboundPort maintenancePort,
                                              String docIds,
                                              String kbIds,
                                              int batchSize) {
        this(maintenancePort, DistributedLockPort.noop(), docIds, kbIds, batchSize);
    }

    public SeahorseKeywordIndexMaintenanceJob(KeywordIndexMaintenanceInboundPort maintenancePort,
                                              DistributedLockPort lockPort,
                                              String docIds,
                                              String kbIds,
                                              int batchSize) {
        this.maintenancePort = Objects.requireNonNull(maintenancePort, "maintenancePort must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.docIds = parseCsv(docIds);
        this.kbIds = parseCsv(kbIds);
        this.batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
    }

    @Scheduled(fixedDelayString = "${seahorse-agent.keyword-index.maintenance.scan-delay-ms:60000}")
    public void rebuildConfiguredTargets() {
        if (docIds.isEmpty() && kbIds.isEmpty()) {
            return;
        }
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            rebuildDocuments();
            rebuildKnowledgeBases();
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }

    private void rebuildDocuments() {
        for (String docId : docIds) {
            try {
                maintenancePort.rebuildDocument(Long.parseLong(docId));
            } catch (RuntimeException ex) {
                LOGGER.warn("Seahorse keyword index document rebuild failed, docId={}", docId, ex);
            }
        }
    }

    private void rebuildKnowledgeBases() {
        for (String kbId : kbIds) {
            try {
                maintenancePort.rebuildKnowledgeBase(Long.parseLong(kbId), batchSize);
            } catch (RuntimeException ex) {
                LOGGER.warn("Seahorse keyword index knowledge base rebuild failed, kbId={}", kbId, ex);
            }
        }
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .forEach(values::add);
        return List.copyOf(values);
    }
}
