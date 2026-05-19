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

import com.miracle.ai.seahorse.agent.kernel.application.auth.KernelAuthService;
import com.miracle.ai.seahorse.agent.kernel.application.user.KernelUserService;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证与用户内核自动配置。
 *
 * <p>认证入口与用户管理入口共享用户仓储和密码能力，单独配置后认证职责不再混在主 kernel 装配类中。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(SeahorseAgentAuthAdapterAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelAuthAutoConfiguration {

    @Bean
    @ConditionalOnBean({UserRepositoryPort.class, PasswordHasherPort.class, TokenServicePort.class})
    @ConditionalOnMissingBean(AuthInboundPort.class)
    public KernelAuthService seahorseAuthInboundPort(UserRepositoryPort userRepositoryPort,
                                                     PasswordHasherPort passwordHasherPort,
                                                     TokenServicePort tokenServicePort) {
        return new KernelAuthService(userRepositoryPort, passwordHasherPort, tokenServicePort);
    }

    @Bean
    @ConditionalOnBean({UserRepositoryPort.class, PasswordHasherPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(UserInboundPort.class)
    public KernelUserService seahorseUserInboundPort(UserRepositoryPort userRepositoryPort,
                                                     PasswordHasherPort passwordHasherPort,
                                                     CurrentUserPort currentUserPort) {
        return new KernelUserService(userRepositoryPort, passwordHasherPort, currentUserPort);
    }
}
