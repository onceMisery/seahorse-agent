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

import java.util.Map;
import java.util.Objects;

/**
 * Adapter 健康状态。
 *
 * @param name Adapter 名称
 * @param up 是否健康
 * @param message 状态说明
 * @param details 诊断详情
 */
public record AdapterHealthStatus(String name, boolean up, String message, Map<String, Object> details) {

    public AdapterHealthStatus {
        name = Objects.requireNonNullElse(name, "");
        message = Objects.requireNonNullElse(message, "");
        details = Map.copyOf(Objects.requireNonNullElse(details, Map.of()));
    }

    public static AdapterHealthStatus up(String name) {
        return new AdapterHealthStatus(name, true, "UP", Map.of());
    }

    public static AdapterHealthStatus down(String name, String message) {
        return new AdapterHealthStatus(name, false, message, Map.of());
    }
}

