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

package com.miracle.ai.seahorse.agent.kernel.domain.billing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment order representing a purchase of a subscription plan.
 *
 * <p>Follows a state machine: PENDING → PAYING → PAID / CANCELED / FAILED.
 * State transitions are immutable — each returns a new record instance.
 *
 * @param id              primary key
 * @param orderNo         unique order number (UUID-based)
 * @param tenantId        owning tenant
 * @param planCode        plan being purchased
 * @param paymentChannel  payment channel: ALIPAY / WECHAT
 * @param status          order status: PENDING / PAYING / PAID / CANCELED / REFUNDED / FAILED
 * @param amount          payment amount
 * @param channelTradeNo  trade number from the payment channel (set after payment)
 * @param createdAt       order creation timestamp
 * @param paidAt          payment completion timestamp
 */
public record PaymentOrder(Long id,
                           String orderNo,
                           String tenantId,
                           PlanCode planCode,
                           String paymentChannel,
                           String status,
                           BigDecimal amount,
                           String channelTradeNo,
                           Instant createdAt,
                           Instant paidAt) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAYING = "PAYING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * Transition from PENDING to PAYING.
     *
     * @return new PaymentOrder in PAYING status
     * @throws IllegalStateException if current status is not PENDING
     */
    public PaymentOrder markPaying() {
        if (!STATUS_PENDING.equals(status)) {
            throw new IllegalStateException(
                    "Cannot transition to PAYING from status: " + status);
        }
        return new PaymentOrder(id, orderNo, tenantId, planCode, paymentChannel,
                STATUS_PAYING, amount, channelTradeNo, createdAt, paidAt);
    }

    /**
     * Transition from PAYING to PAID.
     *
     * @param tradeNo the channel trade number confirming payment
     * @return new PaymentOrder in PAID status
     * @throws IllegalStateException if current status is not PAYING
     */
    public PaymentOrder markPaid(String tradeNo) {
        if (!STATUS_PAYING.equals(status)) {
            throw new IllegalStateException(
                    "Cannot transition to PAID from status: " + status);
        }
        return new PaymentOrder(id, orderNo, tenantId, planCode, paymentChannel,
                STATUS_PAID, amount, tradeNo, createdAt, Instant.now());
    }

    /**
     * Transition to CANCELED (from PENDING or PAYING).
     *
     * @return new PaymentOrder in CANCELED status
     * @throws IllegalStateException if current status does not allow cancellation
     */
    public PaymentOrder markCanceled() {
        if (!STATUS_PENDING.equals(status) && !STATUS_PAYING.equals(status)) {
            throw new IllegalStateException(
                    "Cannot cancel order from status: " + status);
        }
        return new PaymentOrder(id, orderNo, tenantId, planCode, paymentChannel,
                STATUS_CANCELED, amount, channelTradeNo, createdAt, paidAt);
    }

    /**
     * Transition to FAILED.
     *
     * @param reason failure reason (logged, not stored on record)
     * @return new PaymentOrder in FAILED status
     * @throws IllegalStateException if current status is terminal
     */
    public PaymentOrder markFailed(String reason) {
        if (STATUS_PAID.equals(status) || STATUS_REFUNDED.equals(status)) {
            throw new IllegalStateException(
                    "Cannot fail order from terminal status: " + status);
        }
        return new PaymentOrder(id, orderNo, tenantId, planCode, paymentChannel,
                STATUS_FAILED, amount, channelTradeNo, createdAt, paidAt);
    }
}
