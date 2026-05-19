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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task A3 契约测试：ToolPort / ToolDescriptor / ToolInvocationResult / ToolRegistryPort。
 */
class ToolPortContractTests {

    @Test
    void toolDescriptorRejectsBlankIdOrName() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("", "x", "y", "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("id", " ", "y", "{}"));
    }

    @Test
    void toolInvocationResultOkAndFailedFactories() {
        ToolInvocationResult ok = ToolInvocationResult.ok("{\"a\":1}");
        assertTrue(ok.success());
        assertEquals("{\"a\":1}", ok.content());
        assertNull(ok.error());

        ToolInvocationResult failed = ToolInvocationResult.failed("oops");
        assertFalse(failed.success());
        assertNull(failed.content());
        assertEquals("oops", failed.error());
    }

    @Test
    void toolPortNotFoundReturnsFailedResult() {
        ToolPort port = ToolPort.notFound("unknown");
        ToolInvocationResult r = port.invoke("c-1", "unknown", Map.of());
        assertFalse(r.success());
        assertNotNull(r.error());
        assertTrue(r.error().contains("unknown"));
    }

    @Test
    void emptyToolRegistryHasNoTools() {
        ToolRegistryPort registry = ToolRegistryPort.empty();
        assertTrue(registry.listTools().isEmpty());
        assertTrue(registry.find("x").isEmpty());
    }
}
