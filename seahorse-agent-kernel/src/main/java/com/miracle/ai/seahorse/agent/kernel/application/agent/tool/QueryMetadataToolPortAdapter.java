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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class QueryMetadataToolPortAdapter implements ToolPort {

    public static final String TOOL_ID = "query_metadata";

    private final AgentToolJsonSupport jsonSupport;

    public QueryMetadataToolPortAdapter(AgentToolJsonSupport jsonSupport) {
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    public static ToolDescriptor descriptor() {
        return new ToolDescriptor(TOOL_ID, "Query Metadata",
                "Describe currently supported metadata fields for Agent knowledge search.",
                """
                        {"type":"object","properties":{"knowledgeBaseIds":{"type":"array","items":{"type":"string"}}}}
                        """);
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        return ToolInvocationResult.ok(jsonSupport.write(Map.of(
                "fields", List.of(
                        Map.of("name", "knowledgeBaseId", "type", "string", "description", "Knowledge base id"),
                        Map.of("name", "documentId", "type", "string", "description", "Document id"),
                        Map.of("name", "tenantId", "type", "string", "description", "Tenant id from server scope")),
                "warnings", List.of("Dynamic metadata dictionary is not configured; returned built-in fields only"))));
    }
}
