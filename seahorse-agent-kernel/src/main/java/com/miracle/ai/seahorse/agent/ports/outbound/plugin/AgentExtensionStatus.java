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

package com.miracle.ai.seahorse.agent.ports.outbound.plugin;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 插件启用状态快照。
 *
 * @param name 插件名称
 * @param portType 端口类型
 * @param featureType Feature 类型
 * @param version 插件版本
 * @param enabled 是否启用
 * @param healthy 是否健康
 * @param capabilities 能力标签
 * @param message 状态说明
 * @param lastError 最后错误
 * @param details 诊断详情
 * @param updatedBy 更新人
 * @param updatedAt 更新时间
 */
public record AgentExtensionStatus(
        String name,
        String portType,
        String featureType,
        String version,
        boolean enabled,
        boolean healthy,
        Set<String> capabilities,
        String message,
        String lastError,
        Map<String, Object> details,
        String updatedBy,
        Instant updatedAt
) {

    public AgentExtensionStatus {
        name = Objects.requireNonNullElse(name, "");
        portType = Objects.requireNonNullElse(portType, "");
        featureType = Objects.requireNonNullElse(featureType, "");
        version = Objects.requireNonNullElse(version, "");
        capabilities = Set.copyOf(Objects.requireNonNullElse(capabilities, Set.of()));
        message = Objects.requireNonNullElse(message, "");
        lastError = Objects.requireNonNullElse(lastError, "");
        details = Map.copyOf(Objects.requireNonNullElse(details, Map.of()));
        updatedBy = Objects.requireNonNullElse(updatedBy, "");
        updatedAt = Objects.requireNonNullElse(updatedAt, Instant.EPOCH);
    }
}

