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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResearchInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimitDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseChatControllerRateLimitTests {

    @Test
    void chatRequestAcquiresUserRateLimitPermit() throws Exception {
        RecordingRateLimiter rateLimiter = new RecordingRateLimiter();
        MockMvc mvc = mvc(rateLimiter);

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "hello")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        assertThat(rateLimiter.calls)
                .extracting(Call::resource)
                .contains("user");
        assertThat(rateLimiter.calls)
                .anySatisfy(call -> {
                    assertThat(call.resource()).isEqualTo("user");
                    assertThat(call.subject()).isEqualTo("user-1");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"deep-research", "github-visual-project-intro"})
    void highCostTaskTemplateAcquiresTemplateDailyPermit(String taskTemplateId) throws Exception {
        RecordingRateLimiter rateLimiter = new RecordingRateLimiter();
        MockMvc mvc = mvc(rateLimiter);

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "research")
                        .param("userId", "user-1")
                        .param("taskTemplateId", taskTemplateId))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        assertThat(rateLimiter.calls)
                .anySatisfy(call -> {
                    assertThat(call.resource()).isEqualTo("template");
                    assertThat(call.subject()).isEqualTo(taskTemplateId);
                    assertThat(call.ttl()).isEqualTo(Duration.ofDays(1));
                });
    }

    private static MockMvc mvc(RateLimiterPort rateLimiter) {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);
        SeahorseChatController controller = new SeahorseChatController(
                provider(ChatInboundPort.class, chatPort),
                (emitter, conversationId, taskId) -> new NoopStreamCallback(),
                streamTaskPort,
                provider(AgentRunSnapshotInboundPort.class, null),
                provider(ResearchInboundPort.class, null),
                provider(ResearchSseBridge.class, null),
                provider(RateLimiterPort.class, rateLimiter),
                provider(AgentRunEventBufferPort.class, AgentRunEventBufferPort.noop()),
                provider(AdvancedFeatureGate.class, AdvancedFeatureGate.consumerWebDefaults()),
                1_000L,
                60,
                60_000L);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ObjectProvider<T> provider(Class<T> type, Object bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return (ObjectProvider) factory.getBeanProvider(type);
    }

    private record Call(String resource, String subject, int permits, Duration ttl) {
    }

    private static final class RecordingRateLimiter implements RateLimiterPort {
        private final List<Call> calls = new ArrayList<>();

        @Override
        public RateLimitDecision tryAcquire(String resource, String subject, int permits, Duration ttl) {
            calls.add(new Call(resource, subject, permits, ttl));
            return RateLimitDecision.allowed(permits);
        }
    }

    private static final class NoopStreamCallback implements com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback {

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable error) {
        }
    }
}
