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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 入库任务创建持久化值对象。
 */
public record IngestionTaskCreateValues(
        String pipelineId,
        String sourceType,
        String sourceLocation,
        String sourceFileName,
        int pipelineVersion,
        Map<String, Object> pipelineSnapshot,
        String operator
) {

    public IngestionTaskCreateValues(String pipelineId,
                                     String sourceType,
                                     String sourceLocation,
                                     String sourceFileName,
                                     String operator) {
        this(pipelineId, sourceType, sourceLocation, sourceFileName, 0, Map.of(), operator);
    }

    public IngestionTaskCreateValues {
        pipelineId = Objects.requireNonNullElse(pipelineId, "");
        sourceType = Objects.requireNonNullElse(sourceType, "");
        sourceLocation = Objects.requireNonNullElse(sourceLocation, "");
        sourceFileName = Objects.requireNonNullElse(sourceFileName, "");
        pipelineVersion = Math.max(0, pipelineVersion);
        pipelineSnapshot = new LinkedHashMap<>(Objects.requireNonNullElse(pipelineSnapshot, Map.of()));
        operator = Objects.requireNonNullElse(operator, "");
    }
}
