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

package com.miracle.ai.seahorse.agent.kernel.application.knowledge;

import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBasePermission;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePermissionRepositoryPort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 知识库权限管理服务，支持授权、撤权、权限检查。
 */
public class KnowledgeBasePermissionService {

    private final KnowledgeBasePermissionRepositoryPort repositoryPort;

    public KnowledgeBasePermissionService(KnowledgeBasePermissionRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    /**
     * 授予权限。
     *
     * @param kbId       知识库 ID
     * @param userId     用户 ID
     * @param permission 权限级别
     * @return 权限记录 ID
     */
    public Long grantPermission(Long kbId, Long userId, String permission) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("permission 不能为空");
        }

        String tenantId = TenantContext.get();

        // 检查是否已存在权限
        Optional<KnowledgeBasePermission> existing = repositoryPort.findByKbIdAndUserId(kbId, userId);
        if (existing.isPresent()) {
            // 更新权限
            KnowledgeBasePermission updated = existing.get().withPermission(permission, Instant.now());
            return repositoryPort.save(updated);
        }

        // 创建新权限
        KnowledgeBasePermission newPermission = new KnowledgeBasePermission(
                null, kbId, tenantId, userId, permission, Instant.now()
        );
        return repositoryPort.save(newPermission);
    }

    /**
     * 撤销权限。
     */
    public boolean revokePermission(Long permissionId) {
        if (permissionId == null) {
            throw new IllegalArgumentException("permissionId 不能为空");
        }
        return repositoryPort.deleteById(permissionId);
    }

    /**
     * 检查用户权限。
     *
     * @return 权限记录，如果无权限则返回 empty
     */
    public Optional<KnowledgeBasePermission> checkPermission(Long kbId, Long userId) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return repositoryPort.findByKbIdAndUserId(kbId, userId);
    }

    /**
     * 检查用户是否有编辑权限。
     */
    public boolean canEdit(Long kbId, Long userId) {
        return checkPermission(kbId, userId)
                .map(KnowledgeBasePermission::canEdit)
                .orElse(false);
    }

    /**
     * 检查用户是否有删除权限。
     */
    public boolean canDelete(Long kbId, Long userId) {
        return checkPermission(kbId, userId)
                .map(KnowledgeBasePermission::canDelete)
                .orElse(false);
    }

    /**
     * 查询知识库的所有权限。
     */
    public List<KnowledgeBasePermission> listPermissions(Long kbId) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        return repositoryPort.findByKbId(kbId);
    }

    /**
     * 删除知识库的所有权限。
     */
    public int revokeAllPermissions(Long kbId) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        return repositoryPort.deleteByKbId(kbId);
    }
}
