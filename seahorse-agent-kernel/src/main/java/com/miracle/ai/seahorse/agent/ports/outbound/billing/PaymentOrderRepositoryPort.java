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

import com.miracle.ai.seahorse.agent.kernel.domain.billing.PaymentOrder;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for payment order persistence.
 */
public interface PaymentOrderRepositoryPort {

    /**
     * Saves (inserts or updates) a payment order.
     *
     * @param order the payment order to save
     * @return the saved payment order
     */
    PaymentOrder save(PaymentOrder order);

    /**
     * Finds a payment order by its unique order number.
     *
     * @param orderNo the order number
     * @return the payment order, or empty if not found
     */
    Optional<PaymentOrder> findByOrderNo(String orderNo);

    /**
     * Pessimistically locks and retrieves a payment order by order number.
     * Used during callback processing to prevent concurrent state transitions.
     *
     * @param orderNo the order number
     * @return the locked payment order, or empty if not found
     */
    Optional<PaymentOrder> lockByOrderNo(String orderNo);

    /**
     * Finds payment orders for a tenant with pagination.
     *
     * @param tenantId the tenant identifier
     * @param page     page number (zero-based)
     * @param size     page size
     * @return paginated list of payment orders
     */
    List<PaymentOrder> findByTenantId(String tenantId, int page, int size);
}
