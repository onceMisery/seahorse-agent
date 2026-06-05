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

import java.math.BigDecimal;

/**
 * Outbound port for interacting with external payment gateways (Alipay, WeChat Pay, etc.).
 *
 * <p>Implementations translate channel-specific APIs into a uniform interface.
 */
public interface PaymentGatewayPort {

    /**
     * Creates a payment request with the external gateway.
     *
     * @param orderNo the internal order number
     * @param amount  the payment amount
     * @param subject the payment subject / description
     * @return the gateway result containing payment credentials
     */
    PaymentGatewayResult createPayment(String orderNo, BigDecimal amount, String subject);

    /**
     * Result returned by the payment gateway after creating a payment request.
     *
     * @param qrCode    QR code string for scan-to-pay (may be {@code null})
     * @param url       payment redirect URL (may be {@code null})
     * @param prepayId  prepay identifier for in-app payments (may be {@code null})
     */
    record PaymentGatewayResult(String qrCode, String url, String prepayId) {
    }
}
