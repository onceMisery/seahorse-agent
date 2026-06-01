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

package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import java.util.Map;
import java.util.Objects;

/**
 * 元数据历史回填任务创建命令。
 */
public record MetadataBackfillCommand(
        String tenantId,
        Long knowledgeBaseId,
        String pipelineId,
        int batchSize,
        String operator,
        Map<String, Object> metadata
) {

    public MetadataBackfillCommand {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        pipelineId = Objects.requireNonNullElse(pipelineId, "");
        operator = Objects.requireNonNullElse(operator, "metadata-backfill");
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
    }
}
