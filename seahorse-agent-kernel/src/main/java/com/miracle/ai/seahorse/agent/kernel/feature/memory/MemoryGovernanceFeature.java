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

package com.miracle.ai.seahorse.agent.kernel.feature.memory;

import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;

/**
 * 记忆治理 Feature。
 * <p>
 * 记忆治理属于核心 RAG 能力的增强策略，接口必须保留晋升、衰减、质量快照等语义入口，
 * 避免重构时把记忆系统简化成缓存读写。
 */
public interface MemoryGovernanceFeature extends AgentFeature {

    @Override
    default FeatureType type() {
        return FeatureType.MEMORY_GOVERNANCE;
    }

    /**
     * 执行一次治理动作。
     *
     * @param request 治理请求
     * @return 治理结果
     */
    MemoryGovernanceResult govern(MemoryGovernanceRequest request);
}
