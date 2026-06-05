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
import com.miracle.ai.seahorse.agent.ports.outbound.admin.AuditLogRepositoryPort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 审计日志管理服务，支持日志记录和查询。
 */
public class KernelAuditLogService {

    private final AuditLogRepositoryPort repositoryPort;

    public KernelAuditLogService(AuditLogRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    /**
     * 记录审计日志。
     */
    public Long recordAction(AuditLog log) {
        if (log == null) {
            throw new IllegalArgumentException("log 不能为空");
        }
        return repositoryPort.save(log);
    }

    /**
     * 记录审计日志（简化版）。
     */
    public Long recordAction(String tenantId, String operator, String action,
                             String resourceType, String resourceId, String detail,
                             String ipAddress, String userAgent) {
        AuditLog log = AuditLog.create(tenantId, operator, action, resourceType,
                resourceId, detail, ipAddress, userAgent);
        return repositoryPort.save(log);
    }

    /**
     * 分页查询审计日志。
     *
     * @param tenantId     租户 ID（可选，null 表示所有租户）
     * @param action       操作类型（可选）
     * @param resourceType 资源类型（可选）
     * @param operator     操作人（可选）
     * @param startTime    开始时间（可选）
     * @param endTime      结束时间（可选）
     * @param page         页码
     * @param size         每页大小
     * @return 日志列表
     */
    public List<AuditLog> queryLogs(String tenantId, String action, String resourceType,
                                    String operator, Instant startTime, Instant endTime,
                                    int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        return repositoryPort.queryLogs(tenantId, action, resourceType, operator,
                startTime, endTime, page, size);
    }

    /**
     * 查询符合条件的日志总数。
     */
    public long countLogs(String tenantId, String action, String resourceType,
                          String operator, Instant startTime, Instant endTime) {
        return repositoryPort.countLogs(tenantId, action, resourceType, operator,
                startTime, endTime);
    }

    /**
     * 清理旧日志。
     *
     * @param cutoff 截止时间，此时间之前的日志将被删除
     * @return 删除的日志数量
     */
    public int cleanupOldLogs(Instant cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff 不能为空");
        }
        return repositoryPort.deleteOlderThan(cutoff);
    }
}
