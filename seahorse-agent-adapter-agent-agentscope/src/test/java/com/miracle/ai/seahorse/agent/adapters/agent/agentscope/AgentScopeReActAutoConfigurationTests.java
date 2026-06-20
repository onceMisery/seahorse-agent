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

import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentConnectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import io.a2a.spec.AgentCard;
import io.a2a.spec.TransportProtocol;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeReActAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentScopeReActAutoConfiguration.class));

    @Test
    void defaultsDoNotCreateExecutorOrA2aConnector() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ReActExecutorPort.class);
            assertThat(context).doesNotHaveBean(A2AAgentConnectorPort.class);
        });
    }

    @Test
    void defaultsDoNotCreateAgentscopeModelOrClientWhenStreamingModelPortExists() {
        contextRunner
                .withUserConfiguration(StreamingModelConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(Model.class);
                    assertThat(context).doesNotHaveBean(AgentScopeAgentClient.class);
                    assertThat(context).doesNotHaveBean(AgentScopeModelFactory.class);
                });
    }

    @Test
    void agentscopeEngineCreatesClientFromStreamingModelPort() {
        contextRunner
                .withUserConfiguration(StreamingModelConfiguration.class)
                .withPropertyValues("seahorse.agent.executor.engine=agentscope")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentScopeModelFactory.class);
                    assertThat(context).hasSingleBean(AgentScopeAgentClient.class);
                    assertThat(context).hasSingleBean(ReActExecutorPort.class);
                });
    }

    @Test
    void agentscopeEngineCreatesReActExecutorFromClientBean() {
        contextRunner
                .withUserConfiguration(ClientConfiguration.class)
                .withPropertyValues("seahorse.agent.executor.engine=agentscope")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReActExecutorPort.class);
                    assertThat(context.getBean(ReActExecutorPort.class).engineId()).isEqualTo("agentscope");
                });
    }

    @Test
    void a2aConnectorCanUseCustomResolverWithoutStartingNacosClient() {
        contextRunner
                .withUserConfiguration(A2aConfiguration.class)
                .withPropertyValues("seahorse.agentscope.a2a.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(A2AAgentConnectorPort.class);
                    assertThat(context).doesNotHaveBean("seahorseAgentScopeNacosAiService");
                });
    }

    @Test
    void a2aInboundServerUsesLocalExecutorWhenA2aIsEnabled() {
        contextRunner
                .withUserConfiguration(A2aServerConfiguration.class)
                .withPropertyValues(
                        "seahorse.agentscope.a2a.enabled=true",
                        "seahorse.agentscope.a2a.url=http://localhost:8080/a2a")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentScopeA2aServerRunner.class);
                    assertThat(context).hasSingleBean(AgentScopeA2aServerController.class);
                    AgentScopeA2aServer server = context.getBean(AgentScopeA2aServer.class);
                    assertThat(server.getTransportWrapper(TransportProtocol.JSONRPC.asString())).isNotNull();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientConfiguration {
        @Bean
        AgentScopeAgentClient agentScopeAgentClient() {
            return new AgentScopeAgentClient() {
                @Override
                public Msg call(AgentLoopRequest request, List<Msg> messages) {
                    return Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build();
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StreamingModelConfiguration {
        @Bean
        StreamingChatModelPort streamingChatModelPort() {
            return new StreamingChatModelPort() {
                @Override
                public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
                    callback.onContent("ok");
                    callback.onComplete();
                    return () -> { };
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class A2aConfiguration {
        @Bean
        AgentCardResolver agentCardResolver() {
            AgentCard card = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-a", Map.of());
            return agentName -> card;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class A2aServerConfiguration {
        @Bean
        ReActExecutorPort reActExecutorPort() {
            return new ReActExecutorPort() {
                @Override
                public AgentLoopResult execute(AgentLoopRequest request) {
                    return new AgentLoopResult("ok", List.of(), false);
                }

                @Override
                public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
                    callback.onContent("ok");
                    callback.onComplete();
                    return () -> { };
                }
            };
        }
    }
}
