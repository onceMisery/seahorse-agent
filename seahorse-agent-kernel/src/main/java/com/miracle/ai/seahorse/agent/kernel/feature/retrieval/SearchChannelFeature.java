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

package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;

/**
 * 检索通道 Feature。
 * <p>
 * 该接口保留现有 SearchChannel 的扩展语义，并把它纳入微内核注册表统一治理。
 * 通道实现仍只负责一种检索策略，检索编排、失败降级和后处理链保留在 L1 内核。
 */
public interface SearchChannelFeature extends AgentFeature {

    @Override
    default FeatureType type() {
        return FeatureType.SEARCH_CHANNEL;
    }

    /**
     * 检索通道类型。
     *
     * @return 通道类型
     */
    SearchChannelType channelType();

    /**
     * 判断检索上下文下是否启用。
     * <p>
     * 该方法用于保留旧 SearchChannel 的上下文启停能力，例如按知识库、意图或租户跳过通道。
     *
     * @param context 检索上下文
     * @return true 表示启用
     */
    boolean enabled(SearchContext context);

    /**
     * 执行检索。
     *
     * @param context 检索上下文
     * @return 通道检索结果
     */
    SearchChannelResult search(SearchContext context);
}
