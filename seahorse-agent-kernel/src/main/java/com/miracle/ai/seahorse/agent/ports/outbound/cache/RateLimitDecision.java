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

import java.time.Duration;
import java.util.Objects;

/**
 * 限流判定结果。
 *
 * @param allowed          是否允许通过
 * @param remainingPermits 剩余许可数
 * @param retryAfter       被拒绝时建议等待时间
 * @param reason           判定原因
 */
public record RateLimitDecision(
        boolean allowed,
        long remainingPermits,
        Duration retryAfter,
        String reason
) {

    public RateLimitDecision {
        retryAfter = Objects.requireNonNullElse(retryAfter, Duration.ZERO);
        reason = Objects.requireNonNullElse(reason, "");
    }

    public static RateLimitDecision allowed(long remainingPermits) {
        return new RateLimitDecision(true, remainingPermits, Duration.ZERO, "");
    }

    public static RateLimitDecision rejected(Duration retryAfter, String reason) {
        return new RateLimitDecision(false, 0, retryAfter, reason);
    }
}
