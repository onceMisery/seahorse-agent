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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Registers middleware health indicators for Spring Boot Actuator's {@code /actuator/health} endpoint.
 * <p>
 * Each contributor performs a direct connectivity check against its respective middleware:
 * <ul>
 *   <li><b>PostgreSQL</b>: {@code connection.isValid(3)} via DataSource</li>
 * </ul>
 * Additional contributors (Redis, Milvus, ES) can be added when their client libraries are available.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HealthIndicator.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class SeahorseAgentMiddlewareHealthAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SeahorseAgentMiddlewareHealthAutoConfiguration.class);

    /**
     * PostgreSQL health indicator: validates database connectivity using {@code Connection.isValid()}.
     */
    @Bean("seahorsePostgresHealth")
    @ConditionalOnBean(DataSource.class)
    public HealthIndicator seahorsePostgresHealthIndicator(DataSource dataSource) {
        return () -> {
            try (Connection conn = dataSource.getConnection()) {
                boolean valid = conn.isValid(3);
                if (valid) {
                    return Health.up()
                            .withDetail("database", conn.getMetaData().getDatabaseProductName())
                            .withDetail("version", conn.getMetaData().getDatabaseProductVersion())
                            .build();
                }
                return Health.down().withDetail("reason", "Connection.isValid() returned false").build();
            } catch (Exception e) {
                log.warn("[Health] PostgreSQL connectivity check failed", e);
                return Health.down().withException(e).build();
            }
        };
    }
}
