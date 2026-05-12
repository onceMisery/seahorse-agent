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

import com.miracle.ai.seahorse.agent.kernel.plugin.AgentSPI;

import java.time.Duration;

/**
 * 统一限流端口。
 */
@AgentSPI(defaultName = "noop")
public interface RateLimiterPort {

    /**
     * 尝试获取限流许可。
     *
     * @param resource 资源标识
     * @param subject  限流主体
     * @param permits  窗口内允许的最大请求数
     * @param ttl      判定窗口
     * @return 限流判定结果
     */
    RateLimitDecision tryAcquire(String resource, String subject, int permits, Duration ttl);

    static RateLimiterPort noop() {
        return (resource, subject, permits, ttl) -> RateLimitDecision.allowed(Long.MAX_VALUE);
    }
}
