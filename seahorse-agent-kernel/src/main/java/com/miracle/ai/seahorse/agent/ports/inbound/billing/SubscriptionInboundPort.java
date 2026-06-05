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

package com.miracle.ai.seahorse.agent.ports.inbound.billing;

import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.Subscription;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.SubscriptionPlan;

import java.util.List;

/**
 * Inbound port for subscription management operations.
 */
public interface SubscriptionInboundPort {

    /**
     * Lists all available subscription plans.
     *
     * @return all active subscription plans
     */
    List<SubscriptionPlan> listPlans();

    /**
     * Retrieves the current active subscription for the given tenant.
     *
     * @param tenantId the tenant identifier
     * @return the active subscription, or {@code null} if none
     */
    Subscription getActiveSubscription(String tenantId);

    /**
     * Creates a new subscription for the given tenant and plan.
     *
     * @param tenantId the tenant identifier
     * @param planCode the plan to subscribe to
     * @return the newly created subscription
     */
    Subscription subscribe(String tenantId, PlanCode planCode);
}
