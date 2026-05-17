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

package com.miracle.ai.seahorse.agent.adapters.vector.milvus;

import java.util.Objects;

/**
 * Milvus adapter 配置契约。
 *
 * @param defaultCollection 默认 collection
 * @param dimension 向量维度
 * @param metricType Milvus 度量类型，默认 COSINE
 * @param contentMaxLength content 字段最大长度
 * @param hnswM HNSW M 参数
 * @param hnswEfConstruction HNSW efConstruction 参数
 * @param mmapEnabled 是否启用 mmap
 * @param searchEf 搜索 ef 参数
 */
public record MilvusVectorProperties(String defaultCollection,
                                     int dimension,
                                     String metricType,
                                     int contentMaxLength,
                                     int hnswM,
                                     int hnswEfConstruction,
                                     boolean mmapEnabled,
                                     int searchEf) {

    private static final int DEFAULT_CONTENT_MAX_LENGTH = 65535;
    private static final int DEFAULT_HNSW_M = 48;
    private static final int DEFAULT_HNSW_EF_CONSTRUCTION = 200;
    private static final int DEFAULT_SEARCH_EF = 128;

    public MilvusVectorProperties(String defaultCollection, int dimension, String metricType) {
        this(defaultCollection, dimension, metricType,
                DEFAULT_CONTENT_MAX_LENGTH, DEFAULT_HNSW_M, DEFAULT_HNSW_EF_CONSTRUCTION, false, DEFAULT_SEARCH_EF);
    }

    public MilvusVectorProperties {
        defaultCollection = Objects.requireNonNullElse(defaultCollection, "");
        metricType = Objects.requireNonNullElse(metricType, "COSINE");
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
        contentMaxLength = positiveOrDefault(contentMaxLength, DEFAULT_CONTENT_MAX_LENGTH);
        hnswM = positiveOrDefault(hnswM, DEFAULT_HNSW_M);
        hnswEfConstruction = positiveOrDefault(hnswEfConstruction, DEFAULT_HNSW_EF_CONSTRUCTION);
        searchEf = positiveOrDefault(searchEf, DEFAULT_SEARCH_EF);
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value <= 0 ? defaultValue : value;
    }
}
