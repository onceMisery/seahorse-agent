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
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.Objects;
import java.util.Optional;

public class KernelToolCatalogManagementService implements ToolCatalogManagementInboundPort {

    private static final String ADMIN_ROLE = "admin";

    private final ToolCatalogRepositoryPort toolCatalogRepository;
    private final CurrentUserPort currentUserPort;

    public KernelToolCatalogManagementService(ToolCatalogRepositoryPort toolCatalogRepository,
                                              CurrentUserPort currentUserPort) {
        this.toolCatalogRepository = Objects.requireNonNull(toolCatalogRepository,
                "toolCatalogRepository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public ToolCatalogPage page(String resourceType, String keyword, long current, long size, Boolean enabled) {
        requireAdmin();
        return toolCatalogRepository.page(new ToolCatalogQuery(resourceType, keyword, current, size, enabled));
    }

    @Override
    public Optional<ToolCatalogEntry> findById(String toolId) {
        requireAdmin();
        return toolCatalogRepository.findById(requireText(toolId, "toolId 不能为空"));
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
        String safeToolId = requireText(toolId, "toolId 不能为空");
        // 先执行状态变更，再读取最新目录条目返回给管理端。
        toolCatalogRepository.setEnabled(safeToolId, enabled);
        return toolCatalogRepository.findById(safeToolId)
                .orElseThrow(() -> new IllegalArgumentException("工具不存在"));
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
