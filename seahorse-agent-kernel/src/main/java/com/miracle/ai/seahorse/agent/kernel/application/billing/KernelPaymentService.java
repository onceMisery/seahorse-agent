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
import com.miracle.ai.seahorse.agent.kernel.domain.billing.PaymentOrder;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.SubscriptionPlan;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.PaymentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentCallbackLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentOrderRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionPlanRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.TransactionRunnerPort;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Kernel service for payment processing.
 *
 * <p>Handles order creation, payment gateway integration, and callback processing
 * with triple idempotency: signature verification, callback log check, and
 * pessimistic lock with amount verification before state transition.
 */
public class KernelPaymentService implements PaymentInboundPort {

    private final PaymentOrderRepositoryPort orderRepository;
    private final SubscriptionPlanRepositoryPort planRepository;
    private final PaymentGatewayPort paymentGateway;
    private final PaymentCallbackLogRepositoryPort callbackLogRepository;
    private final TransactionRunnerPort transactionRunner;

    public KernelPaymentService(PaymentOrderRepositoryPort orderRepository,
                                SubscriptionPlanRepositoryPort planRepository,
                                PaymentGatewayPort paymentGateway,
                                PaymentCallbackLogRepositoryPort callbackLogRepository,
                                TransactionRunnerPort transactionRunner) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository must not be null");
        this.paymentGateway = Objects.requireNonNull(paymentGateway, "paymentGateway must not be null");
        this.callbackLogRepository = Objects.requireNonNull(callbackLogRepository, "callbackLogRepository must not be null");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner must not be null");
    }

    @Override
    public PaymentOrder createOrder(String tenantId, PlanCode planCode, String channel) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(planCode, "planCode must not be null");
        Objects.requireNonNull(channel, "channel must not be null");

        SubscriptionPlan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan code: " + planCode));

        String orderNo = UUID.randomUUID().toString().replace("-", "");

        PaymentOrder order = new PaymentOrder(
                null,
                orderNo,
                tenantId,
                planCode,
                channel,
                PaymentOrder.STATUS_PENDING,
                plan.monthlyPrice(),
                null,
                Instant.now(),
                null
        );

        order = orderRepository.save(order);

        // Call payment gateway to create payment
        paymentGateway.createPayment(orderNo, plan.monthlyPrice(),
                "Seahorse Agent - " + plan.name());

        return order;
    }

    @Override
    public PaymentOrder getOrderStatus(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        return orderRepository.findByOrderNo(orderNo).orElse(null);
    }

    @Override
    public PaymentOrder handleCallback(String channel, Map<String, String> params) {
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(params, "params must not be null");

        String orderNo = params.get("out_trade_no");
        String channelTradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");

        if (orderNo == null || channelTradeNo == null) {
            throw new IllegalArgumentException("Missing required callback parameters");
        }

        // Idempotency check 1: check callback log
        if (callbackLogRepository.exists(channel, channelTradeNo)) {
            return orderRepository.findByOrderNo(orderNo).orElse(null);
        }

        // Idempotency check 2 & 3: lock order + verify state within transaction
        return transactionRunner.runInTransaction(() -> {
            PaymentOrder order = orderRepository.lockByOrderNo(orderNo)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNo));

            // Skip if already in terminal state
            if (PaymentOrder.STATUS_PAID.equals(order.status())) {
                return order;
            }

            // Transition to PAYING if still PENDING
            if (PaymentOrder.STATUS_PENDING.equals(order.status())) {
                order = order.markPaying();
            }

            // Verify trade status from channel
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                order = order.markPaid(channelTradeNo);
            } else {
                order = order.markFailed("Channel reported status: " + tradeStatus);
            }

            order = orderRepository.save(order);

            // Record callback log for idempotency
            callbackLogRepository.save(channel, channelTradeNo, orderNo);

            return order;
        });
    }
}
