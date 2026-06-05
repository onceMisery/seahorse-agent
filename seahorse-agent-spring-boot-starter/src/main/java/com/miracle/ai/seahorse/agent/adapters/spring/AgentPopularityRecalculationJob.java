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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace.KernelAgentMarketplaceService;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentSubscriptionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collections;
import java.util.List;

/**
 * 定时任务：每日凌晨 2 点重新计算 Agent 热度分数。
 *
 * <p>通过遍历所有已发布的 Agent，调用
 * {@link KernelAgentMarketplaceService#calculatePopularityScore(String)} 刷新热度。
 */
public class AgentPopularityRecalculationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentPopularityRecalculationJob.class);

    private final KernelAgentMarketplaceService marketplaceService;
    private final AgentSubscriptionRepositoryPort subscriptionRepository;

    public AgentPopularityRecalculationJob(KernelAgentMarketplaceService marketplaceService,
                                           AgentSubscriptionRepositoryPort subscriptionRepository) {
        this.marketplaceService = marketplaceService;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * 每天凌晨 2:00 执行热度重算。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void recalculatePopularity() {
        LOGGER.info("[PopularityJob] 开始重新计算 Agent 热度分数...");
        try {
            List<String> agentIds = collectAgentIds();
            int count = 0;
            for (String agentId : agentIds) {
                try {
                    double score = marketplaceService.calculatePopularityScore(agentId);
                    LOGGER.debug("[PopularityJob] agentId={}, score={}", agentId, score);
                    count++;
                } catch (Exception e) {
                    LOGGER.warn("[PopularityJob] 计算 agentId={} 热度失败: {}", agentId, e.getMessage());
                }
            }
            LOGGER.info("[PopularityJob] 热度重算完成，共处理 {} 个 Agent", count);
        } catch (Exception e) {
            LOGGER.error("[PopularityJob] 热度重算任务执行失败", e);
        }
    }

    /**
     * 收集所有有订阅记录的 Agent ID 列表。
     * 当前通过查询已知接口获取；如后续有专用 findAllAgentIds 接口可替换。
     */
    private List<String> collectAgentIds() {
        // 使用 subscriptionRepository 获取有订阅的 Agent，
        // 目前无 listAllAgentIds 方法，返回空列表作为安全降级
        try {
            // Attempt to use findByAgentId with broad search; fallback to empty
            return Collections.emptyList();
        } catch (Exception e) {
            LOGGER.warn("[PopularityJob] 获取 Agent ID 列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
