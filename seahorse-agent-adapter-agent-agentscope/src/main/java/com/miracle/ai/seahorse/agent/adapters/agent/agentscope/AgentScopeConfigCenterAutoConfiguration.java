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

import com.alibaba.nacos.api.ai.AiService;
import com.miracle.ai.seahorse.agent.kernel.application.chat.AgentRunMetadataContributor;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.nacos.prompt.NacosPromptListener;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Properties;

@AutoConfiguration
@AutoConfigureAfter(AgentScopeNacosAutoConfiguration.class)
@ConditionalOnClass(ReActAgent.class)
@EnableConfigurationProperties(AgentScopeProperties.class)
public class AgentScopeConfigCenterAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_CONFIG_CENTER_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean(name = "seahorseAgentScopeRunMetadataContributor")
    public AgentRunMetadataContributor seahorseAgentScopeRunMetadataContributor(AgentScopeProperties properties) {
        return new AgentScopeRunMetadataContributor(properties);
    }

    @Bean
    @ConditionalOnBean(AiService.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_CONFIG_CENTER_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public NacosPromptListener seahorseAgentScopeNacosPromptListener(AiService aiService) {
        return new NacosPromptListener(aiService);
    }

    @Bean
    @ConditionalOnBean(NacosPromptListener.class)
    @ConditionalOnMissingBean
    public AgentScopePromptConfigCenter seahorseAgentScopePromptConfigCenter(
            NacosPromptListener promptListener,
            AgentScopeProperties properties) {
        return new AgentScopePromptConfigCenter(promptListener, properties);
    }

    @Bean
    @ConditionalOnProperty(name = {
            AgentScopeAutoConfigurationSupport.PROP_CONFIG_CENTER_ENABLED,
            AgentScopeAutoConfigurationSupport.PROP_CONFIG_CENTER_STRICT_STARTUP
    }, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeConfigCenterStartupValidator seahorseAgentScopeConfigCenterStartupValidator(
            AgentScopeProperties properties,
            ObjectProvider<AgentScopePromptConfigCenter> promptConfigCenterProvider,
            ObjectProvider<AgentSkillRepository> skillRepositoryProvider) {
        return new AgentScopeConfigCenterStartupValidator(
                properties,
                promptConfigCenterProvider.getIfAvailable(),
                skillRepositoryProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(AiService.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_CONFIG_CENTER_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean(AgentSkillRepository.class)
    public AgentSkillRepository seahorseAgentScopeNacosSkillRepository(
            AiService aiService,
            AgentScopeProperties properties) {
        AgentScopeProperties.ConfigCenter configCenter = properties.getConfigCenter();
        Properties skillProperties = new Properties();
        AgentScopeAutoConfigurationSupport.putIfPresent(
                skillProperties,
                NacosSkillRepository.SKILL_VERSION_PATH,
                configCenter.getSkillVersion());
        AgentScopeAutoConfigurationSupport.putIfPresent(
                skillProperties,
                NacosSkillRepository.SKILL_LABEL_PATH,
                configCenter.getSkillLabel());
        return new NacosSkillRepository(
                aiService,
                AgentScopeAutoConfigurationSupport.firstText(
                        configCenter.getSkillNamespace(),
                        properties.getNacos().getNamespace()),
                skillProperties);
    }
}
