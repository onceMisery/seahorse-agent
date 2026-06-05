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

import com.miracle.ai.seahorse.agent.adapters.web.BCryptPasswordHasherAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.LoggingEmailSenderAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.auth.KernelRegistrationService;
import com.miracle.ai.seahorse.agent.kernel.application.trial.KernelTrialService;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RegistrationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.email.EmailSenderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.tenant.TenantProvisioningPort;
import com.miracle.ai.seahorse.agent.ports.outbound.user.TrialRepositoryPort;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the user registration and trial system.
 *
 * <p>Registers the following beans:
 * <ul>
 *   <li>{@link BCryptPasswordHasherAdapter} — replaces the plain-text hasher</li>
 *   <li>{@link LoggingEmailSenderAdapter} — dev-time email logger</li>
 *   <li>{@link KernelRegistrationService} — registration flow orchestrator</li>
 *   <li>{@link KernelTrialService} — trial query service</li>
 * </ul>
 *
 * <p><b>Registration in AutoConfiguration.imports</b> — add this class
 * <em>before</em> {@code SeahorseAgentAuthAdapterAutoConfiguration} so the
 * BCrypt hasher wins the {@code @ConditionalOnMissingBean} race:
 * <pre>
 * # Layer 0.5: Registration (before Auth adapter for BCrypt priority)
 * com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentRegistrationAutoConfiguration
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(SeahorseAgentAuthAdapterAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentRegistrationAutoConfiguration {

    /**
     * SHA-256 salted password hasher — takes priority over the plain-text
     * fallback defined in {@link SeahorseAgentAuthAdapterAutoConfiguration}.
     */
    @Bean
    @ConditionalOnMissingBean(PasswordHasherPort.class)
    public BCryptPasswordHasherAdapter seahorseBCryptPasswordHasherAdapter() {
        return new BCryptPasswordHasherAdapter();
    }

    /**
     * Development-time email sender that logs instead of sending.
     * Replace with a production adapter (SMTP / API) in real environments.
     */
    @Bean
    @ConditionalOnMissingBean(EmailSenderPort.class)
    public LoggingEmailSenderAdapter seahorseLoggingEmailSenderAdapter() {
        return new LoggingEmailSenderAdapter();
    }

    /**
     * Registration service orchestrating the full sign-up flow.
     */
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

    /**
     * Trial query service.
     */
    @Bean
    @ConditionalOnBean(TrialRepositoryPort.class)
    @ConditionalOnMissingBean(KernelTrialService.class)
    public KernelTrialService seahorseKernelTrialService(TrialRepositoryPort trialRepositoryPort) {
        return new KernelTrialService(trialRepositoryPort);
    }
}
