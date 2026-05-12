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

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;

import java.util.List;

/**
 * 向量检索端口。
 * <p>
 * 该端口隔离 Milvus、pgvector 等向量库 SDK。检索 Feature 依赖端口而不是 SDK，
 * 从而支持配置驱动切换向量实现。
 */
public interface VectorSearchPort {

    /**
     * 执行向量检索。
     *
     * @param request 向量检索请求
     * @return 检索到的 Chunk 列表
     */
    List<RetrievedChunk> search(VectorSearchRequest request);
}
