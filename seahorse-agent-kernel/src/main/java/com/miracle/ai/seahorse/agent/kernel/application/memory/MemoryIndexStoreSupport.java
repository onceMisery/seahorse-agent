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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 记忆存储与索引协作者（从 {@link DefaultMemoryEnginePort} 提取）。
 * 按 §7 收敛原则外提：只负责按目标层落库、写入派生的内存/图索引或投递 outbox、目标层解析。
 */
final class MemoryIndexStoreSupport {

    private static final String METADATA_TARGET_LAYER = "targetLayer";

    private final MemoryLayerStoreRegistry stores;
    private final MemoryDerivedIndexDispatchService derivedIndexDispatch;

    MemoryIndexStoreSupport(MemoryLayerStoreRegistry stores,
                            MemoryDerivedIndexDispatchService derivedIndexDispatch) {
        this.stores = Objects.requireNonNull(stores, "stores must not be null");
        this.derivedIndexDispatch = Objects.requireNonNull(derivedIndexDispatch,
                "derivedIndexDispatch must not be null");
    }

    List<String> indexMemoryOrEnqueueOutbox(MemoryRecord record, String userId, String tenantId) {
        return derivedIndexDispatch.dispatchUpsert(record, userId, tenantId);
    }

    List<String> deleteIndexesOrEnqueueOutbox(String memoryId, String userId, String tenantId) {
        return derivedIndexDispatch.dispatchDelete(memoryId, userId, tenantId);
    }

    String saveMemory(MemoryRecord record, MemoryLayer targetLayer) {
        MemoryLayer safeLayer = targetLayer == null ? MemoryLayer.SHORT_TERM : targetLayer;
        stores.storeFor(safeLayer).save(record);
        return switch (safeLayer) {
            case LONG_TERM -> "LONG_TERM_SAVE";
            case SEMANTIC -> "SEMANTIC_SAVE";
            case WORKING, SHORT_TERM -> "SHORT_TERM_SAVE";
        };
    }

    MemoryLayer targetLayer(MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta != null) {
            Object value = delta.metadata().get(METADATA_TARGET_LAYER);
            if (value != null && !value.toString().isBlank()) {
                return targetLayer(value.toString());
            }
        }
        return MemoryLayer.SHORT_TERM;
    }

    MemoryLayer targetLayer(String layer) {
        if (isBlank(layer)) {
            return MemoryLayer.SHORT_TERM;
        }
        try {
            MemoryLayer parsed = MemoryLayer.valueOf(layer.trim().toUpperCase(Locale.ROOT));
            return parsed == MemoryLayer.WORKING ? MemoryLayer.SHORT_TERM : parsed;
        } catch (IllegalArgumentException ex) {
            return MemoryLayer.SHORT_TERM;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
