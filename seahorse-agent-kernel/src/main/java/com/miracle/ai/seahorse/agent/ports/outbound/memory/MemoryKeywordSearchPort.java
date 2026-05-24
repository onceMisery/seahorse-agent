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

import com.miracle.ai.seahorse.agent.ports.common.NoopFallback;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface MemoryKeywordSearchPort {

    List<MemoryKeywordHit> search(String userId, String tenantId, String query, int topK);

    static MemoryKeywordSearchPort noop() {
        return new NoopMemoryKeywordSearchPort();
    }

    record MemoryKeywordHit(String memoryId, double score, String layer, Map<String, Object> metadata) {

        public MemoryKeywordHit {
            memoryId = Objects.requireNonNullElse(memoryId, "");
            layer = Objects.requireNonNullElse(layer, "");
            metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        }
    }

    /**
     * 显式 noop fallback：命中关键字索引降级路径。
     *
     * <p>实现 {@link NoopFallback} 以便 {@code SeahorseAgentNoopPortGuard} 区分真实实现与
     * 兜底降级；Class B（索引增强）允许保留但启动期会发出 WARN + metric。
     */
    final class NoopMemoryKeywordSearchPort implements MemoryKeywordSearchPort, NoopFallback {

        @Override
        public List<MemoryKeywordHit> search(String userId, String tenantId, String query, int topK) {
            return List.of();
        }
    }
}
