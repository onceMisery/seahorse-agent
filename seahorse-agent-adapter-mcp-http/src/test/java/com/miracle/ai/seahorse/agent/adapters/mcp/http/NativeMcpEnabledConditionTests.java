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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

class NativeMcpEnabledConditionTests {

    @Test
    void shouldNotMatchWhenMcpIsNotConfigured() {
        NativeMcpEnabledCondition condition = new NativeMcpEnabledCondition();

        Assertions.assertFalse(condition.matches(context(Map.of()), Mockito.mock(AnnotatedTypeMetadata.class)));
    }

    @Test
    void shouldMatchWhenFirstServerUrlExists() {
        NativeMcpEnabledCondition condition = new NativeMcpEnabledCondition();
        Map<String, Object> properties = Map.of("seahorse-agent.adapters.mcp.servers[0].url", "http://127.0.0.1");

        Assertions.assertTrue(condition.matches(context(properties), Mockito.mock(AnnotatedTypeMetadata.class)));
    }

    @Test
    void shouldMatchWhenFirstStdioCommandExists() {
        NativeMcpEnabledCondition condition = new NativeMcpEnabledCondition();
        Map<String, Object> properties = Map.of(
                "seahorse-agent.adapters.mcp.servers[0].transport", "stdio",
                "seahorse-agent.adapters.mcp.servers[0].command", "node");

        Assertions.assertTrue(condition.matches(context(properties), Mockito.mock(AnnotatedTypeMetadata.class)));
    }

    @Test
    void shouldNotMatchWhenExplicitlyDisabled() {
        NativeMcpEnabledCondition condition = new NativeMcpEnabledCondition();
        Map<String, Object> properties = Map.of(
                "seahorse-agent.adapters.mcp.enabled", "false",
                "seahorse-agent.adapters.mcp.servers[0].url", "http://127.0.0.1");

        Assertions.assertFalse(condition.matches(context(properties), Mockito.mock(AnnotatedTypeMetadata.class)));
    }

    private ConditionContext context(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        ConditionContext context = Mockito.mock(ConditionContext.class);
        Mockito.when(context.getEnvironment()).thenReturn(environment);
        return context;
    }
}
