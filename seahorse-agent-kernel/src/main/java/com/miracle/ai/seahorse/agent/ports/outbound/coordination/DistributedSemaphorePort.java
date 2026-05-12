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

package com.miracle.ai.seahorse.agent.ports.outbound.coordination;

import com.miracle.ai.seahorse.agent.kernel.plugin.AgentSPI;

import java.time.Duration;
import java.util.Optional;

/**
 * 分布式信号量端口。
 */
@AgentSPI(defaultName = "noop")
public interface DistributedSemaphorePort {

    /**
     * 尝试获取信号量许可。
     *
     * @param resource 资源标识
     * @param owner    持有者
     * @param permits  许可数
     * @param ttl      许可有效期
     * @return 获取成功时返回许可
     */
    Optional<SemaphorePermit> tryAcquire(String resource, String owner, int permits, Duration ttl);

    /**
     * 释放信号量许可。
     *
     * @param permit 许可
     */
    void release(SemaphorePermit permit);
}
