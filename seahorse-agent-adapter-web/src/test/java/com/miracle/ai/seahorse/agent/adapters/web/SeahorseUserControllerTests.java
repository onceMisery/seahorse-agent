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

import com.miracle.ai.seahorse.agent.ports.inbound.user.UserInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseUserControllerTests {

    @Test
    void shouldGetCurrentUser() throws Exception {
        UserInboundPort port = mock(UserInboundPort.class);
        when(port.currentUser()).thenReturn(new CurrentUser(1L, "admin", "ADMIN", "avatar.png"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseUserController(provider(UserInboundPort.class, port))).build();

        mvc.perform(get("/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).currentUser();
    }

    @Test
    void shouldPageQueryUsers() throws Exception {
        UserInboundPort port = mock(UserInboundPort.class);
        when(port.page(1L, 10L, "admin")).thenReturn(new UserPage(java.util.List.of(), 0L, 10L, 1L, 1L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseUserController(provider(UserInboundPort.class, port))).build();

        mvc.perform(get("/users")
                        .param("current", "1")
                        .param("size", "10")
                        .param("keyword", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).page(1L, 10L, "admin");
    }

    @Test
    void shouldCreateUser() throws Exception {
        UserInboundPort port = mock(UserInboundPort.class);
        when(port.create(any())).thenReturn(1L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseUserController(provider(UserInboundPort.class, port))).build();

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "secret",
                                  "role": "ADMIN",
                                  "avatar": "avatar.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(1));

        verify(port).create(any());
    }

    @Test
    void shouldUpdateUser() throws Exception {
        UserInboundPort port = mock(UserInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseUserController(provider(UserInboundPort.class, port))).build();

        mvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin-updated",
                                  "password": "newpass",
                                  "role": "USER",
                                  "avatar": "new-avatar.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).update(eq(1L), any());
    }

    @Test
    void shouldDeleteUser() throws Exception {
        UserInboundPort port = mock(UserInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseUserController(provider(UserInboundPort.class, port))).build();

        mvc.perform(delete("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).delete(1L);
    }

    @Test
    void shouldChangePassword() throws Exception {
        UserInboundPort port = mock(UserInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseUserController(provider(UserInboundPort.class, port))).build();

        mvc.perform(put("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "old-pass",
                                  "newPassword": "new-pass"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).changePassword(any());
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
