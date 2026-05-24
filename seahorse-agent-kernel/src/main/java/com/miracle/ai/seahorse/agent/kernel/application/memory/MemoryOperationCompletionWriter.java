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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Slice 3 续 cut 10：把 operation log "completed" 写入逻辑从 {@code DefaultMemoryEnginePort} 拆出。
 *
 * <p>原 facade 私有方法 {@code markOperationCompleted} / {@code operationStatus} /
 * {@code decisionMap(result)} / {@code decisionMap(result, classification)} 合并到本 service，
 * 对外只暴露 {@link #markCompleted(String, MemoryIngestionResult, MemoryClassificationResult)} 单一入口。
 *
 * <p>结果状态映射保持完全一致：
 * <ul>
 *     <li>{@code null result} → {@link MemoryOperationStatus#FAILED}</li>
 *     <li>{@link MemoryIngestionStatus#ACCEPTED} → SUCCEEDED</li>
 *     <li>{@link MemoryIngestionStatus#REJECTED} + REVIEW action → REVIEW；其它 REJECTED → REJECTED</li>
 *     <li>{@link MemoryIngestionStatus#IGNORED} / FAILED 直通对应 MemoryOperationStatus</li>
 * </ul>
 */
public final class MemoryOperationCompletionWriter {

    private final MemoryOperationLogPort memoryOperationLogPort;
    private final MemoryRefinerMetadataWriter refinerMetadataWriter;

    public MemoryOperationCompletionWriter(MemoryOperationLogPort memoryOperationLogPort,
                                           MemoryRefinerMetadataWriter refinerMetadataWriter) {
        this.memoryOperationLogPort = Objects.requireNonNull(memoryOperationLogPort,
                "memoryOperationLogPort must not be null");
        this.refinerMetadataWriter = Objects.requireNonNull(refinerMetadataWriter,
                "refinerMetadataWriter must not be null");
    }

    public void markCompleted(String operationId,
                              MemoryIngestionResult result,
                              MemoryClassificationResult classification) {
        memoryOperationLogPort.markCompleted(operationId, statusFor(result), decisionMap(result, classification));
    }

    private static MemoryOperationStatus statusFor(MemoryIngestionResult result) {
        if (result == null) {
            return MemoryOperationStatus.FAILED;
        }
        return switch (result.status()) {
            case ACCEPTED -> MemoryOperationStatus.SUCCEEDED;
            case REJECTED -> result.action() == MemoryIngestionAction.REVIEW
                    ? MemoryOperationStatus.REVIEW
                    : MemoryOperationStatus.REJECTED;
            case IGNORED -> MemoryOperationStatus.IGNORED;
            case FAILED -> MemoryOperationStatus.FAILED;
        };
    }

    private Map<String, Object> decisionMap(MemoryIngestionResult result, MemoryClassificationResult classification) {
        Map<String, Object> values = new LinkedHashMap<>(decisionMap(result));
        refinerMetadataWriter.appendRefined(values, classification);
        return values;
    }

    private static Map<String, Object> decisionMap(MemoryIngestionResult result) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (result == null) {
            values.put("status", MemoryIngestionStatus.FAILED.name());
            values.put("action", MemoryIngestionAction.IGNORE.name());
            values.put("reason", "empty_result");
            values.put("operations", List.of());
            return values;
        }
        values.put("status", result.status().name());
        values.put("action", result.action().name());
        values.put("reason", result.reason());
        values.put("operations", result.operations());
        values.putAll(result.details());
        return values;
    }
}
