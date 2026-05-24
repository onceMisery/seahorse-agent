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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.output;

import java.util.Map;
import java.util.Objects;

/**
 * 输出治理请求。
 *
 * @param runId        关联 AgentRun 标识，可为空（一次性调用场景）
 * @param agentId      关联 Agent 定义标识
 * @param tenantId     租户
 * @param userId       用户
 * @param artifactType 期望的 artifact 类型
 * @param schemaJson   JSON Schema 文本，仅 {@link OutputArtifactType#JSON} 时使用
 * @param content      模型实际产生的内容
 * @param attributes   扩展属性，调用方可携带 validator 偏好（不可变）
 */
public record OutputValidationRequest(
        String runId,
        String agentId,
        String tenantId,
        String userId,
        OutputArtifactType artifactType,
        String schemaJson,
        String content,
        Map<String, Object> attributes) {

    public OutputValidationRequest {
        Objects.requireNonNull(artifactType, "artifactType must not be null");
        content = Objects.requireNonNullElse(content, "");
        attributes = Map.copyOf(Objects.requireNonNullElse(attributes, Map.of()));
    }
}
