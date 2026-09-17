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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReview;

import java.util.List;
import java.util.Map;

/**
 * 工单回访记录的仓储端口（追加写）。
 *
 * <p>回访记录是<b>只追加</b>的：一张工单在其生命周期里会被回访多次，历史结论不能互相覆盖，
 * 否则无法回答"上一次它是被判定为改善还是回归"。
 *
 * <p>{@code (ticket_id, batch_id)} 上是唯一索引，{@link #save} 必须做到"重复批次直接忽略"
 * （{@code INSERT ... ON CONFLICT DO NOTHING}）——幂等的最后一道防线放在数据库，
 * 因为应用层的"先查再写"在并发下拦不住重复。
 */
public interface PolicyAdjustmentTicketReviewRepositoryPort {

    /**
     * 追加一条回访记录。同一 {@code (ticketId, batchId)} 已存在时应静默忽略。
     */
    void save(PolicyAdjustmentTicketReview review);

    List<PolicyAdjustmentTicketReview> listByTicket(String ticketId, int limit);

    /**
     * 批量取"每张工单最近一次回访"，避免逐张查询。
     *
     * @return ticketId → 该工单最近一次回访；没有任何回访的工单不在 map 里
     */
    Map<String, PolicyAdjustmentTicketReview> latestByTickets(List<String> ticketIds);

    static PolicyAdjustmentTicketReviewRepositoryPort empty() {
        return new PolicyAdjustmentTicketReviewRepositoryPort() {
            @Override
            public void save(PolicyAdjustmentTicketReview review) {
            }

            @Override
            public List<PolicyAdjustmentTicketReview> listByTicket(String ticketId, int limit) {
                return List.of();
            }

            @Override
            public Map<String, PolicyAdjustmentTicketReview> latestByTickets(List<String> ticketIds) {
                return Map.of();
            }
        };
    }
}
