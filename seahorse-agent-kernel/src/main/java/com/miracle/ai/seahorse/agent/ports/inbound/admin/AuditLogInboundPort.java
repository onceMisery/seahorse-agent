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

package com.miracle.ai.seahorse.agent.ports.inbound.admin;

import com.miracle.ai.seahorse.agent.kernel.domain.audit.AuditLog;

import java.time.Instant;
import java.util.List;

/**
 * 审计日志查询与记录入站用例端口。
 *
 * <p>Web 适配器依赖该端口而不是具体 {@code KernelAuditLogService} 实现，
 * 保持「Web 依赖入站用例契约，而非 Kernel 服务实现」的依赖方向。</p>
 */
public interface AuditLogInboundPort {

    Long recordAction(AuditLog log);

    Long recordAction(String tenantId, String operator, String action,
                      String resourceType, String resourceId, String detail,
                      String ipAddress, String userAgent);

    List<AuditLog> queryLogs(String tenantId, String action, String resourceType,
                             String operator, Instant startTime, Instant endTime,
                             int page, int size);

    long countLogs(String tenantId, String action, String resourceType,
                   String operator, Instant startTime, Instant endTime);

    int cleanupOldLogs(Instant cutoff);
}
