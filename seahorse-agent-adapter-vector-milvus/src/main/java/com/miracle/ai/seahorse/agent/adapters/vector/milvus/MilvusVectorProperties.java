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
 * @param dimension         向量维度
 * @param metricType        Milvus 度量类型，默认 COSINE
 */
public record MilvusVectorProperties(String defaultCollection, int dimension, String metricType) {

    public MilvusVectorProperties {
        defaultCollection = Objects.requireNonNullElse(defaultCollection, "");
        metricType = Objects.requireNonNullElse(metricType, "COSINE");
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
    }
}
