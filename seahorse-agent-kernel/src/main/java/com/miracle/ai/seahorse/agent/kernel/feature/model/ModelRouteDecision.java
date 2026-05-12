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
import java.util.Objects;

/**
 * 模型路由决策。
 * <p>
 * 决策保留主选模型和 fallback 链，确保新架构仍能支持现有模型路由失败切换能力。
 *
 * @param primaryModel  主选模型
 * @param fallbackModels fallback 模型列表
 * @param reason        决策原因
 */
public record ModelRouteDecision(String primaryModel, List<String> fallbackModels, String reason) {

    /**
     * 构造不可变路由决策。
     */
    public ModelRouteDecision {
        primaryModel = Objects.requireNonNullElse(primaryModel, "");
        fallbackModels = List.copyOf(Objects.requireNonNullElse(fallbackModels, List.of()));
        reason = Objects.requireNonNullElse(reason, "");
    }
}
