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

import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;

/**
 * 模型路由策略 Feature。
 * <p>
 * 路由策略可插拔，但模型调用 fallback、健康状态和候选链编排仍由内核服务统一执行。
 */
public interface ModelRoutingPolicyFeature extends AgentFeature {

    @Override
    default FeatureType type() {
        return FeatureType.MODEL_ROUTING_POLICY;
    }

    /**
     * 生成模型路由决策。
     *
     * @param request 路由请求
     * @return 路由决策
     */
    ModelRouteDecision route(ModelRouteRequest request);
}
