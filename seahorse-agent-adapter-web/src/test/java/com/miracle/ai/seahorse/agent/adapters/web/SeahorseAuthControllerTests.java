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

import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthRefreshInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.LoginCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.LoginResult;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RefreshTokenCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RefreshTokenResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseAuthControllerTests {

    @Test
    void shouldLoginAndReturnToken() throws Exception {
        AuthInboundPort port = mock(AuthInboundPort.class);
        when(port.login(any())).thenReturn(new LoginResult("user-1", "admin", "token-abc", "avatar.png"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAuthController(provider(AuthInboundPort.class, port))).build();

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.token").value("token-abc"));

        ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(port).login(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("admin");
        assertThat(captor.getValue().password()).isEqualTo("secret");
    }

    @Test
    void shouldLogout() throws Exception {
        AuthInboundPort port = mock(AuthInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAuthController(provider(AuthInboundPort.class, port))).build();

        mvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).logout();
    }

    @Test
    void shouldRefreshToken() throws Exception {
        AuthInboundPort authPort = mock(AuthInboundPort.class);
        AuthRefreshInboundPort refreshPort = mock(AuthRefreshInboundPort.class);
        when(refreshPort.refresh(any())).thenReturn(new RefreshTokenResult(
                "user-1", "admin", "token-next", "refresh-next",
                Instant.parse("2026-06-13T00:00:00Z"), "avatar.png", "default"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAuthController(
                        provider(AuthInboundPort.class, authPort),
                        provider(AuthRefreshInboundPort.class, refreshPort),
                        null)).build();

        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-current-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.token").value("token-next"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-next"));

        ArgumentCaptor<RefreshTokenCommand> captor = ArgumentCaptor.forClass(RefreshTokenCommand.class);
        verify(refreshPort).refresh(captor.capture());
        assertThat(captor.getValue().refreshToken()).isEqualTo("refresh-current-token");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
