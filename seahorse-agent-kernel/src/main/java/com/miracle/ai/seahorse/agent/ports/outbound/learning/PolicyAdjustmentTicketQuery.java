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

package com.miracle.ai.seahorse.agent.ports.outbound.learning;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;

/**
 * 落地工单分页查询条件。未指定状态时返回全部——工单量级很小，默认不做状态过滤。
 */
public record PolicyAdjustmentTicketQuery(String tenantId,
                                          PolicyAdjustmentTicketStatus status,
                                          long current,
                                          long size) {

    public static final long DEFAULT_CURRENT = 1L;
    public static final long DEFAULT_PAGE_SIZE = 20L;

    public PolicyAdjustmentTicketQuery(String tenantId, PolicyAdjustmentTicketStatus status) {
        this(tenantId, status, DEFAULT_CURRENT, DEFAULT_PAGE_SIZE);
    }
}
