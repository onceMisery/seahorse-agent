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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.AesGcmSecretValueCipher;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSecretStoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.SecretValueCipher;
import com.miracle.ai.seahorse.agent.kernel.application.credential.KernelSecretManagementService;
import com.miracle.ai.seahorse.agent.ports.inbound.credential.SecretManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.InMemoryOAuthTokenCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.OAuthCredentialProvider;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.OAuthTokenCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.OAuthTokenPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWritePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        DataSourceAutoConfiguration.class,
        SeahorseAgentAuthAdapterAutoConfiguration.class,
        SeahorseAgentKernelAuthAutoConfiguration.class
})
@ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SeahorseAgentCredentialAutoConfiguration {

    @Bean
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.credentials.jdbc", name = "aes-key-base64")
    @ConditionalOnMissingBean(SecretValueCipher.class)
    public AesGcmSecretValueCipher seahorseSecretValueCipher(
            @Value("${seahorse-agent.credentials.jdbc.aes-key-base64:${seahorse.agent.credentials.jdbc.aes-key-base64:}}")
            String aesKeyBase64) {
        return AesGcmSecretValueCipher.fromBase64Key(aesKeyBase64);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, SecretValueCipher.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc",
            matchIfMissing = true)
    @ConditionalOnMissingBean({SecretStorePort.class, SecretWritePort.class})
    public JdbcSecretStoreAdapter seahorseJdbcSecretStoreAdapter(DataSource dataSource, SecretValueCipher cipher) {
        return new JdbcSecretStoreAdapter(dataSource, cipher);
    }

    @Bean
    @ConditionalOnBean(OAuthTokenPort.class)
    @ConditionalOnMissingBean(OAuthTokenCachePort.class)
    public InMemoryOAuthTokenCachePort seahorseInMemoryOAuthTokenCachePort(ObjectProvider<Clock> clockProvider) {
        return new InMemoryOAuthTokenCachePort(clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({SecretStorePort.class, OAuthTokenPort.class, OAuthTokenCachePort.class})
    @ConditionalOnMissingBean(CredentialProviderPort.class)
    public OAuthCredentialProvider seahorseOAuthCredentialProvider(
            SecretStorePort secretStorePort,
            OAuthTokenPort oauthTokenPort,
            OAuthTokenCachePort oauthTokenCachePort,
            ObjectProvider<Clock> clockProvider) {
        return new OAuthCredentialProvider(
                secretStorePort,
                oauthTokenPort,
                oauthTokenCachePort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({SecretWritePort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(SecretManagementInboundPort.class)
    public KernelSecretManagementService seahorseSecretManagementInboundPort(
            SecretWritePort secretWritePort,
            CurrentUserPort currentUserPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelSecretManagementService(
                secretWritePort,
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }
}
