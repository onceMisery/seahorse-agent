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

import com.miracle.ai.seahorse.agent.adapters.web.ForbiddenExceptionMapper;
import com.miracle.ai.seahorse.agent.kernel.application.credential.SecretRotationService;
import com.miracle.ai.seahorse.agent.kernel.application.sandbox.SandboxPathValidator;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretMetadataQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWritePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Auto-configuration for the Security Hardening Module (Tasks 2.5-2.6).
 * Registers sandbox path validation, secret rotation, and forbidden exception handling.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SandboxPathValidator.class)
    public SandboxPathValidator seahorseSandboxPathValidator() {
        return new SandboxPathValidator();
    }

    @Bean
    @ConditionalOnBean({SecretStorePort.class, SecretWritePort.class, SecretMetadataQueryPort.class})
    @ConditionalOnMissingBean(SecretRotationService.class)
    public SecretRotationService seahorseSecretRotationService(
            SecretStorePort secretStorePort,
            SecretWritePort secretWritePort,
            SecretMetadataQueryPort secretMetadataQueryPort,
            ObjectProvider<Clock> clockProvider) {
        return new SecretRotationService(
                secretStorePort,
                secretWritePort,
                secretMetadataQueryPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean(ForbiddenExceptionMapper.class)
    public ForbiddenExceptionMapper seahorseForbiddenExceptionMapper() {
        return new ForbiddenExceptionMapper();
    }
}
