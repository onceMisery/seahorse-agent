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

import com.miracle.ai.seahorse.agent.kernel.domain.billing.PaymentOrder;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.Subscription;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.SubscriptionPlan;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.PaymentSubscriptionInboundPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 支付与订阅的组合入站实现。
 *
 * <p>将支付订单（{@link KernelPaymentService}）与订阅
 * （{@link KernelSubscriptionService}）聚合到同一入站用例端口，供 Web 适配器
 * 通过契约依赖，避免 Web 直接依赖具体 Kernel 服务实现。</p>
 */
public class KernelPaymentSubscriptionFacade implements PaymentSubscriptionInboundPort {

    private final KernelPaymentService paymentService;
    private final KernelSubscriptionService subscriptionService;

    public KernelPaymentSubscriptionFacade(KernelPaymentService paymentService,
                                           KernelSubscriptionService subscriptionService) {
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService must not be null");
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService must not be null");
    }

    @Override
    public PaymentOrder createOrder(String tenantId, PlanCode planCode, String channel) {
        return paymentService.createOrder(tenantId, planCode, channel);
    }

    @Override
    public PaymentOrder getOrderStatus(String orderNo) {
        return paymentService.getOrderStatus(orderNo);
    }

    @Override
    public PaymentOrder handleCallback(String channel, Map<String, String> params) {
        return paymentService.handleCallback(channel, params);
    }

    @Override
    public List<SubscriptionPlan> listPlans() {
        return subscriptionService.listPlans();
    }

    @Override
    public Subscription getActiveSubscription(String tenantId) {
        return subscriptionService.getActiveSubscription(tenantId);
    }

    @Override
    public Subscription subscribe(String tenantId, PlanCode planCode) {
        return subscriptionService.subscribe(tenantId, planCode);
    }
}
