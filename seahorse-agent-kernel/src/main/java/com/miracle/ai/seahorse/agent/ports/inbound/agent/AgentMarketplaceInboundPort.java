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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentPublishReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentSubscription;

import java.util.List;

/**
 * Agent 市场发布、订阅与评分入站用例端口。
 *
 * <p>Web 适配器依赖该端口而不是具体 {@code KernelAgentMarketplaceService} 实现，
 * 保持「Web 依赖入站用例契约，而非 Kernel 服务实现」的依赖方向。</p>
 */
public interface AgentMarketplaceInboundPort {

    Long submitForReview(String agentId, String submittedBy);

    List<AgentPublishReview> listPendingReviews(int page, int size);

    void approve(Long reviewId, String reviewer, String comment);

    void reject(Long reviewId, String reviewer, String comment);

    Long subscribe(String agentId, Long userId);

    void unsubscribe(String agentId, Long userId);

    List<AgentSubscription> mySubscriptions(Long userId, boolean activeOnly);

    Long rate(String agentId, Long userId, int rating, String comment);
}
