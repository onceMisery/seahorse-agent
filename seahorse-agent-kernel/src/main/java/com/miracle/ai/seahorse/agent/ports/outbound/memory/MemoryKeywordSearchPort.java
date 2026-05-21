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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface MemoryKeywordSearchPort {

    List<MemoryKeywordHit> search(String userId, String tenantId, String query, int topK);

    static MemoryKeywordSearchPort noop() {
        return (userId, tenantId, query, topK) -> List.of();
    }

    record MemoryKeywordHit(String memoryId, double score, String layer, Map<String, Object> metadata) {

        public MemoryKeywordHit {
            memoryId = Objects.requireNonNullElse(memoryId, "");
            layer = Objects.requireNonNullElse(layer, "");
            metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        }
    }
}
