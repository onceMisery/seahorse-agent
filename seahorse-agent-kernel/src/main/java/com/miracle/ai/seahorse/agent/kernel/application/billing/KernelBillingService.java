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

import com.miracle.ai.seahorse.agent.kernel.domain.billing.Bill;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.BillLineItem;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.Subscription;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.SubscriptionPlan;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.BillingInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.BillLineItemRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.BillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionPlanRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionRepositoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Kernel service for billing and invoice generation.
 *
 * <p>Handles bill listing, detail retrieval, and monthly bill generation
 * by iterating over all active subscriptions and creating bills with line items.
 */
public class KernelBillingService implements BillingInboundPort {

    private static final DateTimeFormatter BILL_PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int BILL_DUE_DAYS = 15;

    private final BillRepositoryPort billRepository;
    private final BillLineItemRepositoryPort lineItemRepository;
    private final SubscriptionRepositoryPort subscriptionRepository;
    private final SubscriptionPlanRepositoryPort planRepository;

    public KernelBillingService(BillRepositoryPort billRepository,
                                BillLineItemRepositoryPort lineItemRepository,
                                SubscriptionRepositoryPort subscriptionRepository,
                                SubscriptionPlanRepositoryPort planRepository) {
        this.billRepository = Objects.requireNonNull(billRepository, "billRepository must not be null");
        this.lineItemRepository = Objects.requireNonNull(lineItemRepository, "lineItemRepository must not be null");
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository, "subscriptionRepository must not be null");
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository must not be null");
    }

    @Override
    public List<Bill> listBills(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return billRepository.findByTenantId(tenantId);
    }

    @Override
    public Bill getBillDetail(String billNo) {
        if (billNo == null || billNo.isBlank()) {
            return null;
        }
        return billRepository.findByBillNo(billNo).orElse(null);
    }

    @Override
    public List<BillLineItem> getBillLineItems(Long billId) {
        if (billId == null) {
            return List.of();
        }
        return lineItemRepository.findByBillId(billId);
    }

    @Override
    public int generateMonthlyBills() {
        String billPeriod = LocalDate.now().format(BILL_PERIOD_FORMAT);
        List<Subscription> activeSubscriptions = subscriptionRepository.findAllActive();
        int generated = 0;

        for (Subscription subscription : activeSubscriptions) {
            // Skip if bill already generated for this period
            if (billRepository.existsForPeriod(subscription.tenantId(), billPeriod)) {
                continue;
            }

            SubscriptionPlan plan = planRepository.findByCode(subscription.planCode()).orElse(null);
            if (plan == null) {
                continue;
            }

            String billNo = "BILL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Instant now = Instant.now();
            Instant dueAt = now.plus(BILL_DUE_DAYS * 24L * 3600, java.time.temporal.ChronoUnit.SECONDS);

            Bill bill = new Bill(
                    null,
                    billNo,
                    subscription.tenantId(),
                    billPeriod,
                    plan.monthlyPrice(),
                    Bill.STATUS_GENERATED,
                    now,
                    dueAt
            );

            bill = billRepository.save(bill);

            // Create subscription fee line item
            BillLineItem feeItem = new BillLineItem(
                    null,
                    bill.id(),
                    BillLineItem.TYPE_SUBSCRIPTION_FEE,
                    "Monthly subscription - " + plan.name(),
                    plan.monthlyPrice(),
                    1L
            );
            lineItemRepository.save(feeItem);

            generated++;
        }

        return generated;
    }
}
