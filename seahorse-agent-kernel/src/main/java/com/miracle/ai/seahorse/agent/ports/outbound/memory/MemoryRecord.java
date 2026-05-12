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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 统一记忆记录。
 * <p>
 * 该对象用于端口层传递记忆内容，保留层级、类型和元数据，避免把记忆系统误降级为简单 KV。
 *
 * @param id        记忆 ID
 * @param layer     记忆层级
 * @param type      记忆类型
 * @param content   记忆内容
 * @param metadata  元数据
 * @param updatedAt 更新时间
 */
public record MemoryRecord(
        String id,
        String layer,
        String type,
        String content,
        Map<String, Object> metadata,
        Instant updatedAt
) {

    /**
     * 构造不可变记忆记录。
     */
    public MemoryRecord {
        id = Objects.requireNonNullElse(id, "");
        layer = Objects.requireNonNullElse(layer, "");
        type = Objects.requireNonNullElse(type, "");
        content = Objects.requireNonNullElse(content, "");
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        updatedAt = Objects.requireNonNullElse(updatedAt, Instant.EPOCH);
    }
}
