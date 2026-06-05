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

package com.miracle.ai.seahorse.agent.kernel.application.billing;

import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.Subscription;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.SubscriptionPlan;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.SubscriptionInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionPlanRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionRepositoryPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Kernel service for subscription management.
 *
 * <p>Handles plan listing, active subscription lookup, and new subscription creation.
 * New subscriptions are created from plan templates with a default 30-day expiry.
 */
public class KernelSubscriptionService implements SubscriptionInboundPort {

    private static final int DEFAULT_SUBSCRIPTION_DAYS = 30;

    private final SubscriptionPlanRepositoryPort planRepository;
    private final SubscriptionRepositoryPort subscriptionRepository;

    public KernelSubscriptionService(SubscriptionPlanRepositoryPort planRepository,
                                     SubscriptionRepositoryPort subscriptionRepository) {
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository must not be null");
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository, "subscriptionRepository must not be null");
    }

    @Override
    public List<SubscriptionPlan> listPlans() {
        return planRepository.findAll();
    }

    @Override
    public Subscription getActiveSubscription(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return subscriptionRepository.findActiveByTenantId(tenantId)
                .filter(Subscription::isActive)
                .filter(sub -> !sub.isExpired(Instant.now()))
                .orElse(null);
    }

    @Override
    public Subscription subscribe(String tenantId, PlanCode planCode) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(planCode, "planCode must not be null");

        SubscriptionPlan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan code: " + planCode));

        Instant now = Instant.now();
        Subscription subscription = new Subscription(
                null,
                tenantId,
                plan.code(),
                Subscription.STATUS_ACTIVE,
                now,
                now.plus(DEFAULT_SUBSCRIPTION_DAYS, ChronoUnit.DAYS),
                plan.tokenLimit(),
                plan.storageLimitBytes(),
                plan.concurrencyLimit()
        );

        return subscriptionRepository.save(subscription);
    }
}
