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
import java.util.Optional;
import java.util.Set;

/**
 * 落地工单的只读查询端口。
 */
public interface PolicyAdjustmentTicketQueryPort {

    Optional<PolicyAdjustmentTicket> findById(String ticketId);

    PolicyAdjustmentTicketPage page(PolicyAdjustmentTicketQuery query);

    /**
     * 判断这些指纹是否已经开过工单，用于闭环每轮去重。
     *
     * @return 已存在的指纹子集
     */
    Set<String> existingFingerprints(String tenantId, List<String> fingerprints);

    static PolicyAdjustmentTicketQueryPort empty() {
        return new PolicyAdjustmentTicketQueryPort() {
            @Override
            public Optional<PolicyAdjustmentTicket> findById(String ticketId) {
                return Optional.empty();
            }

            @Override
            public PolicyAdjustmentTicketPage page(PolicyAdjustmentTicketQuery query) {
                return PolicyAdjustmentTicketPage.empty();
            }

            @Override
            public Set<String> existingFingerprints(String tenantId, List<String> fingerprints) {
                return Set.of();
            }
        };
    }
}
