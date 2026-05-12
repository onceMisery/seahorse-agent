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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.function.Supplier;

class LlmMcpParameterExtractionAdapterTests {

    @Test
    void shouldParseDeclaredParametersAndFillDefaults() {
        ChatModelPort chatModelPort = (request, modelId) -> """
                ```json
                {"city":"杭州","unused":"ignore"}
                ```
                """;
        LlmMcpParameterExtractionAdapter adapter = new LlmMcpParameterExtractionAdapter(
                new FixedObjectProvider<>(chatModelPort), new ObjectMapper());

        Map<String, Object> result = adapter.extract(new McpParameterExtractionRequest(tool(), "杭州天气", ""));

        Assertions.assertEquals("杭州", result.get("city"));
        Assertions.assertEquals("celsius", result.get("unit"));
        Assertions.assertFalse(result.containsKey("unused"));
    }

    @Test
    void shouldFallbackToDefaultsWhenModelIsUnavailable() {
        LlmMcpParameterExtractionAdapter adapter = new LlmMcpParameterExtractionAdapter(
                new FixedObjectProvider<>(null), new ObjectMapper());

        Map<String, Object> result = adapter.extract(new McpParameterExtractionRequest(tool(), "杭州天气", ""));

        Assertions.assertEquals(Map.of("unit", "celsius"), result);
    }

    private McpToolDescriptor tool() {
        return new McpToolDescriptor("weather_query", "查询天气", Map.of(
                "city", new McpToolDescriptor.Parameter("城市", "string", true, null, java.util.List.of()),
                "unit", new McpToolDescriptor.Parameter("单位", "string", false, "celsius", java.util.List.of())
        ));
    }

    private record FixedObjectProvider<T>(T value) implements ObjectProvider<T> {

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getIfAvailable(Supplier<T> defaultSupplier) {
            return value == null ? defaultSupplier.get() : value;
        }

        @Override
        public T getIfUnique(Supplier<T> defaultSupplier) {
            return value == null ? defaultSupplier.get() : value;
        }
    }
}
