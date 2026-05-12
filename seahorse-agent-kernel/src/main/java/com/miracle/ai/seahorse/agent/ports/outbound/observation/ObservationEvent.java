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

package com.miracle.ai.seahorse.agent.ports.outbound.observation;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 观测事件。
 *
 * @param name       事件名称
 * @param occurredAt 发生时间
 * @param attributes 事件属性
 */
public record ObservationEvent(String name, Instant occurredAt, Map<String, String> attributes) {

    public ObservationEvent {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        occurredAt = Objects.requireNonNullElseGet(occurredAt, Instant::now);
        attributes = Map.copyOf(Objects.requireNonNullElse(attributes, Map.of()));
    }
}
