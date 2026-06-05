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

package com.miracle.ai.seahorse.agent.ports.outbound.billing;

import com.miracle.ai.seahorse.agent.kernel.domain.billing.Subscription;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for subscription persistence.
 */
public interface SubscriptionRepositoryPort {

    /**
     * Finds all subscriptions for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return subscriptions for the tenant
     */
    List<Subscription> findByTenantId(String tenantId);

    /**
     * Saves (inserts or updates) a subscription.
     *
     * @param subscription the subscription to save
     * @return the saved subscription
     */
    Subscription save(Subscription subscription);

    /**
     * Finds all active subscriptions across all tenants.
     *
     * @return active subscriptions
     */
    List<Subscription> findAllActive();

    /**
     * Finds the active subscription for a tenant, if any.
     *
     * @param tenantId the tenant identifier
     * @return the active subscription, or empty
     */
    Optional<Subscription> findActiveByTenantId(String tenantId);
}
