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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryForgetToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryReadToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryWriteToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.QueryMetadataToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.SearchKnowledgeBaseToolPortAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Objects;

/**
 * Registers built-in kernel capabilities as Agent tools when the corresponding
 * adapters are present in the Spring context.
 */
public class BuiltInAgentToolRegistrar implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BuiltInAgentToolRegistrar.class);

    private final ToolRegistryPort toolRegistry;
    private final ObjectProvider<SearchKnowledgeBaseToolPortAdapter> searchTool;
    private final ObjectProvider<QueryMetadataToolPortAdapter> metadataTool;
    private final ObjectProvider<MemoryReadToolPortAdapter> memoryReadTool;
    private final ObjectProvider<MemoryWriteToolPortAdapter> memoryWriteTool;
    private final ObjectProvider<MemoryForgetToolPortAdapter> memoryForgetTool;

    public BuiltInAgentToolRegistrar(ToolRegistryPort toolRegistry,
                                     ObjectProvider<SearchKnowledgeBaseToolPortAdapter> searchTool,
                                     ObjectProvider<QueryMetadataToolPortAdapter> metadataTool,
                                     ObjectProvider<MemoryReadToolPortAdapter> memoryReadTool,
                                     ObjectProvider<MemoryWriteToolPortAdapter> memoryWriteTool,
                                     ObjectProvider<MemoryForgetToolPortAdapter> memoryForgetTool) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
        this.searchTool = Objects.requireNonNull(searchTool, "searchTool must not be null");
        this.metadataTool = Objects.requireNonNull(metadataTool, "metadataTool must not be null");
        this.memoryReadTool = Objects.requireNonNull(memoryReadTool, "memoryReadTool must not be null");
        this.memoryWriteTool = Objects.requireNonNull(memoryWriteTool, "memoryWriteTool must not be null");
        this.memoryForgetTool = Objects.requireNonNull(memoryForgetTool, "memoryForgetTool must not be null");
    }

    @Override
    public void run(ApplicationArguments args) {
        register(SearchKnowledgeBaseToolPortAdapter.descriptor(), searchTool.getIfAvailable());
        register(QueryMetadataToolPortAdapter.descriptor(), metadataTool.getIfAvailable());
        register(MemoryReadToolPortAdapter.descriptor(), memoryReadTool.getIfAvailable());
        register(MemoryWriteToolPortAdapter.descriptor(), memoryWriteTool.getIfAvailable());
        register(MemoryForgetToolPortAdapter.descriptor(), memoryForgetTool.getIfAvailable());
    }

    private void register(ToolDescriptor descriptor, ToolPort toolPort) {
        if (toolPort == null || toolRegistry.find(descriptor.toolId()).isPresent()) {
            return;
        }
        try {
            toolRegistry.register(descriptor, toolPort);
        } catch (UnsupportedOperationException ex) {
            LOG.warn("ToolRegistryPort does not support built-in tool registration: toolId={}",
                    descriptor.toolId(), ex);
        }
    }
}
