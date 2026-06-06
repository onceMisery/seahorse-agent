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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.PaymentCallbackLogDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.PaymentCallbackLogMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentCallbackLogRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 支付回调日志 MyBatis Plus 适配器。
 */
public class MybatisPlusPaymentCallbackLogRepositoryAdapter implements PaymentCallbackLogRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusPaymentCallbackLogRepositoryAdapter.class);

    private final PaymentCallbackLogMapper paymentCallbackLogMapper;

    public MybatisPlusPaymentCallbackLogRepositoryAdapter(PaymentCallbackLogMapper paymentCallbackLogMapper) {
        this.paymentCallbackLogMapper = Objects.requireNonNull(paymentCallbackLogMapper,
                "paymentCallbackLogMapper must not be null");
    }

    @Override
    public boolean exists(String channel, String channelTradeNo) {
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(channelTradeNo, "channelTradeNo must not be null");
        try {
            LambdaQueryWrapper<PaymentCallbackLogDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaymentCallbackLogDO::getChannel, channel)
                    .eq(PaymentCallbackLogDO::getChannelTradeNo, channelTradeNo);
            Long count = paymentCallbackLogMapper.selectCount(wrapper);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check callback log for channel={}, tradeNo={}: {}",
                    channel, channelTradeNo, e.getMessage());
            return false;
        }
    }

    @Override
    public void save(String channel, String channelTradeNo, String orderNo) {
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(channelTradeNo, "channelTradeNo must not be null");
        Objects.requireNonNull(orderNo, "orderNo must not be null");
        try {
            PaymentCallbackLogDO entity = new PaymentCallbackLogDO();
            entity.setChannel(channel);
            entity.setChannelTradeNo(channelTradeNo);
            entity.setOrderNo(orderNo);
            paymentCallbackLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("Failed to save callback log for channel={}, tradeNo={}, orderNo={}: {}",
                    channel, channelTradeNo, orderNo, e.getMessage(), e);
            throw e;
        }
    }
}
