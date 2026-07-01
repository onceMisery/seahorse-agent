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

package com.miracle.ai.seahorse.agent.kernel.application.agent.connector;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class OpenApiAwareToolRegistryPort implements ToolRegistryPort {

    private final ToolRegistryPort delegate;
    private final OpenApiToolPortAdapter openApiToolPortAdapter;

    public OpenApiAwareToolRegistryPort(ToolRegistryPort delegate,
                                        OpenApiToolPortAdapter openApiToolPortAdapter) {
        this.delegate = Objects.requireNonNullElseGet(delegate, ToolRegistryPort::empty);
        this.openApiToolPortAdapter = Objects.requireNonNull(openApiToolPortAdapter,
                "openApiToolPortAdapter must not be null");
    }

    @Override
    public List<ToolDescriptor> listTools() {
        Map<String, ToolDescriptor> descriptors = new LinkedHashMap<>();
        delegate.listTools().forEach(descriptor -> descriptors.put(descriptor.toolId(), descriptor));
        openApiToolPortAdapter.listEnabledDescriptors()
                .forEach(descriptor -> descriptors.putIfAbsent(descriptor.toolId(), descriptor));
        return List.copyOf(descriptors.values());
    }

    @Override
    public Optional<ToolPort> find(String toolId) {
        Optional<ToolPort> delegated = delegate.find(toolId);
        if (delegated.isPresent()) {
            return delegated;
        }
        return openApiToolPortAdapter.descriptor(toolId).map(descriptor -> (ToolPort) openApiToolPortAdapter);
    }

    @Override
    public void register(ToolDescriptor descriptor, ToolPort port) {
        delegate.register(descriptor, port);
    }
}
