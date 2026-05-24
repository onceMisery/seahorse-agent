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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Slice 3 续 cut 4：refiner batch 安全闸——当一批 refined operations 触发"过多 operation"
 * 或"delete 占比过高"任一条件时，覆写为 REVIEW classification 以阻止批量误清理。
 *
 * <p>原 facade 私有方法 {@code circuitBreakUnsafeBatch} /
 * {@code circuitBrokenBatchClassification} / {@code refinerBatchDeleteRatio} /
 * {@code refinerBatchOperationSummaries} / {@code refinerOperationContent} 全部合并到本
 * service，对外仅暴露 {@link #evaluate(MemoryRefinementResult, List)} 单一入口。
 *
 * <p>策略阈值 {@code maxOperationCount} / {@code maxDeleteRatio} 由构造时传入，对应历史
 * {@code options.maxRefinerBatchOperations()} / {@code options.maxRefinerDeleteRatio()}。
 *
 * <p>无任何 outbound port 依赖；保持纯函数（除可读性的 final field 外无可变状态）。
 */
public final class MemoryRefinerBatchCircuitBreaker {

    public static final String STATUS_CIRCUIT_BREAKER = "circuit_breaker";
    public static final String TARGET_KIND = "REFINER_BATCH";
    public static final String CIRCUIT_BREAKER_REASON = "refiner_batch_circuit_breaker";
    public static final String CIRCUIT_OPERATION_COUNT = "OPERATION_COUNT";
    public static final String CIRCUIT_DELETE_RATIO = "DELETE_RATIO";
    public static final String REASON_OPERATION_COUNT_EXCEEDED = "operation_count_exceeded";
    public static final String REASON_DELETE_RATIO_EXCEEDED = "delete_ratio_exceeded";
    public static final String METADATA_OPERATION_COUNT = "refinerBatchOperationCount";
    public static final String METADATA_DELETE_RATIO = "refinerBatchDeleteRatio";
    public static final String METADATA_CIRCUIT_REASON = "refinerBatchCircuitReason";
    public static final String METADATA_CIRCUIT_TYPE = "refinerBatchCircuitType";
    public static final String METADATA_OPERATIONS = "refinerBatchOperations";

    private static final String METADATA_CONTENT_KEY = "content";

    private final int maxOperationCount;
    private final double maxDeleteRatio;

    public MemoryRefinerBatchCircuitBreaker(int maxOperationCount, double maxDeleteRatio) {
        this.maxOperationCount = maxOperationCount;
        this.maxDeleteRatio = maxDeleteRatio;
    }

    /**
     * 评估一批 classification 是否需要触发熔断。
     *
     * @return 若超过阈值返回熔断 classification（{@link MemoryIngestionAction#REVIEW}）；
     *     否则返回 {@code null}，调用方继续后续 batch 路径。
     */
    public MemoryClassificationResult evaluate(MemoryRefinementResult result,
                                               List<MemoryClassificationResult> classifications) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(classifications, "classifications must not be null");
        int operationCount = classifications.size();
        if (operationCount > maxOperationCount) {
            return circuitBroken(
                    result,
                    classifications,
                    CIRCUIT_OPERATION_COUNT,
                    REASON_OPERATION_COUNT_EXCEEDED);
        }
        double deleteRatio = deleteRatio(classifications);
        if (operationCount > 1 && deleteRatio > maxDeleteRatio) {
            return circuitBroken(
                    result,
                    classifications,
                    CIRCUIT_DELETE_RATIO,
                    REASON_DELETE_RATIO_EXCEEDED);
        }
        return null;
    }

    private MemoryClassificationResult circuitBroken(MemoryRefinementResult result,
                                                     List<MemoryClassificationResult> classifications,
                                                     String circuitType,
                                                     String circuitReason) {
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata());
        metadata.put("status", STATUS_CIRCUIT_BREAKER);
        metadata.put(METADATA_OPERATION_COUNT, classifications.size());
        metadata.put(METADATA_DELETE_RATIO, deleteRatio(classifications));
        metadata.put(METADATA_CIRCUIT_TYPE, circuitType);
        metadata.put(METADATA_CIRCUIT_REASON, circuitReason);
        metadata.put("maxRefinerBatchOperations", maxOperationCount);
        metadata.put("maxRefinerDeleteRatio", maxDeleteRatio);
        metadata.put(METADATA_OPERATIONS, operationSummaries(classifications));
        return new MemoryClassificationResult(
                MemoryIngestionAction.REVIEW,
                null,
                null,
                new RefinedMemoryDelta(
                        MemoryIngestionAction.REVIEW,
                        TARGET_KIND,
                        "",
                        CIRCUIT_BREAKER_REASON,
                        metadata),
                CIRCUIT_BREAKER_REASON);
    }

    private static double deleteRatio(List<MemoryClassificationResult> classifications) {
        if (classifications == null || classifications.isEmpty()) {
            return 0D;
        }
        int deleteCount = 0;
        for (MemoryClassificationResult classification : classifications) {
            RefinedMemoryDelta delta = classification.refinedDelta();
            if (delta != null && delta.action() == MemoryIngestionAction.DELETE) {
                deleteCount++;
            }
        }
        return (double) deleteCount / classifications.size();
    }

    private static List<Map<String, Object>> operationSummaries(List<MemoryClassificationResult> classifications) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (MemoryClassificationResult classification : classifications) {
            RefinedMemoryDelta delta = classification.refinedDelta();
            if (delta == null) {
                continue;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("action", delta.action().name());
            summary.put("targetKind", delta.targetKind());
            summary.put("targetKey", delta.targetKey());
            summary.put(METADATA_CONTENT_KEY, operationContent(classification));
            summaries.add(summary);
        }
        return summaries;
    }

    private static String operationContent(MemoryClassificationResult classification) {
        if (classification.decision() != null) {
            return classification.decision().content();
        }
        RefinedMemoryDelta delta = classification.refinedDelta();
        if (delta == null) {
            return "";
        }
        return stringMetadata(delta.metadata(), METADATA_CONTENT_KEY, "");
    }

    private static String stringMetadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value == null || value.toString().isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return value.toString().trim();
    }
}
