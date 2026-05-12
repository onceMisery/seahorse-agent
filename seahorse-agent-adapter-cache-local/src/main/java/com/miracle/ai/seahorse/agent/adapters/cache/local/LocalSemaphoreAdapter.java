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

package com.miracle.ai.seahorse.agent.adapters.cache.local;

import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedSemaphorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.SemaphorePermit;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 本地信号量 adapter。
 *
 * <p>该实现只在单 JVM 内生效，默认不限制总许可数，仅为依赖信号量端口的流程提供本地可运行实现。
 */
public class LocalSemaphoreAdapter implements DistributedSemaphorePort {

    @Override
    public Optional<SemaphorePermit> tryAcquire(String resource, String owner, int permits, Duration ttl) {
        if (permits <= 0) {
            return Optional.empty();
        }
        SemaphorePermit permit = new SemaphorePermit(
                requireText(resource, "resource"),
                requireText(owner, "owner"),
                permits,
                expireAt(ttl));
        return Optional.of(permit);
    }

    @Override
    public void release(SemaphorePermit permit) {
        Objects.requireNonNull(permit, "permit must not be null");
    }

    private Instant expireAt(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Instant.MAX;
        }
        return Instant.now().plus(ttl);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
