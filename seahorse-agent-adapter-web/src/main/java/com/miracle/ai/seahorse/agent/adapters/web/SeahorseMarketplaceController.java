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

import com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace.KernelAgentMarketplaceService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentPublishReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentRating;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentSubscription;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCatalogQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
public class SeahorseMarketplaceController {

    private final ObjectProvider<KernelAgentMarketplaceService> marketplaceServiceProvider;
    private final ObjectProvider<AgentCatalogQueryPort> catalogQueryPortProvider;
    private final CurrentUserPort currentUserPort;

    public SeahorseMarketplaceController(ObjectProvider<KernelAgentMarketplaceService> marketplaceServiceProvider,
                                         ObjectProvider<AgentCatalogQueryPort> catalogQueryPortProvider,
                                         CurrentUserPort currentUserPort) {
        this.marketplaceServiceProvider = marketplaceServiceProvider;
        this.catalogQueryPortProvider = catalogQueryPortProvider;
        this.currentUserPort = currentUserPort;
    }

    @PostMapping({"/marketplace/agents/{agentId}/publish", "/api/marketplace/agents/{agentId}/publish"})
    public ApiResponse<Long> submitForReview(@PathVariable String agentId) {
        CurrentUser user = currentUserPort.requireCurrentUser();
        return ApiResponses.requireService(marketplaceServiceProvider,
                svc -> svc.submitForReview(agentId, user.operator()));
    }

    @GetMapping({"/marketplace/reviews/pending", "/api/marketplace/reviews/pending"})
    public ApiResponse<List<ReviewResponse>> listPendingReviews(@RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.requireService(marketplaceServiceProvider,
                svc -> svc.listPendingReviews(page, size).stream()
                        .map(ReviewResponse::from)
                        .toList());
    }

    @PutMapping({"/marketplace/reviews/{reviewId}/approve", "/api/marketplace/reviews/{reviewId}/approve"})
    public ApiResponse<Void> approve(@PathVariable Long reviewId,
                                     @RequestBody(required = false) ReviewActionRequest request) {
        CurrentUser user = currentUserPort.requireCurrentUser();
        ReviewActionRequest safe = request == null ? new ReviewActionRequest(null) : request;
        return ApiResponses.requireService(marketplaceServiceProvider,
                svc -> {
                    svc.approve(reviewId, user.operator(), safe.comment());
                    return null;
                });
    }

    @PutMapping({"/marketplace/reviews/{reviewId}/reject", "/api/marketplace/reviews/{reviewId}/reject"})
    public ApiResponse<Void> reject(@PathVariable Long reviewId,
                                    @RequestBody(required = false) ReviewActionRequest request) {
        CurrentUser user = currentUserPort.requireCurrentUser();
        ReviewActionRequest safe = request == null ? new ReviewActionRequest(null) : request;
        return ApiResponses.requireService(marketplaceServiceProvider,
                svc -> {
                    svc.reject(reviewId, user.operator(), safe.comment());
                    return null;
                });
    }

    @GetMapping({"/marketplace/agents", "/api/marketplace/agents"})
    public ApiResponse<Object> listMarketplace(@RequestParam(required = false) String category,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        AgentCatalogQueryPort catalogPort = catalogQueryPortProvider != null
                ? catalogQueryPortProvider.getIfAvailable() : null;
        if (catalogPort == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service not available");
        }
        CurrentUser user = currentUserPort.requireCurrentUser();
        AgentCatalogQuery query = new AgentCatalogQuery(user.tenantId(), category, page, size);
        return ApiResponse.ok(catalogPort.page(query));
    }

    @PostMapping({"/marketplace/agents/{agentId}/subscribe", "/api/marketplace/agents/{agentId}/subscribe"})
    public ApiResponse<Long> subscribe(@PathVariable String agentId) {
        CurrentUser user = currentUserPort.requireCurrentUser();
        return ApiResponses.requireService(marketplaceServiceProvider,
                svc -> svc.subscribe(agentId, user.userId()));
    }

    @DeleteMapping({"/marketplace/agents/{agentId}/subscribe", "/api/marketplace/agents/{agentId}/subscribe"})
    public ApiResponse<Void> unsubscribe(@PathVariable String agentId) {
        CurrentUser user = currentUserPort.requireCurrentUser();
        return ApiResponses.requireService(marketplaceServiceProvider,
                svc -> {
                    svc.unsubscribe(agentId, user.userId());
                    return null;
                });
    }

    @GetMapping({"/marketplace/agents/my-subscriptions", "/api/marketplace/agents/my-subscriptions"})
    public ApiResponse<List<SubscriptionResponse>> mySubscriptions(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        CurrentUser user = currentUserPort.requireCurrentUser();
        return ApiResponses.requireService(marketplaceServiceProvider,
                svc -> svc.mySubscriptions(user.userId(), activeOnly).stream()
                        .map(SubscriptionResponse::from)
                        .toList());
    }

    @PostMapping({"/marketplace/agents/{agentId}/ratings", "/api/marketplace/agents/{agentId}/ratings"})
    public ApiResponse<Long> rate(@PathVariable String agentId,
                                  @RequestBody RateRequest request) {
        CurrentUser user = currentUserPort.requireCurrentUser();
        RateRequest safe = request == null ? new RateRequest(5, null) : request;
        return ApiResponses.requireService(marketplaceServiceProvider,
                svc -> svc.rate(agentId, user.userId(), safe.rating(), safe.comment()));
    }

    public record ReviewActionRequest(String comment) {
    }

    public record RateRequest(int rating, String comment) {
    }

    public record ReviewResponse(Long id,
                                 String agentId,
                                 String submittedBy,
                                 String status,
                                 String reviewComment,
                                 Instant submittedAt,
                                 Instant reviewedAt) {

        static ReviewResponse from(AgentPublishReview review) {
            return new ReviewResponse(
                    review.id(), review.agentId(), review.submittedBy(), review.status(),
                    review.reviewComment(), review.submittedAt(), review.reviewedAt());
        }
    }

    public record SubscriptionResponse(Long id,
                                       String agentId,
                                       Long userId,
                                       String tenantId,
                                       Instant subscribedAt,
                                       boolean active) {

        static SubscriptionResponse from(AgentSubscription sub) {
            return new SubscriptionResponse(
                    sub.id(), sub.agentId(), sub.userId(), sub.tenantId(),
                    sub.subscribedAt(), sub.active());
        }
    }
}
