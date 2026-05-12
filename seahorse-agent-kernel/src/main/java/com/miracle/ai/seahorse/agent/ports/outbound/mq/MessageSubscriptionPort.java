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

package com.miracle.ai.seahorse.agent.ports.outbound.mq;

/**
 * 消息订阅端口。
 *
 * <p>应用层只暴露业务 handler，具体订阅、ACK、重试与反序列化由 L3 adapter 负责。
 */
public interface MessageSubscriptionPort {

    <T> AutoCloseable subscribe(String topic, String subscriptionName, Class<T> payloadType, MessageHandler<T> handler);
}
