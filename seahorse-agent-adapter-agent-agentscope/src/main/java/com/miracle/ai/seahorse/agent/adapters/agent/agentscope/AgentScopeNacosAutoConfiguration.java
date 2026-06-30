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

import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.ReActAgent;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

@AutoConfiguration
@ConditionalOnClass(ReActAgent.class)
@EnableConfigurationProperties(AgentScopeProperties.class)
public class AgentScopeNacosAutoConfiguration {

    @Bean
    @Conditional(AgentScopeAutoConfigurationSupport.NacosEnabledAndConfiguredCondition.class)
    @ConditionalOnMissingBean
    public NacosPropertiesFactory seahorseAgentScopeNacosPropertiesFactory() {
        return new NacosPropertiesFactory();
    }

    @Bean
    @Conditional(AgentScopeAutoConfigurationSupport.NacosEnabledAndConfiguredCondition.class)
    @ConditionalOnMissingBean
    public AiService seahorseAgentScopeNacosAiService(
            NacosPropertiesFactory nacosPropertiesFactory,
            AgentScopeProperties properties) throws NacosException {
        return AiFactory.createAiService(nacosPropertiesFactory.nacosProperties(properties));
    }
}
