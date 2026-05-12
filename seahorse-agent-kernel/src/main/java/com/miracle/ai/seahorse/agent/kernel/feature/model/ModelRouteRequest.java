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

package com.miracle.ai.seahorse.agent.kernel.feature.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型路由请求。
 * <p>
 * 请求对象封装能力类型、候选模型和上下文属性，避免路由策略直接依赖具体 Provider 配置。
 *
 * @param capability      模型能力，例如 chat、embedding、rerank
 * @param candidateModels 候选模型 ID 列表
 * @param attributes      路由属性
 */
public record ModelRouteRequest(
        String capability,
        List<String> candidateModels,
        Map<String, Object> attributes
) {

    /**
     * 构造不可变路由请求。
     */
    public ModelRouteRequest {
        capability = Objects.requireNonNullElse(capability, "");
        candidateModels = List.copyOf(Objects.requireNonNullElse(candidateModels, List.of()));
        attributes = Map.copyOf(Objects.requireNonNullElse(attributes, Map.of()));
    }
}
