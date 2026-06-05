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

/**
 * Outbound port for payment callback log persistence.
 *
 * <p>Used to achieve idempotent callback processing by recording
 * each successfully handled channel trade number.
 */
public interface PaymentCallbackLogRepositoryPort {

    /**
     * Checks whether a callback for the given channel trade has already been processed.
     *
     * @param channel       the payment channel
     * @param channelTradeNo the trade number from the channel
     * @return {@code true} if already processed
     */
    boolean exists(String channel, String channelTradeNo);

    /**
     * Records a successfully processed payment callback.
     *
     * @param channel       the payment channel
     * @param channelTradeNo the trade number from the channel
     * @param orderNo       the internal order number
     */
    void save(String channel, String channelTradeNo, String orderNo);
}
