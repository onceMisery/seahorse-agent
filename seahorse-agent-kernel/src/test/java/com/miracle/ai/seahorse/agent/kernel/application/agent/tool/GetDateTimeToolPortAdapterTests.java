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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetDateTimeToolPortAdapterTests {

    @Test
    void shouldRedactCredentialTextFromFailureResult() {
        GetDateTimeToolPortAdapter adapter = new GetDateTimeToolPortAdapter(() -> {
            throw new IllegalStateException("Authorization: Bearer datetime-token api_key=datetime-secret");
        });

        ToolInvocationResult result = adapter.invoke("call-1", GetDateTimeToolPortAdapter.TOOL_ID, Map.of());

        assertFalse(result.success());
        assertTrue(result.error().contains("[REDACTED]"));
        assertFalse(result.error().contains("datetime-token"));
        assertFalse(result.error().contains("datetime-secret"));
    }

    @Test
    void shouldReturnCurrentDateTime() {
        GetDateTimeToolPortAdapter adapter = new GetDateTimeToolPortAdapter(() -> ZoneId.of("Asia/Shanghai"));

        ToolInvocationResult result = adapter.invoke("call-1", GetDateTimeToolPortAdapter.TOOL_ID, Map.of());

        assertTrue(result.success());
        assertTrue(result.content().contains("Asia/Shanghai"));
    }
}
