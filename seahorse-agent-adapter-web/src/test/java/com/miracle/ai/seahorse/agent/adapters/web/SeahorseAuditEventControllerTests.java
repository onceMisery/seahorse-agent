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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseAuditEventControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldExposeAuditEventQueryApis() throws Exception {
        AuditQueryInboundPort port = mock(AuditQueryInboundPort.class);
        AuditEvent event = event();
        when(port.findById("audit-1")).thenReturn(Optional.of(event));
        when(port.page(
                "tenant-a",
                "run-1",
                "agent-1",
                "tool",
                "weather",
                AuditEventType.TOOL_INVOKED,
                null,
                null,
                1,
                10))
                .thenReturn(new AuditEventPage(List.of(event), 1L, 10L, 1L, 1L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAuditEventController(provider(AuditQueryInboundPort.class, port))).build();

        mvc.perform(get("/api/audit-events")
                        .param("tenantId", "tenant-a")
                        .param("runId", "run-1")
                        .param("agentId", "agent-1")
                        .param("resourceType", "tool")
                        .param("resourceId", "weather")
                        .param("eventType", "TOOL_INVOKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].auditId").value("audit-1"))
                .andExpect(jsonPath("$.data.records[0].eventType").value("TOOL_INVOKED"))
                .andExpect(jsonPath("$.data.records[0].redactedPayload").value("{\"safe\":\"ok\"}"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<AuditEventType> eventTypeCaptor = ArgumentCaptor.forClass(AuditEventType.class);
        verify(port).page(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("run-1"),
                org.mockito.ArgumentMatchers.eq("agent-1"),
                org.mockito.ArgumentMatchers.eq("tool"),
                org.mockito.ArgumentMatchers.eq("weather"),
                eventTypeCaptor.capture(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L));
        assertThat(eventTypeCaptor.getValue()).isEqualTo(AuditEventType.TOOL_INVOKED);

        mvc.perform(get("/api/audit-events/audit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditId").value("audit-1"))
                .andExpect(jsonPath("$.data.resourceType").value("tool"))
                .andExpect(jsonPath("$.data.resourceId").value("weather"));
        verify(port).findById("audit-1");
    }

    private static AuditEvent event() {
        return new AuditEvent(
                "audit-1",
                "tenant-a",
                AuditEventType.TOOL_INVOKED,
                AuditActorType.AGENT,
                "agent-1",
                "run-1",
                "agent-1",
                "tool",
                "weather",
                "{\"safe\":\"ok\"}",
                NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
