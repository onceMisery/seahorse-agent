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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class FakeStdioMcpServer {

    private FakeStdioMcpServer() {
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode request = mapper.readTree(line);
                if (!request.has("id")) {
                    continue;
                }
                ObjectNode response = mapper.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.set("id", request.get("id"));
                response.set("result", resultFor(mapper, request));
                writer.write(mapper.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
        }
    }

    private static JsonNode resultFor(ObjectMapper mapper, JsonNode request) {
        return switch (request.path("method").asText()) {
            case "initialize" -> initializeResult(mapper);
            case "tools/list" -> toolsListResult(mapper);
            case "tools/call" -> toolsCallResult(mapper, request);
            default -> mapper.createObjectNode();
        };
    }

    private static ObjectNode initializeResult(ObjectMapper mapper) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2026-02-28");
        result.set("capabilities", mapper.createObjectNode());
        ObjectNode serverInfo = mapper.createObjectNode();
        serverInfo.put("name", "fake-stdio");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);
        return result;
    }

    private static ObjectNode toolsListResult(ObjectMapper mapper) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = mapper.createArrayNode();
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", "echo");
        tool.put("description", "Echo text");
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ObjectNode text = mapper.createObjectNode();
        text.put("type", "string");
        text.put("description", "Text to echo");
        properties.set("text", text);
        schema.set("properties", properties);
        ArrayNode required = mapper.createArrayNode();
        required.add("text");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        tools.add(tool);
        result.set("tools", tools);
        return result;
    }

    private static ObjectNode toolsCallResult(ObjectMapper mapper, JsonNode request) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("type", "text");
        String prefix = System.getenv().getOrDefault("FAKE_MCP_PREFIX", "echo");
        String text = request.path("params").path("arguments").path("text").asText("");
        item.put("text", prefix + " " + text);
        content.add(item);
        result.set("content", content);
        return result;
    }
}
