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

import com.alibaba.nacos.api.exception.NacosException;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import io.agentscope.core.nacos.prompt.NacosPromptListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentScopePromptConfigCenterTests {

    @Test
    void fallsBackToLocalPromptWhenNacosPromptFailsAndStrictStartupIsFalse() throws Exception {
        NacosPromptListener listener = mock(NacosPromptListener.class);
        when(listener.getPrompt(eq("agent.prompt"), eq("v1"), eq("stable"), anyMap(), eq("local prompt")))
                .thenThrow(new NacosException(500, "nacos unavailable"));
        AgentScopeProperties properties = properties(false);
        AgentScopePromptConfigCenter configCenter = new AgentScopePromptConfigCenter(listener, properties);

        String prompt = configCenter.systemPrompt(request(), "local prompt");

        assertThat(prompt).isEqualTo("local prompt");
    }

    @Test
    void throwsClearErrorWhenNacosPromptFailsAndStrictStartupIsTrue() throws Exception {
        NacosPromptListener listener = mock(NacosPromptListener.class);
        when(listener.getPrompt(eq("agent.prompt"), eq("v1"), eq("stable"), anyMap(), eq("local prompt")))
                .thenThrow(new NacosException(500, "nacos unavailable"));
        AgentScopeProperties properties = properties(true);
        AgentScopePromptConfigCenter configCenter = new AgentScopePromptConfigCenter(listener, properties);

        assertThatThrownBy(() -> configCenter.systemPrompt(request(), "local prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strict startup");
    }

    @Test
    void passesPromptVersionLabelAndRunArgumentsToNacos() throws Exception {
        NacosPromptListener listener = mock(NacosPromptListener.class);
        when(listener.getPrompt(eq("agent.prompt"), eq("v1"), eq("stable"), anyMap(), eq("local prompt")))
                .thenReturn("remote prompt");
        AgentScopeProperties properties = properties(false);
        AgentScopePromptConfigCenter configCenter = new AgentScopePromptConfigCenter(listener, properties);

        String prompt = configCenter.systemPrompt(request(), "local prompt");

        assertThat(prompt).isEqualTo("remote prompt");
        verify(listener).getPrompt(eq("agent.prompt"), eq("v1"), eq("stable"), org.mockito.ArgumentMatchers.argThat(
                args -> "run-1".equals(args.get("runId")) && "tenant-a".equals(args.get("tenantId"))),
                eq("local prompt"));
    }

    private AgentScopeProperties properties(boolean strictStartup) {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getConfigCenter().setPromptKey("agent.prompt");
        properties.getConfigCenter().setPromptVersion("v1");
        properties.getConfigCenter().setPromptLabel("stable");
        properties.getConfigCenter().setStrictStartup(strictStartup);
        return properties;
    }

    private AgentLoopRequest request() {
        return AgentLoopRequest.builder()
                .runId("run-1")
                .tenantId("tenant-a")
                .question("draft")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();
    }
}
