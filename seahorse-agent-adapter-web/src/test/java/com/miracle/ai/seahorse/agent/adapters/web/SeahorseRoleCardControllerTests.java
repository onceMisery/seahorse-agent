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

import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardInboundPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseRoleCardControllerTests {

    @Test
    void shouldListRoleCardsForUser() throws Exception {
        RoleCardInboundPort port = mock(RoleCardInboundPort.class);
        when(port.list("user-1")).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRoleCardController(provider(port))).build();

        mvc.perform(get("/api/role-cards").param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());

        verify(port).list("user-1");
    }

    @Test
    void shouldCreateRoleCard() throws Exception {
        RoleCardInboundPort port = mock(RoleCardInboundPort.class);
        when(port.save(org.mockito.ArgumentMatchers.any())).thenReturn(9L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRoleCardController(provider(port))).build();

        mvc.perform(post("/api/role-cards")
                        .param("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Coach",
                                  "definition": "Ask short questions.",
                                  "avatarRef": "coach.png",
                                  "higherPerm": true,
                                  "shareScope": "TEAM",
                                  "approvalStatus": "APPROVED",
                                  "published": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(9));

        ArgumentCaptor<RoleCardCommand> captor = ArgumentCaptor.forClass(RoleCardCommand.class);
        verify(port).save(captor.capture());
        assertThat(captor.getValue().id()).isNull();
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().name()).isEqualTo("Coach");
        assertThat(captor.getValue().definition()).isEqualTo("Ask short questions.");
        assertThat(captor.getValue().avatarRef()).isEqualTo("coach.png");
        assertThat(captor.getValue().higherPerm()).isTrue();
        assertThat(captor.getValue().shareScope()).isEqualTo("TEAM");
        assertThat(captor.getValue().approvalStatus()).isEqualTo("APPROVED");
        assertThat(captor.getValue().published()).isTrue();
    }

    @Test
    void shouldUpdateActivateAndDeleteRoleCard() throws Exception {
        RoleCardInboundPort port = mock(RoleCardInboundPort.class);
        when(port.save(org.mockito.ArgumentMatchers.any())).thenReturn(9L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRoleCardController(provider(port))).build();

        mvc.perform(put("/api/role-cards/9")
                        .param("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Coach",
                                  "definition": "Ask short questions.",
                                  "higherPerm": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(9));
        mvc.perform(put("/api/role-cards/9/activate").param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(delete("/api/role-cards/9").param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        ArgumentCaptor<RoleCardCommand> captor = ArgumentCaptor.forClass(RoleCardCommand.class);
        verify(port).save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(9L);
        verify(port).activate("user-1", 9L);
        verify(port).delete("user-1", 9L);
    }

    private static ObjectProvider<RoleCardInboundPort> provider(RoleCardInboundPort instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(RoleCardInboundPort.class.getName(), instance);
        return beanFactory.getBeanProvider(RoleCardInboundPort.class);
    }
}
