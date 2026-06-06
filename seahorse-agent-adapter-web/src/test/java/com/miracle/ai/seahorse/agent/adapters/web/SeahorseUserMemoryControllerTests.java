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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.UserMemoryPrivacySetting;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryPage;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.UserMemoryPrivacyInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseUserMemoryControllerTests {

    @Test
    void shouldListCurrentUserLongTermMemories() throws Exception {
        MemoryManagementInboundPort managementPort = mock(MemoryManagementInboundPort.class);
        when(managementPort.listMemories("user-1", "long_term", null, 20))
                .thenReturn(new MemoryPage("long_term", List.of(memory("memory-1", "user-1"))));
        UserMemoryPrivacyInboundPort privacyPort = mock(UserMemoryPrivacyInboundPort.class);
        when(privacyPort.current("user-1")).thenReturn(UserMemoryPrivacySetting.enabled("user-1", false));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(managementPort, privacyPort)).build();

        mvc.perform(get("/api/me/memories").header(WebUserIdResolver.HEADER_USER_ID, "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.privacyMode").value(false))
                .andExpect(jsonPath("$.data.memories[0].memoryId").value("memory-1"))
                .andExpect(jsonPath("$.data.memories[0].memoryType").value("PREFERENCE"))
                .andExpect(jsonPath("$.data.memories[0].displayText").value("Likes concise answers"))
                .andExpect(jsonPath("$.data.memories[0].sourceConversationId").value("conversation-1"))
                .andExpect(jsonPath("$.data.memories[0].status").value("ACTIVE"));

        verify(managementPort).listMemories("user-1", "long_term", null, 20);
    }

    @Test
    void shouldDeleteOnlyOwnedMemory() throws Exception {
        MemoryManagementInboundPort managementPort = mock(MemoryManagementInboundPort.class);
        when(managementPort.findMemory("long_term", "memory-1")).thenReturn(Optional.of(memory("memory-1", "user-1")));
        when(managementPort.deleteMemory("long_term", "memory-1")).thenReturn(true);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(managementPort, mock(UserMemoryPrivacyInboundPort.class)))
                .build();

        mvc.perform(delete("/api/me/memories/memory-1").header(WebUserIdResolver.HEADER_USER_ID, "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.deleted").value(true));

        verify(managementPort).deleteMemory("long_term", "memory-1");
    }

    @Test
    void shouldRejectDeletingAnotherUsersMemory() throws Exception {
        MemoryManagementInboundPort managementPort = mock(MemoryManagementInboundPort.class);
        when(managementPort.findMemory("long_term", "memory-1")).thenReturn(Optional.of(memory("memory-1", "other")));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(managementPort, mock(UserMemoryPrivacyInboundPort.class)))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(delete("/api/me/memories/memory-1").header(WebUserIdResolver.HEADER_USER_ID, "user-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        verify(managementPort, never()).deleteMemory("long_term", "memory-1");
    }

    @Test
    void shouldUpdatePrivacyModeForCurrentUser() throws Exception {
        UserMemoryPrivacyInboundPort privacyPort = mock(UserMemoryPrivacyInboundPort.class);
        when(privacyPort.setPrivacyMode("user-1", true))
                .thenReturn(UserMemoryPrivacySetting.enabled("user-1", true));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(mock(MemoryManagementInboundPort.class), privacyPort))
                .build();

        mvc.perform(post("/api/me/memory-settings/privacy-mode")
                        .header(WebUserIdResolver.HEADER_USER_ID, "user-1")
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.privacyMode").value(true));

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(privacyPort).setPrivacyMode(userCaptor.capture(), org.mockito.ArgumentMatchers.eq(true));
        assertThat(userCaptor.getValue()).isEqualTo("user-1");
    }

    private static SeahorseUserMemoryController controller(MemoryManagementInboundPort managementPort,
                                                           UserMemoryPrivacyInboundPort privacyPort) {
        return new SeahorseUserMemoryController(
                provider(MemoryManagementInboundPort.class, managementPort),
                provider(UserMemoryPrivacyInboundPort.class, privacyPort));
    }

    private static MemoryRecord memory(String id, String userId) {
        return new MemoryRecord(
                id,
                "long_term",
                "PREFERENCE",
                "Likes concise answers",
                Map.of(
                        "userId", userId,
                        "conversationId", "conversation-1",
                        "messageId", "message-1",
                        "status", "ACTIVE",
                        "sensitivity", "LOW"),
                Instant.parse("2026-05-26T00:00:00Z"));
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (instance != null) {
            beanFactory.addBean(type.getName(), instance);
        }
        return beanFactory.getBeanProvider(type);
    }
}
