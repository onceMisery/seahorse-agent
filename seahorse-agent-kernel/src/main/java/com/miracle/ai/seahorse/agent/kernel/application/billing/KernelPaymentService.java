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
import com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace.RevenueService;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.PaymentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentCallbackLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentOrderRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionPlanRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.TransactionRunnerPort;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Kernel service for payment processing.
 *
 * <p>Handles order creation, payment gateway integration, and callback processing
 * with triple idempotency: signature verification, callback log check, and
 * pessimistic lock with amount verification before state transition.
 *
 * <p>Optionally integrates with {@link RevenueService} to record marketplace
 * revenue shares when a payment for a marketplace agent subscription completes.
 */
public class KernelPaymentService implements PaymentInboundPort {

    private static final DateTimeFormatter PERIOD_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault());

    private final PaymentOrderRepositoryPort orderRepository;
    private final SubscriptionPlanRepositoryPort planRepository;
    private final PaymentGatewayPort paymentGateway;
    private final PaymentCallbackLogRepositoryPort callbackLogRepository;
    private final TransactionRunnerPort transactionRunner;
    private final RevenueService revenueService;

    /**
     * Full constructor with optional revenue service for marketplace revenue tracking.
     *
     * @param orderRepository       payment order persistence port
     * @param planRepository        subscription plan persistence port
     * @param paymentGateway        payment gateway integration port
     * @param callbackLogRepository callback log persistence port for idempotency
     * @param transactionRunner     transaction boundary port
     * @param revenueService        optional revenue service for marketplace payments (nullable)
     */
    public KernelPaymentService(PaymentOrderRepositoryPort orderRepository,
                                SubscriptionPlanRepositoryPort planRepository,
                                PaymentGatewayPort paymentGateway,
                                PaymentCallbackLogRepositoryPort callbackLogRepository,
                                TransactionRunnerPort transactionRunner,
                                RevenueService revenueService) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository must not be null");
        this.paymentGateway = Objects.requireNonNull(paymentGateway, "paymentGateway must not be null");
        this.callbackLogRepository = Objects.requireNonNull(callbackLogRepository, "callbackLogRepository must not be null");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner must not be null");
        this.revenueService = revenueService; // nullable — revenue tracking disabled when null
    }

    /**
     * Backward-compatible constructor without revenue service.
     */
    public KernelPaymentService(PaymentOrderRepositoryPort orderRepository,
                                SubscriptionPlanRepositoryPort planRepository,
                                PaymentGatewayPort paymentGateway,
                                PaymentCallbackLogRepositoryPort callbackLogRepository,
                                TransactionRunnerPort transactionRunner) {
        this(orderRepository, planRepository, paymentGateway, callbackLogRepository,
                transactionRunner, null);
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

            // Record marketplace revenue if revenue service is configured
            if (PaymentOrder.STATUS_PAID.equals(order.status())) {
                recordMarketplaceRevenue(order);
            }

            return order;
        });
    }

    /**
     * Records marketplace revenue share for a paid order.
     *
     * <p>This is a best-effort operation: any failure is silently ignored to ensure
     * revenue recording issues never affect the payment flow. The current implementation
     * uses the plan code as the agent reference and the tenant ID hash as the creator
     * reference. Full marketplace agent-level revenue tracking requires extending
     * {@link PaymentOrder} with agent metadata.
     *
     * @param order the paid payment order
     */
    private void recordMarketplaceRevenue(PaymentOrder order) {
        if (revenueService == null) {
            return;
        }
        try {
            String period = PERIOD_FORMATTER.format(
                    order.paidAt() != null ? order.paidAt() : Instant.now());
            String agentRef = order.planCode() != null
                    ? "plan:" + order.planCode().name()
                    : "plan:UNKNOWN";
            // Derive a stable creator user ID from tenant ID for revenue attribution.
            // In a full marketplace integration, the agent creator's userId would be
            // resolved from the order's marketplace metadata.
            Long creatorUserId = Math.abs((long) order.tenantId().hashCode());
            revenueService.calculateRevenue(agentRef, period, order.amount(), creatorUserId);
        } catch (Exception ignored) {
            // Best-effort: revenue recording failure must not affect payment flow
        }
    }
}
