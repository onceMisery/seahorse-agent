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

package com.miracle.ai.seahorse.agent.ports.outbound.vector;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 向量检索请求。
 * <p>
 * 该 DTO 隔离 Milvus、pgvector 等具体实现差异，L2 检索 Feature 只构造统一请求。
 *
 * @param collectionName 集合名称
 * @param query          检索文本
 * @param vector         查询向量
 * @param topK           返回数量
 * @param filters        过滤条件
 */
public record VectorSearchRequest(
        String collectionName,
        String query,
        List<Float> vector,
        int topK,
        Map<String, Object> filters,
        CompiledMetadataFilter compiledFilter
) {

    public VectorSearchRequest(String collectionName,
                               String query,
                               List<Float> vector,
                               int topK,
                               Map<String, Object> filters) {
        this(collectionName, query, vector, topK, filters, CompiledMetadataFilter.empty());
    }

    /**
     * 构造不可变请求。
     */
    public VectorSearchRequest {
        collectionName = Objects.requireNonNullElse(collectionName, "");
        query = Objects.requireNonNullElse(query, "");
        vector = List.copyOf(Objects.requireNonNullElse(vector, List.of()));
        filters = Map.copyOf(Objects.requireNonNullElse(filters, Map.of()));
        compiledFilter = Objects.requireNonNullElseGet(compiledFilter, CompiledMetadataFilter::empty);
    }
}
