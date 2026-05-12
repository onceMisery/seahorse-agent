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

package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;

/**
 * 入库节点 Feature。
 * <p>
 * 该接口保留现有 IngestionNode 的流水线扩展语义，后续可以用桥接适配器接入旧节点。
 * 入库引擎仍负责起始节点判断、环检测、条件跳过和失败中断。
 */
public interface IngestionNodeFeature extends AgentFeature {

    @Override
    default FeatureType type() {
        return FeatureType.INGESTION_NODE;
    }

    /**
     * 节点类型。
     *
     * @return 节点类型标识
     */
    String nodeType();

    /**
     * 执行入库节点。
     *
     * @param context 入库上下文
     * @param config  节点配置
     * @return 节点执行结果
     */
    NodeResult execute(IngestionContext context, NodeConfig config);
}
