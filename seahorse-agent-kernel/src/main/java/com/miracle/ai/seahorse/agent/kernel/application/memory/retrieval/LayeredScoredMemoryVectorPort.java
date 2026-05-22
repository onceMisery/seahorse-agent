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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorHit;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class LayeredScoredMemoryVectorPort implements ScoredMemoryVectorPort {

    private static final String EMBEDDING_MODEL_LEGACY = "legacy";
    private static final String METADATA_LAYER = "layer";
    private static final String METADATA_TYPE = "type";
    private static final String METADATA_STATUS = "status";
    private static final String METADATA_GENERATION_ID = "generationId";
    private static final String METADATA_UPDATED_AT = "updatedAt";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MemoryVectorPort vectorPort;
    private final ShortTermMemoryPort shortTermPort;
    private final LongTermMemoryPort longTermPort;
    private final SemanticMemoryPort semanticPort;

    public LayeredScoredMemoryVectorPort(MemoryVectorPort vectorPort,
                                         ShortTermMemoryPort shortTermPort,
                                         LongTermMemoryPort longTermPort,
                                         SemanticMemoryPort semanticPort) {
        this.vectorPort = Objects.requireNonNullElseGet(vectorPort, MemoryVectorPort::noop);
        this.shortTermPort = Objects.requireNonNull(shortTermPort, "shortTermPort must not be null");
        this.longTermPort = Objects.requireNonNull(longTermPort, "longTermPort must not be null");
        this.semanticPort = Objects.requireNonNull(semanticPort, "semanticPort must not be null");
    }

    @Override
    public List<ScoredMemoryVectorHit> search(String userId, String tenantId, String query, int topK) {
        List<String> memoryIds = vectorPort.search(userId, query, topK);
        if (memoryIds == null || memoryIds.isEmpty()) {
            return List.of();
        }
        List<ScoredMemoryVectorHit> hits = new ArrayList<>();
        for (String memoryId : memoryIds) {
            if (memoryId == null || memoryId.isBlank()) {
                continue;
            }
            findById(memoryId)
                    .map(this::toHit)
                    .ifPresent(hits::add);
        }
        return List.copyOf(hits);
    }

    private Optional<MemoryRecord> findById(String memoryId) {
        Optional<MemoryRecord> shortTerm = safeFindById(shortTermPort, memoryId);
        if (shortTerm.isPresent()) {
            return shortTerm;
        }
        Optional<MemoryRecord> longTerm = safeFindById(longTermPort, memoryId);
        if (longTerm.isPresent()) {
            return longTerm;
        }
        return safeFindById(semanticPort, memoryId);
    }

    private Optional<MemoryRecord> safeFindById(MemoryStorePort port, String memoryId) {
        try {
            return port.findById(memoryId);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private ScoredMemoryVectorHit toHit(MemoryRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<>(record.metadata());
        metadata.put(METADATA_LAYER, record.layer());
        metadata.put(METADATA_TYPE, record.type());
        metadata.putIfAbsent(METADATA_STATUS, STATUS_ACTIVE);
        metadata.putIfAbsent(METADATA_UPDATED_AT, record.updatedAt().toString());
        String generationId = Objects.toString(metadata.getOrDefault(METADATA_GENERATION_ID, ""), "");
        return new ScoredMemoryVectorHit(record.id(), 0D, generationId, EMBEDDING_MODEL_LEGACY, metadata);
    }
}
