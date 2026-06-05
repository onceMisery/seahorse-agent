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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace.RevenueService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.RevenueShare;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST controller for marketplace revenue operations: creator earnings and admin settlement.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/marketplace/revenue/my-earnings} — current user's all-time earnings</li>
 *   <li>{@code GET /api/marketplace/revenue/my-earnings/{period}} — earnings for a specific period</li>
 *   <li>{@code POST /api/admin/marketplace/revenue/settle/{period}} — trigger monthly settlement (admin)</li>
 * </ul>
 */
@RestController
public class SeahorseRevenueController {

    private final ObjectProvider<RevenueService> revenueServiceProvider;
    private final CurrentUserPort currentUserPort;

    public SeahorseRevenueController(ObjectProvider<RevenueService> revenueServiceProvider,
                                      CurrentUserPort currentUserPort) {
        this.revenueServiceProvider = revenueServiceProvider;
        this.currentUserPort = currentUserPort;
    }

    /**
     * Returns the current user's all-time earnings across all statuses.
     */
    @GetMapping("/api/marketplace/revenue/my-earnings")
    public ApiResponse<EarningsSummary> myEarnings() {
        CurrentUser user = currentUserPort.requireCurrentUser();
        return ApiResponses.requireService(revenueServiceProvider,
                svc -> {
                    List<RevenueShare> earnings = svc.getCreatorEarnings(user.userId(), null);
                    return EarningsSummary.from(earnings);
                });
    }

    /**
     * Returns the current user's earnings for a specific billing period.
     *
     * @param period the billing period in yyyy-MM format
     */
    @GetMapping("/api/marketplace/revenue/my-earnings/{period}")
    public ApiResponse<List<EarningsRecord>> myEarningsByPeriod(@PathVariable String period) {
        CurrentUser user = currentUserPort.requireCurrentUser();
        return ApiResponses.requireService(revenueServiceProvider,
                svc -> svc.getCreatorEarnings(user.userId(), null).stream()
                        .filter(r -> period.equals(r.period()))
                        .map(EarningsRecord::from)
                        .toList());
    }

    /**
     * Triggers monthly revenue settlement for all PENDING shares in the given period.
     * Requires admin privileges.
     *
     * @param period the billing period in yyyy-MM format
     */
    @PostMapping("/api/admin/marketplace/revenue/settle/{period}")
    public ApiResponse<SettlementResult> settleMonth(@PathVariable String period) {
        currentUserPort.requireCurrentUser();
        currentUserPort.requireRole("ADMIN");
        return ApiResponses.requireService(revenueServiceProvider,
                svc -> {
                    int settled = svc.settleMonth(period);
                    return new SettlementResult(period, settled);
                });
    }

    /**
     * Summary of a creator's earnings across all records.
     */
    public record EarningsSummary(BigDecimal totalGrossRevenue,
                                   BigDecimal totalPlatformShare,
                                   BigDecimal totalCreatorShare,
                                   int recordCount) {

        static EarningsSummary from(List<RevenueShare> earnings) {
            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal platform = BigDecimal.ZERO;
            BigDecimal creator = BigDecimal.ZERO;
            for (RevenueShare r : earnings) {
                gross = gross.add(r.grossRevenue());
                platform = platform.add(r.platformShare());
                creator = creator.add(r.creatorShare());
            }
            return new EarningsSummary(gross, platform, creator, earnings.size());
        }
    }

    /**
     * A single earnings record for a creator in a specific period.
     */
    public record EarningsRecord(Long id,
                                  String agentId,
                                  String period,
                                  BigDecimal grossRevenue,
                                  BigDecimal platformShare,
                                  BigDecimal creatorShare,
                                  String status) {

        static EarningsRecord from(RevenueShare share) {
            return new EarningsRecord(
                    share.id(), share.agentId(), share.period(),
                    share.grossRevenue(), share.platformShare(),
                    share.creatorShare(), share.status());
        }
    }

    /**
     * Result of a monthly settlement operation.
     */
    public record SettlementResult(String period, int settledCount) {
    }
}
