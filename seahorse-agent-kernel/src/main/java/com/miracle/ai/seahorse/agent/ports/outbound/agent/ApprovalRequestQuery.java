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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;

/**
 * 审批请求分页查询条件。
 *
 * @param tenantId 租户 ID 过滤条件，可为空
 * @param status   审批状态，默认 PENDING
 * @param current  当前页码，从 1 开始
 * @param size     每页大小
 */
public record ApprovalRequestQuery(String tenantId,
                                   ApprovalRequestStatus status,
                                   long current,
                                   long size) {

    public static final long DEFAULT_CURRENT = 1L;
    public static final long DEFAULT_PAGE_SIZE = 10L;
    public static final ApprovalRequestStatus DEFAULT_STATUS = ApprovalRequestStatus.PENDING;

    public ApprovalRequestQuery {
        tenantId = trimToNull(tenantId);
        status = status == null ? DEFAULT_STATUS : status;
        current = current <= 0 ? DEFAULT_CURRENT : current;
        size = size <= 0 ? DEFAULT_PAGE_SIZE : size;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

