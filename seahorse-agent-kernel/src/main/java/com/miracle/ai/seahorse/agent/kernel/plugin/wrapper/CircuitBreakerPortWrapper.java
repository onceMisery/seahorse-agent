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

package com.miracle.ai.seahorse.agent.kernel.plugin.wrapper;

import java.util.Objects;

/**
 * 熔断包装器占位实现。
 */
public class CircuitBreakerPortWrapper<T> implements PortWrapper<T> {

    @Override
    public T wrap(T delegate) {
        return Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public String name() {
        return "circuit-breaker";
    }

    @Override
    public int order() {
        return 50;
    }
}
