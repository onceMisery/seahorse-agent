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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.BillingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.PaymentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.SubscriptionInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for billing operations: plans, subscriptions, payments, and bills.
 */
@RestController
public class SeahorseBillingController {

    private final ObjectProvider<SubscriptionInboundPort> subscriptionPortProvider;
    private final ObjectProvider<PaymentInboundPort> paymentPortProvider;
    private final ObjectProvider<BillingInboundPort> billingPortProvider;

    public SeahorseBillingController(ObjectProvider<SubscriptionInboundPort> subscriptionPortProvider,
                                     ObjectProvider<PaymentInboundPort> paymentPortProvider,
                                     ObjectProvider<BillingInboundPort> billingPortProvider) {
        this.subscriptionPortProvider = subscriptionPortProvider;
        this.paymentPortProvider = paymentPortProvider;
        this.billingPortProvider = billingPortProvider;
    }

    @GetMapping("/api/billing/plans")
    public ApiResponse<Object> listPlans() {
        return ApiResponses.requireService(subscriptionPortProvider,
                SubscriptionInboundPort::listPlans);
    }

    @GetMapping("/api/billing/subscription")
    public ApiResponse<Object> getActiveSubscription() {
        String tenantId = TenantContext.get();
        return ApiResponses.requireService(subscriptionPortProvider,
                port -> port.getActiveSubscription(tenantId));
    }

    @PostMapping("/api/billing/subscribe")
    public ApiResponse<Object> subscribe(@RequestBody SubscribeRequest request) {
        String tenantId = TenantContext.get();
        SubscribeRequest safeRequest = request == null
                ? new SubscribeRequest(null)
                : request;
        return ApiResponses.requireService(subscriptionPortProvider,
                port -> port.subscribe(tenantId, safeRequest.planCode()));
    }

    @PostMapping("/api/billing/orders")
    public ApiResponse<Object> createOrder(@RequestBody CreateOrderRequest request) {
        String tenantId = TenantContext.get();
        CreateOrderRequest safeRequest = request == null
                ? new CreateOrderRequest(null, null)
                : request;
        return ApiResponses.requireService(paymentPortProvider,
                port -> port.createOrder(tenantId, safeRequest.planCode(), safeRequest.paymentChannel()));
    }

    @GetMapping("/api/billing/orders/{orderNo}")
    public ApiResponse<Object> getOrderStatus(@PathVariable String orderNo) {
        return ApiResponses.requireService(paymentPortProvider,
                port -> port.getOrderStatus(orderNo));
    }

    @PostMapping("/api/billing/callbacks/{channel}")
    public ApiResponse<Object> handleCallback(@PathVariable String channel,
                                              @RequestParam Map<String, String> params) {
        return ApiResponses.requireService(paymentPortProvider,
                port -> port.handleCallback(channel, params));
    }

    @GetMapping("/api/billing/bills")
    public ApiResponse<Object> listBills() {
        String tenantId = TenantContext.get();
        return ApiResponses.requireService(billingPortProvider,
                port -> port.listBills(tenantId));
    }

    @GetMapping("/api/billing/bills/{billNo}")
    public ApiResponse<Object> getBillDetail(@PathVariable String billNo) {
        return ApiResponses.requireService(billingPortProvider,
                port -> port.getBillDetail(billNo));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscribeRequest(PlanCode planCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateOrderRequest(PlanCode planCode, String paymentChannel) {
    }
}
