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

import java.util.List;

/**
 * Embedding 模型端口。
 * <p>
 * 检索和记忆向量化通过该端口访问模型，不直接绑定具体 Provider。
 */
public interface EmbeddingModelPort {

    /**
     * 生成文本向量。
     *
     * @param modelId 模型 ID
     * @param text    输入文本
     * @return 向量
     */
    List<Float> embed(String modelId, String text);

    /**
     * 创建空 Embedding 端口。
     *
     * @return 空实现
     */
    static EmbeddingModelPort noop() {
        return (modelId, text) -> List.of();
    }
}
