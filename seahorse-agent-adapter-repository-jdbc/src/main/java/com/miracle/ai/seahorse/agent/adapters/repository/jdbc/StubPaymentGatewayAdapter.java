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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Stub implementation of {@link PaymentGatewayPort} that returns a mock payment result.
 *
 * <p>This adapter is registered as a fallback when no real payment gateway (Alipay, WeChat Pay, etc.)
 * is configured. It allows the billing system to start without an external payment provider.
 * Replace with a real gateway adapter in production environments.
 */
public class StubPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(StubPaymentGatewayAdapter.class);

    @Override
    public PaymentGatewayResult createPayment(String orderNo, BigDecimal amount, String subject) {
        log.warn("Using stub payment gateway — orderNo={}, amount={}, subject={}. " +
                "Configure a real PaymentGatewayPort bean for production use.",
                orderNo, amount, subject);
        return new PaymentGatewayResult(null, null, null);
    }
}
