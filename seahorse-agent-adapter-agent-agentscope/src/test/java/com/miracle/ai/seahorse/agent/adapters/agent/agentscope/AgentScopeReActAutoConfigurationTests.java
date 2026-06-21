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

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.application.chat.AgentRunMetadataContributor;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentConnectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import io.a2a.spec.AgentCard;
import io.a2a.spec.TransportProtocol;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.studio.StudioMessageHook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void componentScanDoesNotCreateA2aControllerWithoutA2aServer() {
        contextRunner
                .withUserConfiguration(AgentScopeComponentScanConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AgentScopeA2aServerController.class);
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
    void agentscopeExecutorCanBeRegisteredWithoutBecomingDefaultEngine() {
        contextRunner
                .withUserConfiguration(StreamingModelConfiguration.class)
                .withPropertyValues("seahorse.agentscope.executor.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentScopeModelFactory.class);
                    assertThat(context).hasSingleBean(AgentScopeAgentClient.class);
                    assertThat(context).hasSingleBean(ReActExecutorPort.class);
                    assertThat(context.getBean(ReActExecutorPort.class).engineId()).isEqualTo("agentscope");
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
    void agentscopeExecutorBeanUsesKernelTraceRecorderWhenAvailable() {
        contextRunner
                .withUserConfiguration(ClientConfiguration.class, TraceConfiguration.class)
                .withPropertyValues("seahorse.agent.executor.engine=agentscope")
                .run(context -> {
                    ReActExecutorPort executor = context.getBean(ReActExecutorPort.class);
                    RecordingTraceRepository traceRepository = context.getBean(RecordingTraceRepository.class);
                    RecordingCallback callback = new RecordingCallback();
                    AgentLoopRequest request = AgentLoopRequest.builder()
                            .question("plan")
                            .samplingOptions(ChatSamplingOptions.builder().build())
                            .runId("run-1")
                            .agentId("agent-1")
                            .tenantId("tenant-a")
                            .build();

                    executor.streamExecute(request, callback, TraceRunScope.active("trace-1", Instant.now()));
                    callback.awaitComplete();

                    assertThat(traceRepository.startedNodes)
                            .extracting(RagTraceNode::getNodeName)
                            .containsExactly("agentscope-step");
                    assertThat(traceRepository.finishedNodes)
                            .extracting(RagTraceNodeFinish::status)
                            .containsExactly(KernelRagTraceRecorder.STATUS_SUCCESS);
                });
    }

    @Test
    void agentscopeEngineCreatesStudioHookWhenStudioIsEnabled() {
        contextRunner
                .withUserConfiguration(StreamingModelConfiguration.class)
                .withPropertyValues(
                        "seahorse.agent.executor.engine=agentscope",
                        "seahorse.agentscope.studio.enabled=true",
                        "seahorse.agentscope.studio.auto-initialize=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentScopeStudioLifecycle.class);
                    assertThat(context).hasSingleBean(StudioMessageHook.class);
                    assertThat(context).hasSingleBean(AgentScopeAgentClient.class);
                });
    }

    @Test
    void a2aConnectorCanUseCustomResolverWithoutStartingNacosClient() {
        contextRunner
                .withUserConfiguration(A2aConfiguration.class)
                .withPropertyValues("seahorse.agentscope.a2a.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(A2AAgentConnectorPort.class);
                    assertThat(context).hasSingleBean(AgentScopeA2AToolPortAdapter.class);
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

    @Test
    void configCenterCreatesPromptProviderAndNacosSkillRepositoryWhenEnabled() {
        contextRunner
                .withUserConfiguration(NacosConfiguration.class)
                .withPropertyValues(
                        "seahorse.agentscope.config-center.enabled=true",
                        "seahorse.agentscope.config-center.prompt-key=agent.system.prompt",
                        "seahorse.agentscope.config-center.prompt-version=stable",
                        "seahorse.agentscope.config-center.prompt-label=default",
                        "seahorse.agentscope.config-center.skill-namespace=agent-skills",
                        "seahorse.agentscope.config-center.skill-version=v1",
                        "seahorse.agentscope.config-center.skill-label=stable")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentScopePromptConfigCenter.class);
                    assertThat(context).hasSingleBean(AgentSkillRepository.class);
                    assertThat(context).hasSingleBean(AgentRunMetadataContributor.class);
                    assertThat(context.getBean(AgentSkillRepository.class)).isInstanceOf(NacosSkillRepository.class);
                    assertThat(mapValue(context.getBean(AgentRunMetadataContributor.class).metadata(null), "prompt"))
                            .containsEntry("source", "nacos")
                            .containsEntry("key", "agent.system.prompt")
                            .containsEntry("version", "stable")
                            .containsEntry("label", "default")
                            .containsEntry("revision", "stable");
                });
    }

    @Test
    void a2aRegistrarUsesAiServiceForEndpointDeregisterOnDestroy() throws Exception {
        contextRunner
                .withUserConfiguration(NacosConfiguration.class)
                .withPropertyValues(
                        "seahorse.agentscope.a2a.enabled=true",
                        "seahorse.agentscope.a2a.register-enabled=true",
                        "seahorse.agentscope.a2a.tenant-id=tenant-a",
                        "seahorse.agentscope.a2a.agent-name=planner",
                        "seahorse.agentscope.a2a.version=1.0.0",
                        "seahorse.agentscope.a2a.url=https://runtime.example:9443/a2a?slot=blue",
                        "seahorse.agentscope.a2a.transport=jsonrpc",
                        "seahorse.agentscope.a2a.host=runtime.example",
                        "seahorse.agentscope.a2a.port=9443",
                        "seahorse.agentscope.a2a.path=/a2a",
                        "seahorse.agentscope.a2a.protocol=https",
                        "seahorse.agentscope.a2a.query=slot=blue",
                        "seahorse.agentscope.a2a.support-tls=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentScopeAgentCardRegistrar.class);
                    AgentScopeAgentCardRegistrar registrar = context.getBean(AgentScopeAgentCardRegistrar.class);
                    AiService aiService = context.getBean(AiService.class);

                    registrar.run(null);
                    registrar.destroy();

                    verify(aiService).releaseAgentCard(any(), eq("SERVICE"), eq(false));
                    verify(aiService).registerAgentEndpoint(eq("tenant-a/planner"), any(AgentEndpoint.class));
                    verify(aiService).deregisterAgentEndpoint(eq("tenant-a/planner"), argThat(endpoint ->
                            endpoint != null
                                    && "JSONRPC".equals(endpoint.getTransport())
                                    && "runtime.example".equals(endpoint.getAddress())
                                    && endpoint.getPort() == 9443
                                    && "/a2a".equals(endpoint.getPath())
                                    && endpoint.isSupportTls()
                                    && "1.0.0".equals(endpoint.getVersion())
                                    && "https".equals(endpoint.getProtocol())
                                    && "slot=blue".equals(endpoint.getQuery())));
                });
    }

    @Test
    void strictConfigCenterFailsStartupWhenConfiguredSkillNamespaceIsEmpty() {
        contextRunner
                .withUserConfiguration(StrictConfigCenterConfiguration.class)
                .withPropertyValues(
                        "seahorse.agentscope.config-center.enabled=true",
                        "seahorse.agentscope.config-center.strict-startup=true",
                        "seahorse.agentscope.config-center.prompt-key=agent.system.prompt",
                        "seahorse.agentscope.config-center.skill-namespace=agent-skills")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("strict startup")
                            .hasStackTraceContaining("skill namespace agent-skills is empty or missing");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = AgentScopeA2aServerController.class)
    static class AgentScopeComponentScanConfiguration {
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

                @Override
                public Flux<AgentEvent> stream(AgentLoopRequest request, List<Msg> messages) {
                    return Flux.just(new AgentResultEvent(Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .textContent("ok")
                            .build()));
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TraceConfiguration {
        @Bean
        RecordingTraceRepository recordingTraceRepository() {
            return new RecordingTraceRepository();
        }

        @Bean
        KernelRagTraceRecorder kernelRagTraceRecorder(RecordingTraceRepository traceRepository) {
            return new KernelRagTraceRecorder(traceRepository);
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

    @Configuration(proxyBeanMethods = false)
    static class NacosConfiguration {
        @Bean
        AiService aiService() {
            return mock(AiService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StrictConfigCenterConfiguration {
        @Bean
        AgentScopePromptConfigCenter agentScopePromptConfigCenter() throws Exception {
            AgentScopePromptConfigCenter promptConfigCenter = mock(AgentScopePromptConfigCenter.class);
            when(promptConfigCenter.getPrompt(eq("agent.system.prompt"), eq(Map.of()), eq(null)))
                    .thenReturn("system prompt");
            return promptConfigCenter;
        }

        @Bean
        AgentSkillRepository agentSkillRepository() {
            AgentSkillRepository repository = mock(AgentSkillRepository.class);
            when(repository.getAllSkillNames()).thenReturn(List.of());
            return repository;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Map<String, Object> metadata, String key) {
        assertThat(metadata.get(key)).isInstanceOf(Map.class);
        return (Map<String, Object>) metadata.get(key);
    }

    private static final class RecordingCallback implements StreamCallback {
        private final CountDownLatch terminal = new CountDownLatch(1);

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }

        @Override
        public void onError(Throwable error) {
            terminal.countDown();
        }

        private void awaitComplete() throws InterruptedException {
            assertThat(terminal.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class RecordingTraceRepository implements RagTraceRepositoryPort {
        private final List<RagTraceNode> startedNodes = new ArrayList<>();
        private final List<RagTraceNodeFinish> finishedNodes = new ArrayList<>();

        @Override
        public RagTracePage<RagTraceRun> pageRuns(RagTracePageRequest request) {
            return new RagTracePage<>(1, 10, 0, List.of());
        }

        @Override
        public Optional<RagTraceRun> findRun(String traceId) {
            return Optional.empty();
        }

        @Override
        public List<RagTraceNode> listNodes(String traceId) {
            return List.of();
        }

        @Override
        public void startRun(RagTraceRun run) {
        }

        @Override
        public void finishRun(RagTraceRunFinish finish) {
        }

        @Override
        public void startNode(RagTraceNode node) {
            startedNodes.add(node);
        }

        @Override
        public void finishNode(RagTraceNodeFinish finish) {
            finishedNodes.add(finish);
        }
    }
}
