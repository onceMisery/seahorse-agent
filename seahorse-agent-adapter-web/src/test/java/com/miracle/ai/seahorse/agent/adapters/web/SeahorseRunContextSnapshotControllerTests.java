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

import com.miracle.ai.seahorse.agent.ports.inbound.runcontext.RunContextSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseRunContextSnapshotControllerTests {

    @Test
    void shouldFindSnapshotByRunId() throws Exception {
        RunContextSnapshotInboundPort port = mock(RunContextSnapshotInboundPort.class);
        when(port.findByRunId("run-1")).thenReturn(Optional.of(snapshot()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRunContextSnapshotController(provider(port))).build();

        mvc.perform(get("/api/run-context-snapshots/by-run/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.executorEngine").value("agentscope"))
                .andExpect(jsonPath("$.data.snapshotJson").value("{\"toolIds\":[\"echo\"]}"));

        verify(port).findByRunId("run-1");
    }

    @Test
    void shouldExposeAgentRunContextSnapshotAlias() throws Exception {
        RunContextSnapshotInboundPort port = mock(RunContextSnapshotInboundPort.class);
        when(port.findByRunId("run-1")).thenReturn(Optional.of(snapshot()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRunContextSnapshotController(provider(port))).build();

        mvc.perform(get("/api/agent-runs/run-1/context-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.runId").value("run-1"));

        verify(port).findByRunId("run-1");
    }

    private static RunContextSnapshotRecord snapshot() {
        RunContextSnapshotRecord record = new RunContextSnapshotRecord();
        record.setId(9L);
        record.setTenantId("default");
        record.setRunId("run-1");
        record.setConversationId(101L);
        record.setBranchLeafMessageId(202L);
        record.setRoleCardId(303L);
        record.setRunProfileId(404L);
        record.setExecutorEngine("agentscope");
        record.setExecutorConfigJson("{\"nacosNamespace\":\"public\"}");
        record.setTraceContextJson("{\"traceId\":\"trace-1\"}");
        record.setSnapshotJson("{\"toolIds\":[\"echo\"]}");
        record.setCreateTime(Instant.parse("2026-06-21T10:00:00Z"));
        record.setDeleted(0);
        return record;
    }

    private static ObjectProvider<RunContextSnapshotInboundPort> provider(RunContextSnapshotInboundPort instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(RunContextSnapshotInboundPort.class.getName(), instance);
        return beanFactory.getBeanProvider(RunContextSnapshotInboundPort.class);
    }
}
