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

import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorRouter;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeahorseAgentKernelChatAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SingleKernelExecutorConfiguration.class);

    @Test
    void wrapsSingleExecutorInRouterSoUnsupportedRequestedEngineCannotSilentlyFallBack() {
        contextRunner.run(context -> {
            Optional<ReActExecutorPort> resolved = ReflectionTestUtils.invokeMethod(
                    new SeahorseAgentKernelChatAutoConfiguration(),
                    "resolveReActExecutor",
                    context.getBeanProvider(ReActExecutorPort.class),
                    context.getEnvironment());

            assertThat(resolved).isPresent();
            assertThat(resolved.get()).isInstanceOf(ReActExecutorRouter.class);
            assertThatThrownBy(() -> resolved.get().execute(AgentLoopRequest.builder()
                    .question("route me")
                    .executorEngine("agentscope")
                    .samplingOptions(ChatSamplingOptions.builder().build())
                    .build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No ReActExecutorPort configured for engine: agentscope");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class SingleKernelExecutorConfiguration {

        @Bean
        ReActExecutorPort kernelOnlyExecutor() {
            return new ReActExecutorPort() {
                @Override
                public AgentLoopResult execute(AgentLoopRequest request) {
                    return new AgentLoopResult("kernel", java.util.List.of(), false);
                }

                @Override
                public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
                    if (callback != null) {
                        callback.onContent("kernel");
                        callback.onComplete();
                    }
                    return () -> {
                    };
                }
            };
        }
    }
}
