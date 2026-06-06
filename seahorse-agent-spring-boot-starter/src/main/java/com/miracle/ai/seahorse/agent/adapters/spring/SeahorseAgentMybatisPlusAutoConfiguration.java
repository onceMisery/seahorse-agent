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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.*;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.*;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.*;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionPlanRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.export.ExportTaskPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * MyBatis Plus 适配器自动配置。
 *
 * <p>在 JDBC 适配器配置之前加载，通过 {@code @ConditionalOnMissingBean} 机制优先使用 MyBatis Plus 实现。
 * 当对应的 Mapper Bean 存在时，自动注册 MyBatis Plus 适配器替换 JdbcTemplate 适配器。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentMybatisPlusAutoConfiguration {

    // ─── Batch 1: Core Business Tables ──────────────────────────────────────

    @Bean
    @ConditionalOnBean(UserMapper.class)
    @ConditionalOnMissingBean(UserRepositoryPort.class)
    public MybatisPlusUserRepositoryAdapter mybatisPlusUserRepositoryAdapter(
            UserMapper mapper, DataSource dataSource) {
        return new MybatisPlusUserRepositoryAdapter(mapper, dataSource);
    }

    @Bean
    @ConditionalOnBean(SubscriptionMapper.class)
    @ConditionalOnMissingBean(SubscriptionRepositoryPort.class)
    public MybatisPlusSubscriptionRepositoryAdapter mybatisPlusSubscriptionRepositoryAdapter(
            SubscriptionMapper mapper, DataSource dataSource) {
        return new MybatisPlusSubscriptionRepositoryAdapter(mapper, dataSource);
    }

    @Bean
    @ConditionalOnBean(SubscriptionPlanMapper.class)
    @ConditionalOnMissingBean(SubscriptionPlanRepositoryPort.class)
    public MybatisPlusSubscriptionPlanRepositoryAdapter mybatisPlusSubscriptionPlanRepositoryAdapter(
            SubscriptionPlanMapper mapper) {
        return new MybatisPlusSubscriptionPlanRepositoryAdapter(mapper);
    }

    @Bean
    @ConditionalOnBean(QuotaPolicyMapper.class)
    @ConditionalOnMissingBean(QuotaPolicyRepositoryPort.class)
    public MybatisPlusQuotaPolicyRepositoryAdapter mybatisPlusQuotaPolicyRepositoryAdapter(
            QuotaPolicyMapper mapper) {
        return new MybatisPlusQuotaPolicyRepositoryAdapter(mapper);
    }

    @Bean
    @ConditionalOnBean(BillMapper.class)
    @ConditionalOnMissingBean(BillRepositoryPort.class)
    public MybatisPlusBillRepositoryAdapter mybatisPlusBillRepositoryAdapter(BillMapper mapper) {
        return new MybatisPlusBillRepositoryAdapter(mapper);
    }

    @Bean
    @ConditionalOnBean(BillLineItemMapper.class)
    @ConditionalOnMissingBean(BillLineItemRepositoryPort.class)
    public MybatisPlusBillLineItemRepositoryAdapter mybatisPlusBillLineItemRepositoryAdapter(
            BillLineItemMapper mapper) {
        return new MybatisPlusBillLineItemRepositoryAdapter(mapper);
    }

    @Bean
    @ConditionalOnBean(PaymentOrderMapper.class)
    @ConditionalOnMissingBean(PaymentOrderRepositoryPort.class)
    public MybatisPlusPaymentOrderRepositoryAdapter mybatisPlusPaymentOrderRepositoryAdapter(
            PaymentOrderMapper mapper, DataSource dataSource) {
        return new MybatisPlusPaymentOrderRepositoryAdapter(mapper, new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnBean(PaymentCallbackLogMapper.class)
    @ConditionalOnMissingBean(PaymentCallbackLogRepositoryPort.class)
    public MybatisPlusPaymentCallbackLogRepositoryAdapter mybatisPlusPaymentCallbackLogRepositoryAdapter(
            PaymentCallbackLogMapper mapper) {
        return new MybatisPlusPaymentCallbackLogRepositoryAdapter(mapper);
    }

    @Bean
    @ConditionalOnBean(ExportTaskMapper.class)
    @ConditionalOnMissingBean(ExportTaskPort.class)
    public MybatisPlusExportTaskAdapter mybatisPlusExportTaskAdapter(ExportTaskMapper mapper) {
        return new MybatisPlusExportTaskAdapter(mapper);
    }

    @Bean
    @ConditionalOnBean(SqlSessionTemplate.class)
    @ConditionalOnMissingBean(ExportTaskPort.class)
    public MybatisPlusExportTaskAdapter mybatisPlusExportTaskAdapterFromSqlSession(
            SqlSessionTemplate sqlSessionTemplate) {
        org.apache.ibatis.session.Configuration configuration =
                sqlSessionTemplate.getConfiguration();
        if (!configuration.hasMapper(ExportTaskMapper.class)) {
            configuration.addMapper(ExportTaskMapper.class);
        }
        return new MybatisPlusExportTaskAdapter(sqlSessionTemplate.getMapper(ExportTaskMapper.class));
    }
}
