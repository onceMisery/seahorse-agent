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

import com.miracle.ai.seahorse.agent.kernel.application.admin.KernelAuditLogService;
import com.miracle.ai.seahorse.agent.kernel.domain.audit.AuditLog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 管理后台 — 审计日志查询控制器。
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class SeahorseAuditLogController {

    private final ObjectProvider<KernelAuditLogService> auditLogServiceProvider;

    public SeahorseAuditLogController(ObjectProvider<KernelAuditLogService> auditLogServiceProvider) {
        this.auditLogServiceProvider = auditLogServiceProvider;
    }

    @GetMapping
    public ApiResponse<AuditLogPageResponse> queryLogs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.requireService(auditLogServiceProvider, svc -> {
            Instant start = parseInstant(startTime);
            Instant end = parseInstant(endTime);
            List<AuditLog> logs = svc.queryLogs(tenantId, action, resourceType, operator,
                    start, end, page, size);
            long total = svc.countLogs(tenantId, action, resourceType, operator, start, end);
            return new AuditLogPageResponse(
                    logs.stream().map(AuditLogResponse::from).toList(), total, page, size);
        });
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    public record AuditLogResponse(Long id,
                                   String tenantId,
                                   String operator,
                                   String action,
                                   String resourceType,
                                   String resourceId,
                                   String detail,
                                   String ipAddress,
                                   String userAgent,
                                   Instant createdAt) {

        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(
                    log.id(),
                    log.tenantId(),
                    log.operator(),
                    log.action(),
                    log.resourceType(),
                    log.resourceId(),
                    log.detail(),
                    log.ipAddress(),
                    log.userAgent(),
                    log.createdAt());
        }
    }

    public record AuditLogPageResponse(List<AuditLogResponse> records, long total, int page, int size) {
    }
}
