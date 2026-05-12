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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Objects;

/**
 * MCP HTTP adapter 激活条件。
 *
 * <p>默认不注册空的原生 registry，避免挡住 legacy MCP registry。只有显式启用、选择 HTTP 类型，
 * 或配置了远程 server 时才接管 MCP 端口。
 */
public class NativeMcpEnabledCondition implements Condition {

    private static final String KEY_ENABLED = "seahorse-agent.adapters.mcp.enabled";
    private static final String KEY_TYPE = "seahorse-agent.adapters.mcp.type";
    private static final String KEY_FIRST_SERVER_URL = "seahorse-agent.adapters.mcp.servers[0].url";
    private static final String TYPE_HTTP = "http";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = Objects.requireNonNull(context.getEnvironment(), "Environment 不能为空");
        Boolean enabled = environment.getProperty(KEY_ENABLED, Boolean.class);
        if (Boolean.FALSE.equals(enabled)) {
            return false;
        }
        return Boolean.TRUE.equals(enabled)
                || TYPE_HTTP.equalsIgnoreCase(environment.getProperty(KEY_TYPE, ""))
                || hasText(environment.getProperty(KEY_FIRST_SERVER_URL));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
