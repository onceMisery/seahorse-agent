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

import com.miracle.ai.seahorse.agent.kernel.domain.audit.AuditLog;

import java.time.Instant;
import java.util.List;

/**
 * 审计日志仓储端口。
 */
public interface AuditLogRepositoryPort {

    /**
     * 保存审计日志。
     */
    Long save(AuditLog log);

    /**
     * 分页查询审计日志（支持多条件过滤）。
     */
    List<AuditLog> queryLogs(String tenantId, String action, String resourceType,
                             String operator, Instant startTime, Instant endTime,
                             int page, int size);

    /**
     * 查询符合条件的日志总数。
     */
    long countLogs(String tenantId, String action, String resourceType,
                   String operator, Instant startTime, Instant endTime);

    /**
     * 清理指定时间之前的日志。
     */
    int deleteOlderThan(Instant cutoff);
}
