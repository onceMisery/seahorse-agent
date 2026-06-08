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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcTenantSchemaUpgrade;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.TenantConnectionPreparer;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Multi-tenancy auto-configuration: registers tenant schema upgrade and connection preparer.
 * <p>
 * Layer 1 — placed after DataSource auto-configuration, before auth adapters.
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentTenantAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(JdbcTenantSchemaUpgrade.class)
    public JdbcTenantSchemaUpgrade seahorseJdbcTenantSchemaUpgrade(DataSource dataSource) {
        JdbcTenantSchemaUpgrade upgrade = new JdbcTenantSchemaUpgrade(dataSource);
        try {
            upgrade.upgrade();
        } catch (Exception e) {
            LoggerFactory.getLogger(SeahorseAgentTenantAutoConfiguration.class)
                    .warn("[Tenant] Schema upgrade failed; continuing with existing schema", e);
        }
        return upgrade;
    }

    @Bean
    @ConditionalOnMissingBean(TenantConnectionPreparer.class)
    public TenantConnectionPreparer seahorseTenantConnectionPreparer() {
        return new TenantConnectionPreparer();
    }
}
