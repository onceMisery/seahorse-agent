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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task A8 契约测试：把 KernelMcpOrchestrator 适配为 Agent ToolPort。
 */
class McpToolPortAdapterTests {

    @Test
    void successfulMcpExecutionReturnsContentAsToolObservation() {
        KernelMcpOrchestrator orchestrator = mock(KernelMcpOrchestrator.class);
        when(orchestrator.execute(any(McpToolExecutionRequest.class)))
                .thenReturn(McpToolExecutionResult.success("weather", "{\"city\":\"Shanghai\"}"));

        McpToolPortAdapter adapter = new McpToolPortAdapter(orchestrator);
        ToolInvocationResult result = adapter.invoke("call-1", "weather", Map.of("city", "Shanghai"));

        assertTrue(result.success());
        assertEquals("{\"city\":\"Shanghai\"}", result.content());
        ArgumentCaptor<McpToolExecutionRequest> captor = ArgumentCaptor.forClass(McpToolExecutionRequest.class);
        verify(orchestrator).execute(captor.capture());
        assertEquals("weather", captor.getValue().toolId());
        assertEquals("", captor.getValue().userQuestion());
        assertEquals(Map.of("city", "Shanghai"), captor.getValue().arguments());
    }

    @Test
    void failedMcpExecutionReturnsMessageAsToolError() {
        KernelMcpOrchestrator orchestrator = mock(KernelMcpOrchestrator.class);
        when(orchestrator.execute(any(McpToolExecutionRequest.class)))
                .thenReturn(McpToolExecutionResult.failed("weather", "remote timeout"));

        ToolInvocationResult result = new McpToolPortAdapter(orchestrator)
                .invoke("call-1", "weather", Map.of());

        assertFalse(result.success());
        assertEquals("remote timeout", result.error());
    }

    @Test
    void orchestratorExceptionReturnsFailedResult() {
        KernelMcpOrchestrator orchestrator = mock(KernelMcpOrchestrator.class);
        when(orchestrator.execute(any(McpToolExecutionRequest.class)))
                .thenThrow(new IllegalStateException("boom"));

        ToolInvocationResult result = new McpToolPortAdapter(orchestrator)
                .invoke("call-1", "weather", Map.of());

        assertFalse(result.success());
        assertEquals("boom", result.error());
    }
}
