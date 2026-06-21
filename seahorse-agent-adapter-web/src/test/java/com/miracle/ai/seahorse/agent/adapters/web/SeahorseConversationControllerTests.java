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

import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationBranchInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ForkCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ForkResult;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchCursor;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.MessageTreeNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseConversationControllerTests {

    @Test
    void shouldCreateConversationFromApiAlias() throws Exception {
        ConversationManagementInboundPort port = mock(ConversationManagementInboundPort.class);
        when(port.create("user-1")).thenReturn("conv-1");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseConversationController(
                        provider(ConversationManagementInboundPort.class, port),
                        emptyProvider(ConversationBranchInboundPort.class))).build();

        mvc.perform(post("/api/conversations")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("conv-1"));

        verify(port).create("user-1");
    }

    @Test
    void shouldListConversationsForUser() throws Exception {
        ConversationManagementInboundPort port = mock(ConversationManagementInboundPort.class);
        when(port.listConversations("user-1")).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseConversationController(
                        provider(ConversationManagementInboundPort.class, port),
                        emptyProvider(ConversationBranchInboundPort.class))).build();

        mvc.perform(get("/conversations")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());

        verify(port).listConversations("user-1");
    }

    @Test
    void shouldRenameConversation() throws Exception {
        ConversationManagementInboundPort port = mock(ConversationManagementInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseConversationController(
                        provider(ConversationManagementInboundPort.class, port),
                        emptyProvider(ConversationBranchInboundPort.class))).build();

        mvc.perform(put("/conversations/conv-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "New Title"
                                }
                                """)
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).rename("conv-1", "user-1", "New Title");
    }

    @Test
    void shouldDeleteConversation() throws Exception {
        ConversationManagementInboundPort port = mock(ConversationManagementInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseConversationController(
                        provider(ConversationManagementInboundPort.class, port),
                        emptyProvider(ConversationBranchInboundPort.class))).build();

        mvc.perform(delete("/conversations/conv-1")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).delete("conv-1", "user-1");
    }

    @Test
    void shouldListMessagesForConversation() throws Exception {
        ConversationManagementInboundPort port = mock(ConversationManagementInboundPort.class);
        when(port.listMessages("conv-1", "user-1")).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseConversationController(
                        provider(ConversationManagementInboundPort.class, port),
                        emptyProvider(ConversationBranchInboundPort.class))).build();

        mvc.perform(get("/conversations/conv-1/messages")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).listMessages("conv-1", "user-1");
    }

    @Test
    void shouldForkConversationMessage() throws Exception {
        ConversationManagementInboundPort conversationPort = mock(ConversationManagementInboundPort.class);
        ConversationBranchInboundPort branchPort = mock(ConversationBranchInboundPort.class);
        when(branchPort.fork(any())).thenReturn(new ForkResult(3L, 1L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseConversationController(
                provider(ConversationManagementInboundPort.class, conversationPort),
                provider(ConversationBranchInboundPort.class, branchPort))).build();

        mvc.perform(post("/api/conversations/conv-1/messages/fork")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "anchorMessageId": 2,
                                  "content": "updated",
                                  "role": "user",
                                  "regenerate": false
                                }
                                """)
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.newMessageId").value(3))
                .andExpect(jsonPath("$.data.parentId").value(1));

        ArgumentCaptor<ForkCommand> captor = ArgumentCaptor.forClass(ForkCommand.class);
        verify(branchPort).fork(captor.capture());
        assertThat(captor.getValue().conversationId()).isEqualTo("conv-1");
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().anchorMessageId()).isEqualTo(2L);
    }

    @Test
    void shouldSwitchBranchAndLoadTree() throws Exception {
        ConversationManagementInboundPort conversationPort = mock(ConversationManagementInboundPort.class);
        ConversationBranchInboundPort branchPort = mock(ConversationBranchInboundPort.class);
        when(branchPort.switchBranch("conv-1", "user-1", 3L)).thenReturn(List.<MessageTreeNode>of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseConversationController(
                provider(ConversationManagementInboundPort.class, conversationPort),
                provider(ConversationBranchInboundPort.class, branchPort))).build();

        mvc.perform(post("/api/conversations/conv-1/messages/branch/switch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetNodeId": 3
                                }
                                """)
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());

        verify(branchPort).switchBranch("conv-1", "user-1", 3L);
    }

    @Test
    void shouldSaveBranchCursor() throws Exception {
        ConversationManagementInboundPort conversationPort = mock(ConversationManagementInboundPort.class);
        ConversationBranchInboundPort branchPort = mock(ConversationBranchInboundPort.class);
        when(branchPort.saveCursor("conv-1", "user-1", 3L)).thenReturn(ConversationBranchCursor.builder()
                .tenantId("default")
                .conversationId("conv-1")
                .userId("user-1")
                .leafMessageId(3L)
                .build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseConversationController(
                provider(ConversationManagementInboundPort.class, conversationPort),
                provider(ConversationBranchInboundPort.class, branchPort))).build();

        mvc.perform(post("/api/conversations/conv-1/branch-cursor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leafMessageId": 3
                                }
                                """)
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.leafMessageId").value(3));

        verify(branchPort).saveCursor("conv-1", "user-1", 3L);
    }

    @Test
    void shouldLoadBranchCursor() throws Exception {
        ConversationManagementInboundPort conversationPort = mock(ConversationManagementInboundPort.class);
        ConversationBranchInboundPort branchPort = mock(ConversationBranchInboundPort.class);
        when(branchPort.loadCursor("conv-1", "user-1")).thenReturn(Optional.of(ConversationBranchCursor.builder()
                .tenantId("default")
                .conversationId("conv-1")
                .userId("user-1")
                .leafMessageId(3L)
                .build()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseConversationController(
                provider(ConversationManagementInboundPort.class, conversationPort),
                provider(ConversationBranchInboundPort.class, branchPort))).build();

        mvc.perform(get("/api/conversations/conv-1/branch-cursor")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.leafMessageId").value(3));

        verify(branchPort).loadCursor("conv-1", "user-1");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        return beanFactory.getBeanProvider(type);
    }
}
