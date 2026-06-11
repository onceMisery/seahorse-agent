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

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpInterface;
import com.miracle.ai.seahorse.agent.adapters.web.IpApiGeolocationAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.SaTokenCurrentUserAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.SaTokenServiceAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.SeahorseSaTokenStpInterface;
import com.miracle.ai.seahorse.agent.adapters.web.SpringCurrentUserAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcRefreshTokenRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcUserRepositoryAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.IpGeolocationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;

/**
 * 认证与当前用户适配器自动配置。
 *
 * <p>本类只承接认证闭环所需的用户仓储、认证策略和 Web 当前用户桥接，避免把其他 JDBC 仓储混入认证配置。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
    DataSourceAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentAuthAdapterAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(UserRepositoryPort.class)
    public JdbcUserRepositoryAdapter seahorseJdbcUserRepositoryAdapter(DataSource dataSource) {
        return new JdbcUserRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(RefreshTokenRepositoryPort.class)
    public JdbcRefreshTokenRepositoryAdapter seahorseJdbcRefreshTokenRepositoryAdapter(DataSource dataSource) {
        return new JdbcRefreshTokenRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordHasherPort.class)
    public PasswordHasherPort seahorsePasswordHasherPort() {
        return PasswordHasherPort.plainText();
    }

    @Bean
    @ConditionalOnMissingBean(TokenServicePort.class)
    public SaTokenServiceAdapter seahorseSaTokenServiceAdapter() {
        return new SaTokenServiceAdapter();
    }

    @Bean
    @ConditionalOnBean(UserRepositoryPort.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.auth", name = "current-user", havingValue = "sa-token",
            matchIfMissing = true)
    @ConditionalOnMissingBean(CurrentUserPort.class)
    public SaTokenCurrentUserAdapter seahorseSaTokenCurrentUserAdapter(UserRepositoryPort userRepositoryPort) {
        return new SaTokenCurrentUserAdapter(userRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(UserRepositoryPort.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.auth", name = "current-user", havingValue = "spring-header")
    @ConditionalOnMissingBean(CurrentUserPort.class)
    public SpringCurrentUserAdapter seahorseSpringCurrentUserAdapter(UserRepositoryPort userRepositoryPort) {
        return new SpringCurrentUserAdapter(userRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(UserRepositoryPort.class)
    @ConditionalOnMissingBean(StpInterface.class)
    public SeahorseSaTokenStpInterface seahorseSaTokenStpInterface(UserRepositoryPort userRepositoryPort) {
        return new SeahorseSaTokenStpInterface(userRepositoryPort);
    }

    @Bean
    @ConditionalOnMissingBean(IpGeolocationPort.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.auth.geolocation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IpApiGeolocationAdapter seahorseIpGeolocationAdapter() {
        return new IpApiGeolocationAdapter();
    }

    /**
     * sa-token Redis持久化配置.
     * 使用ObjectProvider确保兼容性.
     */
    @Bean
    @ConditionalOnMissingBean(SaTokenDao.class)
    public SaTokenDao saTokenDao(org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.connection.RedisConnectionFactory> factoryProvider) {
        var factory = factoryProvider.getIfAvailable();
        if (factory != null) {
            try {
                org.slf4j.LoggerFactory.getLogger(getClass()).info("创建SaTokenDaoForRedisTemplate，使用RedisConnectionFactory: {}", factory.getClass().getName());
                cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate dao = new cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate();
                dao.init(factory);
                org.slf4j.LoggerFactory.getLogger(getClass()).info("SaTokenDaoForRedisTemplate初始化成功，token将持久化到Redis");
                return dao;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("SaTokenDaoForRedisTemplate创建失败，回退到内存存储: {}", e.getMessage());
            }
        } else {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("RedisConnectionFactory不可用，sa-token使用内存存储（token在重启后丢失）");
        }
        return new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl();
    }
}
