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
 * 模型 Provider 端口。
 * <p>
 * 该端口用于查询 Provider 支持的模型和健康状态，供模型路由内核生成候选链。
 */
public interface ModelProviderPort {

    /**
     * 判断模型是否可用。
     *
     * @param modelId 模型 ID
     * @return true 表示可用
     */
    boolean available(String modelId);

    /**
     * 列出指定能力的候选模型。
     *
     * @param capability 模型能力
     * @return 模型 ID 列表
     */
    List<String> listModels(String capability);

    /**
     * 创建空 Provider 端口。
     *
     * @return 空实现
     */
    static ModelProviderPort noop() {
        return new ModelProviderPort() {
            @Override
            public boolean available(String modelId) {
                return false;
            }

            @Override
            public List<String> listModels(String capability) {
                return List.of();
            }
        };
    }
}
