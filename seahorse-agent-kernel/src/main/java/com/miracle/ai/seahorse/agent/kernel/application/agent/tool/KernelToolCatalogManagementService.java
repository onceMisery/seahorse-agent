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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolCatalogManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolProviderExposurePolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelToolCatalogManagementService implements ToolCatalogManagementInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String TOOL_NOT_FOUND = "Tool not found";

    private final ToolCatalogRepositoryPort toolCatalogRepository;
    private final ToolProviderExposurePolicyPort providerExposurePolicy;
    private final CurrentUserPort currentUserPort;

    public KernelToolCatalogManagementService(ToolCatalogRepositoryPort toolCatalogRepository,
                                              CurrentUserPort currentUserPort) {
        this(toolCatalogRepository, currentUserPort, ToolProviderExposurePolicyPort.demoDefaults());
    }

    public KernelToolCatalogManagementService(ToolCatalogRepositoryPort toolCatalogRepository,
                                              CurrentUserPort currentUserPort,
                                              ToolProviderExposurePolicyPort providerExposurePolicy) {
        this.toolCatalogRepository = Objects.requireNonNull(toolCatalogRepository,
                "toolCatalogRepository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.providerExposurePolicy = Objects.requireNonNullElseGet(
                providerExposurePolicy,
                ToolProviderExposurePolicyPort::demoDefaults);
    }

    @Override
    public ToolCatalogPage page(String resourceType, String keyword, long current, long size, Boolean enabled) {
        requireAdmin();
        ToolCatalogPage page = toolCatalogRepository.page(new ToolCatalogQuery(resourceType, keyword, current, size,
                enabled));
        List<ToolCatalogEntry> records = page.records().stream()
                .filter(providerExposurePolicy::isToolAllowed)
                .toList();
        return new ToolCatalogPage(records, records.size(), page.size(), page.current(), pages(records.size(),
                page.size()));
    }

    @Override
    public Optional<ToolCatalogEntry> findById(String toolId) {
        requireAdmin();
        return toolCatalogRepository.findById(requireText(toolId, "toolId must not be blank"))
                .filter(providerExposurePolicy::isToolAllowed);
    }

    @Override
    public ToolCatalogEntry enable(String toolId) {
        return setEnabled(toolId, true);
    }

    @Override
    public ToolCatalogEntry disable(String toolId) {
        return setEnabled(toolId, false);
    }

    private ToolCatalogEntry setEnabled(String toolId, boolean enabled) {
        requireAdmin();
        String safeToolId = requireText(toolId, "toolId must not be blank");
        toolCatalogRepository.findById(safeToolId)
                .ifPresent(providerExposurePolicy::requireToolAllowed);
        toolCatalogRepository.setEnabled(safeToolId, enabled);
        return toolCatalogRepository.findById(safeToolId)
                .filter(providerExposurePolicy::isToolAllowed)
                .orElseThrow(() -> new IllegalArgumentException(TOOL_NOT_FOUND));
    }

    private long pages(long total, long size) {
        if (total <= 0L || size <= 0L) {
            return 0L;
        }
        return (total + size - 1L) / size;
    }

    private void requireAdmin() {
        currentUserPort.requireRole(ADMIN_ROLE);
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
