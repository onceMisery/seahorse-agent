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

package com.miracle.ai.seahorse.agent.adapters.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Seahorse 原生 L3 adapter 自动配置入口。
 *
 * <p>该类只保留外部兼容入口，所有子配置已注册在 AutoConfiguration.imports 中作为独立自动配置类，
 * 通过 @AutoConfigureAfter 控制加载顺序，避免 @Import 导致 @ConditionalOnBean 提前求值。
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        SeahorseAgentAiAdapterAutoConfiguration.class,
        SeahorseAgentAuthAdapterAutoConfiguration.class,
        SeahorseAgentCacheAdapterAutoConfiguration.class,
        SeahorseAgentIngestionRepositoryAutoConfiguration.class,
        SeahorseAgentKeywordAdapterAutoConfiguration.class,
        SeahorseAgentKnowledgeRepositoryAutoConfiguration.class,
        SeahorseAgentLocalAdapterAutoConfiguration.class,
        SeahorseAgentMemoryRepositoryAutoConfiguration.class,
        SeahorseAgentMetadataAdapterAutoConfiguration.class,
        SeahorseAgentMqAdapterAutoConfiguration.class,
        SeahorseAgentObservationAdapterAutoConfiguration.class,
        SeahorseAgentOpsRepositoryAutoConfiguration.class,
        SeahorseAgentOutboxRelayAutoConfiguration.class,
        SeahorseAgentRetrievalRepositoryAutoConfiguration.class,
        SeahorseAgentRegistryRepositoryAutoConfiguration.class,
        SeahorseAgentStorageAdapterAutoConfiguration.class,
        SeahorseAgentVectorAdapterAutoConfiguration.class
})
public class SeahorseAgentNativeAdapterAutoConfiguration {
}
