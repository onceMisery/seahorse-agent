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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;

import java.util.List;

/**
 * 落地工单分页结果。
 */
public record PolicyAdjustmentTicketPage(List<PolicyAdjustmentTicket> records,
                                         long total,
                                         long size,
                                         long current,
                                         long pages) {

    public PolicyAdjustmentTicketPage {
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static PolicyAdjustmentTicketPage empty() {
        return new PolicyAdjustmentTicketPage(
                List.of(), 0L, PolicyAdjustmentTicketQuery.DEFAULT_PAGE_SIZE,
                PolicyAdjustmentTicketQuery.DEFAULT_CURRENT, 0L);
    }
}
