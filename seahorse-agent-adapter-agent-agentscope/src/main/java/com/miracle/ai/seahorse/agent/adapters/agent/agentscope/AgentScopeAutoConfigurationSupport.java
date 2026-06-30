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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.net.URI;
import java.util.Properties;

final class AgentScopeAutoConfigurationSupport {

    static final String PROP_EXECUTOR_ENGINE = "seahorse.agent.executor.engine";
    static final String PROP_EXECUTOR_ENABLED = "seahorse.agentscope.executor.enabled";
    static final String PROP_A2A_ENABLED = "seahorse.agentscope.a2a.enabled";
    static final String PROP_CONFIG_CENTER_ENABLED = "seahorse.agentscope.config-center.enabled";
    static final String PROP_CONFIG_CENTER_STRICT_STARTUP = "seahorse.agentscope.config-center.strict-startup";
    static final String PROP_A2A_NACOS_SERVER = "seahorse.agentscope.a2a.nacos-server";
    static final String PROP_NACOS_SERVER = "seahorse.agentscope.nacos.server-addr";

    private AgentScopeAutoConfigurationSupport() {
    }

    static final class AgentScopeExecutorEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return Boolean.TRUE.equals(environment.getProperty(PROP_EXECUTOR_ENABLED, Boolean.class, false))
                    || "agentscope".equalsIgnoreCase(environment.getProperty(PROP_EXECUTOR_ENGINE, ""));
        }
    }

    static final class NacosEnabledAndConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            boolean enabled = Boolean.TRUE.equals(environment.getProperty(PROP_A2A_ENABLED, Boolean.class, false))
                    || Boolean.TRUE.equals(environment.getProperty(PROP_CONFIG_CENTER_ENABLED, Boolean.class, false));
            return enabled && (!isBlank(environment.getProperty(PROP_A2A_NACOS_SERVER))
                    || !isBlank(environment.getProperty(PROP_NACOS_SERVER)));
        }

        private boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }

    static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    static String firstText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? trimToNull(fallback) : trimmed;
    }

    static void putIfPresent(Properties properties, String key, String value) {
        String safeKey = trimToNull(key);
        String safeValue = trimToNull(value);
        if (safeKey != null && safeValue != null) {
            properties.setProperty(safeKey, safeValue);
        }
    }

    static Integer a2aPort(AgentScopeProperties.A2a a2a, URI endpointUri) {
        if (a2a.getPort() > 0) {
            return a2a.getPort();
        }
        if (endpointUri == null) {
            return null;
        }
        if (endpointUri.getPort() > 0) {
            return endpointUri.getPort();
        }
        return "https".equalsIgnoreCase(endpointUri.getScheme()) ? 443 : 80;
    }

    static URI endpointUri(AgentScopeProperties.A2a a2a) {
        String url = trimToNull(a2a.getUrl());
        if (url == null) {
            return null;
        }
        return URI.create(url);
    }

    static boolean isHttps(URI endpointUri) {
        return endpointUri != null && "https".equalsIgnoreCase(endpointUri.getScheme());
    }
}
