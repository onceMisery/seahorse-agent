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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task A4 契约测试：InMemoryToolRegistry。
 */
class InMemoryToolRegistryTests {

    private final ToolDescriptor descriptor = new ToolDescriptor(
            "weather", "Weather Tool", "查询城市天气", "{}");

    private final ToolPort weatherPort = (callId, toolId, args) ->
            ToolInvocationResult.ok("{\"city\":" + args.getOrDefault("city", "?") + "}");

    @Test
    void registerStoresDescriptorAndPort() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(descriptor, weatherPort);
        assertEquals(1, registry.listTools().size());
        assertSame(descriptor, registry.listTools().get(0));
        Optional<ToolPort> found = registry.find("weather");
        assertTrue(found.isPresent());
        ToolInvocationResult result = found.get().invoke("c-1", "weather", Map.of("city", "SH"));
        assertNotNull(result);
        assertTrue(result.success());
    }

    @Test
    void duplicateToolIdThrows() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(descriptor, weatherPort);
        assertThrows(IllegalStateException.class, () -> registry.register(descriptor, weatherPort));
    }

    @Test
    void nullArgumentsRejected() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        assertThrows(NullPointerException.class, () -> registry.register(null, weatherPort));
        assertThrows(NullPointerException.class, () -> registry.register(descriptor, null));
    }

    @Test
    void findMissingToolReturnsEmpty() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        assertTrue(registry.find("nope").isEmpty());
    }
}
