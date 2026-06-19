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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcTaskRepository;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.TaskMapper;
import com.miracle.ai.seahorse.agent.kernel.application.task.InMemoryTaskEventBus;
import com.miracle.ai.seahorse.agent.kernel.application.task.TaskOrchestrationService;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.task.TaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskEventPort;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskRepositoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Task Facade 自动配置。
 * <p>
 * 注册 JdbcTaskRepository（仓储适配器）、InMemoryTaskEventBus（事件总线）
 * 和 TaskOrchestrationService（编排服务）。
 * Controller 由 @RestController 组件扫描自动发现。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentMybatisPlusAutoConfiguration.class,
        SeahorseAgentKernelOpsAutoConfiguration.class,
        SeahorseAgentKernelChatAutoConfiguration.class,
        SeahorseAgentKernelAgentAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentTaskAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskRepositoryPort.class)
    public TaskRepositoryPort seahorseTaskRepository(TaskMapper mapper) {
        return new JdbcTaskRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(TaskEventPort.class)
    public TaskEventPort seahorseTaskEventBus() {
        return new InMemoryTaskEventBus();
    }

    @Bean
    @ConditionalOnMissingBean(TaskInboundPort.class)
    public TaskInboundPort seahorseTaskOrchestrationService(
            TaskRepositoryPort taskRepository,
            ConversationManagementInboundPort conversationPort,
            TaskEventPort eventPort,
            ObjectProvider<ChatInboundPort> chatPort,
            ObjectProvider<AgentRunInboundPort> agentRunPort,
            ObjectProvider<AgentArtifactQueryInboundPort> artifactQueryPort
    ) {
        return new TaskOrchestrationService(
                taskRepository,
                conversationPort,
                chatPort.getIfAvailable(),
                agentRunPort.getIfAvailable(),
                artifactQueryPort.getIfAvailable(),
                eventPort
        );
    }
}
