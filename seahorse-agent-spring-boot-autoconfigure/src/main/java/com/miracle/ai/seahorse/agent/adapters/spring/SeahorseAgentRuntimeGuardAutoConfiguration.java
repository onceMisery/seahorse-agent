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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Spec §7 noop guard 自动装配。
 *
 * <p>Slice 2a 仅注册检测组件并在启动期登记日志，默认不强制 fail-fast。生产环境可通过属性
 * {@code seahorse.agent.runtime.noop-guard.enforce-class-a=true} 在检测到 A 类 noop fallback 时直接抛出。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "seahorse.agent.runtime.noop-guard", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SeahorseAgentRuntimeGuardAutoConfiguration {

    private static final String PROP_ENFORCE_CLASS_A = "seahorse.agent.runtime.noop-guard.enforce-class-a";

    @Bean
    @ConditionalOnMissingBean
    public SeahorseAgentNoopPortGuard seahorseAgentNoopPortGuard(ApplicationContext applicationContext,
                                                                 org.springframework.core.env.Environment environment) {
        boolean enforceClassA = Boolean.parseBoolean(environment.getProperty(PROP_ENFORCE_CLASS_A, "false"));
        return new SeahorseAgentNoopPortGuard(applicationContext, enforceClassA);
    }
}
