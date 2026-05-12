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

package com.miracle.ai.seahorse.agent.kernel.plugin;

/**
 * Feature 类型枚举。
 * <p>
 * 类型定义了微内核认可的稳定扩展点。新增业务扩展时应优先复用这些类型，
 * 只有出现新的主干能力时才扩展枚举，避免无限制插件化导致核心能力空心化。
 */
public enum FeatureType {

    /**
     * 检索通道，例如全局向量检索、意图定向检索、关键词检索。
     */
    SEARCH_CHANNEL,

    /**
     * 检索结果后处理，例如去重、版本过滤、Rerank。
     */
    SEARCH_RESULT_POST_PROCESSOR,

    /**
     * 入库流水线节点，例如解析、分块、增强、索引。
     */
    INGESTION_NODE,

    /**
     * MCP 工具扩展。
     */
    MCP_TOOL,

    /**
     * 记忆治理策略，例如晋升、衰减、质量快照。
     */
    MEMORY_GOVERNANCE,

    /**
     * 模型路由策略，例如优先级、健康状态和 fallback。
     */
    MODEL_ROUTING_POLICY,

    /**
     * 观测包装器，例如 tracing、metrics、审计。
     */
    OBSERVATION_WRAPPER
}
