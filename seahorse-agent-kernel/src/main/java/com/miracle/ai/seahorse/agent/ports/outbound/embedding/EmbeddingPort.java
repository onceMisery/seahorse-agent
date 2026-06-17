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

package com.miracle.ai.seahorse.agent.ports.outbound.embedding;

import java.util.List;

/**
 * Embedding 服务接口：将文本转换为语义向量。
 *
 * <p>支持单文本和批量文本的向量化，用于语义相似度计算。
 *
 * <p>实现建议：
 * <ul>
 *   <li>OpenAI Embeddings API</li>
 *   <li>本地 sentence-transformers 模型</li>
 *   <li>通义千问 Embeddings</li>
 *   <li>智谱 AI Embeddings</li>
 * </ul>
 */
public interface EmbeddingPort {

    /**
     * 将单个文本转换为向量。
     *
     * @param text 输入文本
     * @return 向量表示（通常为 768 或 1536 维）
     */
    float[] embed(String text);

    /**
     * 批量将文本转换为向量。
     *
     * @param texts 输入文本列表
     * @return 向量列表，顺序与输入一致
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取向量维度。
     *
     * @return 向量维度（如 768, 1536）
     */
    int dimension();

    /**
     * 获取模型名称。
     *
     * @return 模型标识符（如 "text-embedding-3-small", "paraphrase-multilingual-mpnet-base-v2"）
     */
    String modelName();

    /**
     * No-op 实现，用于未启用 Embedding 服务时的降级。
     */
    static EmbeddingPort noop() {
        return new EmbeddingPort() {
            @Override
            public float[] embed(String text) {
                return new float[0];
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                return List.of();
            }

            @Override
            public int dimension() {
                return 0;
            }

            @Override
            public String modelName() {
                return "noop";
            }
        };
    }
}
