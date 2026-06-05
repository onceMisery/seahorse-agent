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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.application.admin.KernelAdminTenantService;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 管理后台 — 用户管理控制器。
 *
 * <p>提供禁用用户、重置密码、强制登出等管理操作。
 * 所有端点均需要 SUPER_ADMIN 权限。
 */
@RestController
@RequestMapping("/api/admin/users")
public class SeahorseAdminUserController {

    private final ObjectProvider<KernelAdminTenantService> adminServiceProvider;
    private final CurrentUserPort currentUserPort;

    public SeahorseAdminUserController(ObjectProvider<KernelAdminTenantService> adminServiceProvider,
                                       CurrentUserPort currentUserPort) {
        this.adminServiceProvider = adminServiceProvider;
        this.currentUserPort = currentUserPort;
    }

    /**
     * 禁用指定用户。
     */
    @PutMapping("/{userId}/ban")
    public ApiResponse<Object> banUser(@PathVariable Long userId) {
        KernelAdminTenantService service = requireService();
        CurrentUser operator = currentUserPort.requireCurrentUser();
        String tenantId = operator.effectiveTenantId();
        service.banUser(tenantId, userId, operator.operator());
        return ApiResponse.ok(Map.of("userId", userId, "status", "BANNED"));
    }

    /**
     * 重置用户密码。
     */
    @PutMapping("/{userId}/reset-password")
    public ApiResponse<Object> resetPassword(@PathVariable Long userId,
                                             @RequestBody(required = false) Map<String, String> body) {
        KernelAdminTenantService service = requireService();
        CurrentUser operator = currentUserPort.requireCurrentUser();
        String tenantId = operator.effectiveTenantId();

        String newPasswordHash = body != null ? body.get("newPasswordHash") : null;
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("newPasswordHash 不能为空");
        }

        service.resetPassword(tenantId, userId, newPasswordHash, operator.operator());
        return ApiResponse.ok(Map.of("userId", userId, "status", "PASSWORD_RESET"));
    }

    /**
     * 强制用户登出。
     */
    @PostMapping("/{userId}/force-logout")
    public ApiResponse<Object> forceLogout(@PathVariable Long userId) {
        KernelAdminTenantService service = requireService();
        CurrentUser operator = currentUserPort.requireCurrentUser();
        String tenantId = operator.effectiveTenantId();
        service.forceLogout(tenantId, userId, operator.operator());
        return ApiResponse.ok(Map.of("userId", userId, "status", "LOGGED_OUT"));
    }

    private KernelAdminTenantService requireService() {
        KernelAdminTenantService service = adminServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin service not available");
        }
        return service;
    }
}
