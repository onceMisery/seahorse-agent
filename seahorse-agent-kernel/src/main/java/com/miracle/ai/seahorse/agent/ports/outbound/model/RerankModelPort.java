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

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;

import java.util.List;

/**
 * Rerank 模型端口。
 * <p>
 * 后处理 Feature 通过该端口执行重排，避免直接依赖具体 Rerank Provider。
 */
public interface RerankModelPort {

    /**
     * 对检索结果重排。
     *
     * @param modelId  模型 ID
     * @param query    用户问题
     * @param chunks   待重排 Chunk
     * @return 重排后的 Chunk
     */
    List<RetrievedChunk> rerank(String modelId, String query, List<RetrievedChunk> chunks);

    /**
     * 创建空 Rerank 端口。
     *
     * @return 原样返回候选文档的空实现
     */
    static RerankModelPort noop() {
        return (modelId, query, chunks) -> chunks == null ? List.of() : List.copyOf(chunks);
    }
}
