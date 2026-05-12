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

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;

import java.util.List;

/**
 * 检索结果后处理 Feature。
 * <p>
 * 后处理器链是核心 RAG 体验的一部分，新架构只把具体策略插件化，
 * 不把后处理顺序、失败跳过和结果传递语义下放给插件自行决定。
 */
public interface SearchResultPostProcessorFeature extends AgentFeature {

    @Override
    default FeatureType type() {
        return FeatureType.SEARCH_RESULT_POST_PROCESSOR;
    }

    /**
     * 判断检索上下文下是否启用。
     *
     * @param context 检索上下文
     * @return true 表示启用
     */
    boolean enabled(SearchContext context);

    /**
     * 处理检索结果。
     *
     * @param chunks  当前 Chunk 列表
     * @param results 原始多通道结果
     * @param context 检索上下文
     * @return 处理后的 Chunk 列表
     */
    List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                 List<SearchChannelResult> results,
                                 SearchContext context);
}
