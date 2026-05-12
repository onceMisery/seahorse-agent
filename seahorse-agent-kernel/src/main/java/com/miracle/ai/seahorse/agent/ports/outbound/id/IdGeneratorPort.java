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

package com.miracle.ai.seahorse.agent.ports.outbound.id;

import com.miracle.ai.seahorse.agent.kernel.plugin.AgentSPI;

/**
 * 分布式 ID 分配端口。
 */
@AgentSPI(defaultName = "local")
public interface IdGeneratorPort {

    /**
     * 获取指定命名空间下的下一个 ID。
     *
     * @param namespace 命名空间
     * @return 单调递增 ID
     */
    long nextId(String namespace);
}
