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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spec §7 noop guard 自动装配。
 *
 * <p>A 类端口（丢写入 / 跳过审计 / 跳过关键持久化）默认强制 fail-fast：缺失或绑定到 noop fallback 时
 * 启动即失败。此前默认值取决于 {@code seahorse-agent.product-mode}——只有 {@code rag} / {@code enterprise}
 * 会强制，而该属性默认为 {@code demo}，等于给 demo 场景长期保留了静默降级。现在不再按 product-mode 豁免，
 * 需要保留旧行为时显式设置 {@code seahorse-agent.runtime.noop-guard.enforce-class-a=false}。
 */
@AutoConfiguration
@ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.runtime.noop-guard", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SeahorseAgentRuntimeGuardAutoConfiguration {

    private static final String PROP_ENFORCE_CLASS_A = "seahorse-agent.runtime.noop-guard.enforce-class-a";
    private static final String LEGACY_PROP_ENFORCE_CLASS_A = "seahorse.agent.runtime.noop-guard.enforce-class-a";

    @Bean
    @ConditionalOnMissingBean
    public SeahorseAgentNoopPortGuard seahorseAgentNoopPortGuard(ApplicationContext applicationContext,
                                                                 Environment environment) {
        String explicit = environment.getProperty(PROP_ENFORCE_CLASS_A);
        if (explicit == null) {
            explicit = environment.getProperty(LEGACY_PROP_ENFORCE_CLASS_A);
        }
        boolean enforceClassA = explicit == null || Boolean.parseBoolean(explicit);
        return new SeahorseAgentNoopPortGuard(applicationContext, enforceClassA);
    }
}
