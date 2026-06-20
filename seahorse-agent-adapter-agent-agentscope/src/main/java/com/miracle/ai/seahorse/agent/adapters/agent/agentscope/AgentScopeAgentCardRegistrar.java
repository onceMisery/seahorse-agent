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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Objects;

public class AgentScopeAgentCardRegistrar implements ApplicationRunner {

    private final NacosA2aRegistry registry;
    private final NacosPropertiesFactory nacosPropertiesFactory;
    private final AgentScopeAgentCardFactory cardFactory;
    private final AgentScopeProperties properties;

    public AgentScopeAgentCardRegistrar(
            NacosA2aRegistry registry,
            NacosPropertiesFactory nacosPropertiesFactory,
            AgentScopeAgentCardFactory cardFactory,
            AgentScopeProperties properties) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.nacosPropertiesFactory = Objects.requireNonNull(nacosPropertiesFactory,
                "nacosPropertiesFactory must not be null");
        this.cardFactory = Objects.requireNonNull(cardFactory, "cardFactory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public void run(ApplicationArguments args) {
        registry.registerAgent(cardFactory.agentCard(properties),
                nacosPropertiesFactory.a2aRegistryProperties(properties));
    }
}
