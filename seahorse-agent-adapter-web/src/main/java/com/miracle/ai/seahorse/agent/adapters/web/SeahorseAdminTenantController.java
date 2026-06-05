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
import com.miracle.ai.seahorse.agent.ports.outbound.admin.TenantDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 — 租户管理控制器。
 *
 * <p>提供租户列表、详情、挂起、删除、用户管理等管理操作。
 */
@RestController
@RequestMapping("/api/admin/tenants")
public class SeahorseAdminTenantController {

    private final ObjectProvider<KernelAdminTenantService> adminServiceProvider;
    private final CurrentUserPort currentUserPort;

    public SeahorseAdminTenantController(ObjectProvider<KernelAdminTenantService> adminServiceProvider,
                                         CurrentUserPort currentUserPort) {
        this.adminServiceProvider = adminServiceProvider;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public ApiResponse<TenantListResponse> listTenants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        KernelAdminTenantService service = requireService();
        List<TenantDetail> tenants = service.listTenants(page, size, status);
        long total = service.countTenants(status);
        return ApiResponse.ok(new TenantListResponse(
                tenants.stream().map(TenantResponse::from).toList(), total, page, size));
    }

    @GetMapping("/{tenantId}")
    public ApiResponse<TenantResponse> getTenantDetail(@PathVariable String tenantId) {
        KernelAdminTenantService service = requireService();
        return ApiResponse.ok(TenantResponse.from(service.getTenantDetail(tenantId)));
    }

    @PutMapping("/{tenantId}/suspend")
    public ApiResponse<Map<String, String>> suspendTenant(@PathVariable String tenantId) {
        KernelAdminTenantService service = requireService();
        CurrentUser operator = currentUserPort.requireCurrentUser();
        service.suspendTenant(tenantId, operator.operator());
        return ApiResponse.ok(Map.of("tenantId", tenantId, "status", "SUSPENDED"));
    }

    @DeleteMapping("/{tenantId}")
    public ApiResponse<Map<String, String>> deleteTenant(@PathVariable String tenantId) {
        KernelAdminTenantService service = requireService();
        CurrentUser operator = currentUserPort.requireCurrentUser();
        service.deleteTenant(tenantId, operator.operator());
        return ApiResponse.ok(Map.of("tenantId", tenantId, "status", "DELETED"));
    }

    @GetMapping("/{tenantId}/users")
    public ApiResponse<UserListResponse> listUsersByTenant(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        KernelAdminTenantService service = requireService();
        List<Map<String, Object>> users = service.listUsersByTenant(tenantId, page, size);
        long total = service.countUsersByTenant(tenantId);
        return ApiResponse.ok(new UserListResponse(users, total, page, size));
    }

    private KernelAdminTenantService requireService() {
        KernelAdminTenantService service = adminServiceProvider != null
                ? adminServiceProvider.getIfAvailable() : null;
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin service not available");
        }
        return service;
    }

    public record TenantResponse(String tenantId,
                                 String tenantName,
                                 String status,
                                 String planCode,
                                 String contactEmail,
                                 long userCount,
                                 long agentCount,
                                 long knowledgeBaseCount,
                                 Instant createdAt,
                                 Instant updatedAt) {

        static TenantResponse from(TenantDetail detail) {
            return new TenantResponse(
                    detail.tenantId(),
                    detail.tenantName(),
                    detail.status(),
                    detail.planCode(),
                    detail.contactEmail(),
                    detail.userCount(),
                    detail.agentCount(),
                    detail.knowledgeBaseCount(),
                    detail.createdAt(),
                    detail.updatedAt());
        }
    }

    public record TenantListResponse(List<TenantResponse> records, long total, int page, int size) {
    }

    public record UserListResponse(List<Map<String, Object>> records, long total, int page, int size) {
    }
}
