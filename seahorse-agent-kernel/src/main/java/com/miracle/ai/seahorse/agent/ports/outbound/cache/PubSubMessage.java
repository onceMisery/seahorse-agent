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

package com.miracle.ai.seahorse.agent.ports.outbound.cache;

import java.util.Map;
import java.util.Objects;

/**
 * 发布订阅消息。
 *
 * @param topic   主题
 * @param payload 消息体
 * @param headers 消息头
 */
public record PubSubMessage(String topic, String payload, Map<String, String> headers) {

    public PubSubMessage {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        payload = Objects.requireNonNullElse(payload, "");
        headers = Map.copyOf(Objects.requireNonNullElse(headers, Map.of()));
    }
}
