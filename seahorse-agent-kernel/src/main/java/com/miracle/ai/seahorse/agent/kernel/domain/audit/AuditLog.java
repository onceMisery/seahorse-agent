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

package com.miracle.ai.seahorse.agent.kernel.domain.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * 系统审计日志，记录关键操作以便追踪和合规。
 *
 * @param id           日志主键
 * @param tenantId     租户 ID
 * @param operator     操作人
 * @param action       操作类型（如 CREATE, UPDATE, DELETE, LOGIN, LOGOUT）
 * @param resourceType 资源类型（如 AGENT, KNOWLEDGE_BASE, USER）
 * @param resourceId   资源 ID
 * @param detail       操作详情（JSON 格式）
 * @param ipAddress    客户端 IP
 * @param userAgent    客户端 User-Agent
 * @param createdAt    创建时间
 */
public record AuditLog(Long id,
                       String tenantId,
                       String operator,
                       String action,
                       String resourceType,
                       String resourceId,
                       String detail,
                       String ipAddress,
                       String userAgent,
                       Instant createdAt) {

    public AuditLog {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action 不能为空");
        }
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType 不能为空");
        }
        resourceId = resourceId == null ? "" : resourceId;
        detail = detail == null ? "" : detail;
        ipAddress = ipAddress == null ? "" : ipAddress;
        userAgent = userAgent == null ? "" : userAgent;
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
    }

    /**
     * 创建审计日志。
     */
    public static AuditLog create(String tenantId, String operator, String action,
                                   String resourceType, String resourceId, String detail,
                                   String ipAddress, String userAgent) {
        return new AuditLog(null, tenantId, operator, action, resourceType, resourceId,
                detail, ipAddress, userAgent, Instant.now());
    }
}
