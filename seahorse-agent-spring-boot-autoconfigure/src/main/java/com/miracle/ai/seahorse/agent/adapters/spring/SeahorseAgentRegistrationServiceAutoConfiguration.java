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

import com.miracle.ai.seahorse.agent.kernel.application.auth.KernelRegistrationService;
import com.miracle.ai.seahorse.agent.kernel.application.trial.KernelTrialService;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RegistrationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.email.EmailSenderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.tenant.TenantProvisioningPort;
import com.miracle.ai.seahorse.agent.ports.outbound.user.TrialRepositoryPort;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for registration kernel services.
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentRegistrationAutoConfiguration.class,
        SeahorseAgentAuthAdapterAutoConfiguration.class,
        SeahorseAgentTenantAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentRegistrationServiceAutoConfiguration {

    @Bean
    @ConditionalOnBean({
            UserRepositoryPort.class,
            TokenServicePort.class,
            EmailSenderPort.class,
            TenantProvisioningPort.class,
            TrialRepositoryPort.class,
            PasswordHasherPort.class
    })
    @ConditionalOnMissingBean(RegistrationInboundPort.class)
    public KernelRegistrationService seahorseRegistrationInboundPort(
            UserRepositoryPort userRepositoryPort,
            TokenServicePort tokenServicePort,
            EmailSenderPort emailSenderPort,
            TenantProvisioningPort tenantProvisioningPort,
            TrialRepositoryPort trialRepositoryPort,
            PasswordHasherPort passwordHasherPort) {
        return new KernelRegistrationService(
                userRepositoryPort, tokenServicePort, emailSenderPort,
                tenantProvisioningPort, trialRepositoryPort, passwordHasherPort);
    }

    @Bean
    @ConditionalOnBean(TrialRepositoryPort.class)
    @ConditionalOnMissingBean(KernelTrialService.class)
    public KernelTrialService seahorseKernelTrialService(TrialRepositoryPort trialRepositoryPort) {
        return new KernelTrialService(trialRepositoryPort);
    }
}
