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

package com.miracle.ai.seahorse.agent.ports.outbound.model;

import java.util.List;
import java.util.Objects;

/**
 * 模型路由状态端口。
 *
 * <p>用于把候选模型选择、启停和冷却状态从旧 infra-ai 迁移到 Seahorse 原生端口。
 */
public interface ModelRoutingStatePort {

    String selectModel(String requestedModelId, String capability, List<String> candidates);

    static ModelRoutingStatePort firstAvailable() {
        return (requestedModelId, capability, candidates) -> {
            String requested = Objects.requireNonNullElse(requestedModelId, "").trim();
            if (!requested.isBlank()) {
                return requested;
            }
            return Objects.requireNonNullElse(candidates, List.<String>of()).stream()
                    .filter(model -> model != null && !model.isBlank())
                    .findFirst()
                    .orElse("");
        };
    }
}
