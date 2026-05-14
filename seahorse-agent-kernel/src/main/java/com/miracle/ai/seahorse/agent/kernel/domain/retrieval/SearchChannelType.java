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

package com.miracle.ai.seahorse.agent.kernel.domain.retrieval;

/**
 * seahorse-agent 自有检索通道类型。
 */
public enum SearchChannelType {

    /**
     * 向量全局检索。
     */
    VECTOR_GLOBAL,

    /**
     * 意图定向检索。
     */
    INTENT_DIRECTED,

    /**
     * 关键词检索。
     */
    KEYWORD_ES,

    /**
     * 后端无关的关键词/BM25 检索，实际实现可由 Elasticsearch、PostgreSQL FTS 等 adapter 承担。
     */
    KEYWORD_BM25,

    /**
     * 混合检索。
     */
    HYBRID
}
