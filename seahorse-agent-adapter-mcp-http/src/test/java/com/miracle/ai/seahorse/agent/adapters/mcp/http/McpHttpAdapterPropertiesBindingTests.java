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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class McpHttpAdapterPropertiesBindingTests {

    @Test
    void shouldBindPlanTransportNamesForHttpAndStdioServers() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withConfiguration(AutoConfigurations.of(McpHttpAutoConfiguration.class))
                .withPropertyValues(
                        "seahorse-agent.adapters.mcp.enabled=true",
                        "seahorse-agent.adapters.mcp.servers[0].name=http-server",
                        "seahorse-agent.adapters.mcp.servers[0].transport=streamable_http",
                        "seahorse-agent.adapters.mcp.servers[0].url=http://127.0.0.1/mcp",
                        "seahorse-agent.adapters.mcp.servers[1].name=stdio-server",
                        "seahorse-agent.adapters.mcp.servers[1].transport=stdio",
                        "seahorse-agent.adapters.mcp.servers[1].command=node",
                        "seahorse-agent.adapters.mcp.servers[1].args[0]=server.js");

        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(McpHttpAdapterProperties.class);
            McpHttpAdapterProperties properties = context.getBean(McpHttpAdapterProperties.class);

            assertThat(properties.getServers()).hasSize(2);
            assertThat(properties.getServers().get(0).getTransport())
                    .isEqualTo(McpHttpAdapterProperties.Transport.STREAMABLE_HTTP);
            assertThat(properties.getServers().get(1).getTransport())
                    .isEqualTo(McpHttpAdapterProperties.Transport.STDIO);
            assertThat(properties.getServers().get(1).getArgs()).containsExactly("server.js");
        });
    }
}
