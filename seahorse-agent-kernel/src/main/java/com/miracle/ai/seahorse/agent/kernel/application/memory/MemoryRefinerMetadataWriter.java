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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;

import java.util.Map;

/**
 * Slice 3 续 cut 8：refiner 决策结果到 metadata 的写入器 + memoryId 构造器。
 *
 * <p>原 facade 私有方法 {@code addRefinedMetadata} / {@code refinerStatus} /
 * {@code memoryId} / {@code refinerOperationSuffix} 合并到本 service，对外暴露：
 * <ul>
 *     <li>{@link #appendRefined(Map, MemoryClassificationResult)} —
 *         把 refined delta 的 status/action/reason/targetKind/targetKey 与 metadata
 *         深合入 capture metadata；遇 {@code refinerBatch} key 跳过避免循环；
 *         decision policy 为 {@code llm_refiner_v1} 时盖戳 {@code capturePolicy=llm_refiner}。</li>
 *     <li>{@link #buildMemoryId(MemoryWriteRequest, MemoryClassificationResult)} —
 *         {@code stm-<messageId|snowflakeId>}，多 op batch 时附 {@code -r<index>} 后缀。</li>
 * </ul>
 *
 * <p>所有 metadata key 字面量与 facade 完全一致，下游消费者无感知变更。
 */
public final class MemoryRefinerMetadataWriter {

    private static final String METADATA_REFINER_BATCH = "refinerBatch";
    private static final String METADATA_REFINER_OPERATION_COUNT = "refinerOperationCount";
    private static final String METADATA_REFINER_OPERATION_INDEX = "refinerOperationIndex";

    private static final String LLM_REFINER_POLICY_VERSION = "llm_refiner_v1";
    private static final String LLM_REFINER_CAPTURE_POLICY = "llm_refiner";

    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_FAILED_OPEN = "failed_open";
    private static final String STATUS_IGNORED = "ignored";
    private static final String FAILED_OPEN_REASON_PREFIX = "failed_open";

    private static final String MEMORY_ID_PREFIX = "stm-";

    /**
     * 在 capture metadata 上追加 refined delta 的字段；当 classification 为空或 delta 无效时 no-op。
     */
    public void appendRefined(Map<String, Object> metadata, MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta == null || delta.action() == MemoryIngestionAction.IGNORE && isBlank(delta.reason())) {
            return;
        }
        metadata.put("refinerStatus", refinerStatus(delta));
        metadata.put("refinerAction", delta.action().name());
        metadata.put("refinerReason", delta.reason());
        metadata.put("targetKind", delta.targetKind());
        metadata.put("targetKey", delta.targetKey());
        for (Map.Entry<String, Object> entry : delta.metadata().entrySet()) {
            if (METADATA_REFINER_BATCH.equals(entry.getKey())) {
                continue;
            }
            metadata.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (classification != null && classification.decision() != null
                && LLM_REFINER_POLICY_VERSION.equals(classification.decision().policyVersion())) {
            metadata.put("capturePolicy", LLM_REFINER_CAPTURE_POLICY);
        }
    }

    /**
     * 构造 memoryId：{@code stm-<messageId|snowflakeId>(-r<index> if batch)}。
     */
    public String buildMemoryId(MemoryWriteRequest request, MemoryClassificationResult classification) {
        String suffix = refinerOperationSuffix(classification);
        if (!isBlank(request.messageId())) {
            return MEMORY_ID_PREFIX + request.messageId() + suffix;
        }
        return MEMORY_ID_PREFIX + SnowflakeIds.nextIdString() + suffix;
    }

    private static String refinerStatus(RefinedMemoryDelta delta) {
        Object status = delta.metadata().get("status");
        if (status != null && !status.toString().isBlank()) {
            return status.toString();
        }
        if (delta.action() == MemoryIngestionAction.ADD) {
            return STATUS_ENABLED;
        }
        return delta.reason().startsWith(FAILED_OPEN_REASON_PREFIX) ? STATUS_FAILED_OPEN : STATUS_IGNORED;
    }

    private static String refinerOperationSuffix(MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta == null) {
            return "";
        }
        Object count = delta.metadata().get(METADATA_REFINER_OPERATION_COUNT);
        if (!(count instanceof Number number) || number.intValue() <= 1) {
            return "";
        }
        Object index = delta.metadata().get(METADATA_REFINER_OPERATION_INDEX);
        if (!(index instanceof Number indexNumber)) {
            return "";
        }
        return "-r" + indexNumber.intValue();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
