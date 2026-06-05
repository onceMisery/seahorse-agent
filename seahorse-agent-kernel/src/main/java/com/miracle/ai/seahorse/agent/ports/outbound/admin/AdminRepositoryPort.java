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

package com.miracle.ai.seahorse.agent.ports.outbound.admin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 管理后台仓储端口（绕过 TenantContext 进行跨租户操作）。
 */
public interface AdminRepositoryPort {

    /**
     * 分页查询所有租户。
     */
    List<TenantDetail> findAllTenants(int page, int size, String statusFilter);

    /**
     * 查询租户总数。
     */
    long countTenants(String statusFilter);

    /**
     * 查询租户详情。
     */
    Optional<TenantDetail> findTenantDetail(String tenantId);

    /**
     * 查询租户下的用户列表。
     */
    List<Map<String, Object>> findUsersByTenant(String tenantId, int page, int size);

    /**
     * 查询租户下的用户总数。
     */
    long countUsersByTenant(String tenantId);

    /**
     * 获取租户资源统计。
     */
    ResourceSummary getTenantResourceSummary(String tenantId);

    /**
     * 更新租户状态。
     */
    boolean updateTenantStatus(String tenantId, String newStatus);

    /**
     * 删除租户及其所有资源。
     */
    boolean deleteTenant(String tenantId);

    /**
     * 禁用用户。
     */
    boolean banUser(String tenantId, Long userId);

    /**
     * 重置用户密码。
     */
    boolean resetUserPassword(String tenantId, Long userId, String newPasswordHash);

    /**
     * 强制用户登出（清除会话）。
     */
    boolean forceLogout(String tenantId, Long userId);
}
