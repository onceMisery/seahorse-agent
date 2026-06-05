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

package com.miracle.ai.seahorse.agent.kernel.application.admin;

import com.miracle.ai.seahorse.agent.kernel.domain.audit.AuditLog;
import com.miracle.ai.seahorse.agent.ports.outbound.admin.AdminRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.admin.AuditLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.admin.ResourceSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.admin.TenantDetail;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理后台租户管理服务，支持租户列表、详情、状态管理、用户管理。
 */
public class KernelAdminTenantService {

    private final AdminRepositoryPort adminRepository;
    private final AuditLogRepositoryPort auditLogRepository;

    public KernelAdminTenantService(AdminRepositoryPort adminRepository,
                                    AuditLogRepositoryPort auditLogRepository) {
        this.adminRepository = Objects.requireNonNull(adminRepository, "adminRepository must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
    }

    /**
     * 分页查询所有租户。
     */
    public List<TenantDetail> listTenants(int page, int size, String statusFilter) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        return adminRepository.findAllTenants(page, size, statusFilter);
    }

    /**
     * 查询租户总数。
     */
    public long countTenants(String statusFilter) {
        return adminRepository.countTenants(statusFilter);
    }

    /**
     * 查询租户详情。
     */
    public TenantDetail getTenantDetail(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        return adminRepository.findTenantDetail(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("租户不存在：" + tenantId));
    }

    /**
     * 挂起租户。
     */
    public void suspendTenant(String tenantId, String operator) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空");
        }

        boolean updated = adminRepository.updateTenantStatus(tenantId, "SUSPENDED");
        if (!updated) {
            throw new IllegalArgumentException("租户不存在或状态更新失败：" + tenantId);
        }

        auditLogRepository.save(AuditLog.create(
                "SYSTEM", operator, "SUSPEND_TENANT", "TENANT", tenantId,
                "挂起租户", "", ""));
    }

    /**
     * 删除租户。
     */
    public void deleteTenant(String tenantId, String operator) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空");
        }

        boolean deleted = adminRepository.deleteTenant(tenantId);
        if (!deleted) {
            throw new IllegalArgumentException("租户不存在或删除失败：" + tenantId);
        }

        auditLogRepository.save(AuditLog.create(
                "SYSTEM", operator, "DELETE_TENANT", "TENANT", tenantId,
                "删除租户及其所有资源", "", ""));
    }

    /**
     * 查询租户下的用户列表。
     */
    public List<Map<String, Object>> listUsersByTenant(String tenantId, int page, int size) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        return adminRepository.findUsersByTenant(tenantId, page, size);
    }

    /**
     * 查询租户下的用户总数。
     */
    public long countUsersByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        return adminRepository.countUsersByTenant(tenantId);
    }

    /**
     * 禁用用户。
     */
    public void banUser(String tenantId, Long userId, String operator) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空");
        }

        boolean banned = adminRepository.banUser(tenantId, userId);
        if (!banned) {
            throw new IllegalArgumentException("用户不存在或禁用失败");
        }

        auditLogRepository.save(AuditLog.create(
                tenantId, operator, "BAN_USER", "USER", String.valueOf(userId),
                "禁用用户", "", ""));
    }

    /**
     * 重置用户密码。
     */
    public void resetPassword(String tenantId, Long userId, String newPasswordHash, String operator) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("newPasswordHash 不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空");
        }

        boolean reset = adminRepository.resetUserPassword(tenantId, userId, newPasswordHash);
        if (!reset) {
            throw new IllegalArgumentException("用户不存在或密码重置失败");
        }

        auditLogRepository.save(AuditLog.create(
                tenantId, operator, "RESET_PASSWORD", "USER", String.valueOf(userId),
                "重置用户密码", "", ""));
    }

    /**
     * 强制用户登出。
     */
    public void forceLogout(String tenantId, Long userId, String operator) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空");
        }

        boolean loggedOut = adminRepository.forceLogout(tenantId, userId);
        if (!loggedOut) {
            throw new IllegalArgumentException("用户不存在或强制登出失败");
        }

        auditLogRepository.save(AuditLog.create(
                tenantId, operator, "FORCE_LOGOUT", "USER", String.valueOf(userId),
                "强制用户登出", "", ""));
    }

    /**
     * 获取租户资源统计。
     */
    public ResourceSummary getTenantResourceSummary(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        return adminRepository.getTenantResourceSummary(tenantId);
    }
}
