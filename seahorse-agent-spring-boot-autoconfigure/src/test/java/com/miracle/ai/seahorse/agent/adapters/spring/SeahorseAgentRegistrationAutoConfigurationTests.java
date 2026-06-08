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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.DefaultTenantProvisioningAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcTrialRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.BCryptPasswordHasherAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.auth.KernelRegistrationService;
import com.miracle.ai.seahorse.agent.kernel.application.trial.KernelTrialService;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RegistrationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.tenant.TenantProvisioningPort;
import com.miracle.ai.seahorse.agent.ports.outbound.user.TrialRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SeahorseAgentRegistrationAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SeahorseAgentRegistrationAutoConfiguration.class,
                    SeahorseAgentAuthAdapterAutoConfiguration.class,
                    SeahorseAgentRegistrationServiceAutoConfiguration.class))
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void createsRegistrationServiceWhenDefaultRegistrationAdaptersAreAvailable() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(BCryptPasswordHasherAdapter.class);
            assertThat(context).hasSingleBean(PasswordHasherPort.class);
            assertThat(context).hasSingleBean(DefaultTenantProvisioningAdapter.class);
            assertThat(context).hasSingleBean(TenantProvisioningPort.class);
            assertThat(context).hasSingleBean(JdbcTrialRepositoryAdapter.class);
            assertThat(context).hasSingleBean(TrialRepositoryPort.class);
            assertThat(context).hasSingleBean(KernelRegistrationService.class);
            assertThat(context).hasSingleBean(RegistrationInboundPort.class);
            assertThat(context).hasSingleBean(KernelTrialService.class);
        });
    }
}
