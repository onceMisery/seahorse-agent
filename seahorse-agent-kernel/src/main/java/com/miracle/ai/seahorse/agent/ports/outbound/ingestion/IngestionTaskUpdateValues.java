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

package com.miracle.ai.seahorse.agent.ports.outbound.ingestion;

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeLog;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 入库任务执行结果持久化值对象。
 */
public record IngestionTaskUpdateValues(
        String status,
        int chunkCount,
        String errorMessage,
        List<NodeLog> logs,
        Map<String, Object> metadata,
        String operator
) {

    public IngestionTaskUpdateValues {
        status = Objects.requireNonNullElse(status, "");
        logs = List.copyOf(Objects.requireNonNullElse(logs, List.of()));
        metadata = new LinkedHashMap<>(Objects.requireNonNullElse(metadata, Map.of()));
        operator = Objects.requireNonNullElse(operator, "");
    }
}
