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
import com.miracle.ai.seahorse.agent.kernel.domain.billing.PaymentOrder;

import java.util.Map;

/**
 * Inbound port for payment processing operations.
 */
public interface PaymentInboundPort {

    /**
     * Creates a new payment order for the given tenant and plan.
     *
     * @param tenantId the tenant identifier
     * @param planCode the plan to purchase
     * @param channel  payment channel (ALIPAY / WECHAT)
     * @return the created payment order
     */
    PaymentOrder createOrder(String tenantId, PlanCode planCode, String channel);

    /**
     * Retrieves the current status of a payment order.
     *
     * @param orderNo the unique order number
     * @return the payment order, or {@code null} if not found
     */
    PaymentOrder getOrderStatus(String orderNo);

    /**
     * Handles an asynchronous payment callback from the payment channel.
     *
     * <p>Implements triple idempotency: verify signature, check callback log,
     * lock order and verify amount before state transition.
     *
     * @param channel the payment channel (ALIPAY / WECHAT)
     * @param params  callback parameters from the payment channel
     * @return the updated payment order after processing
     */
    PaymentOrder handleCallback(String channel, Map<String, String> params);
}
