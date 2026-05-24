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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;

import java.util.List;
import java.util.Optional;

public interface AgentRunInboundPort {

    /**
     * 创建并启动一次 Agent 运行。
     */
    AgentRun startRun(AgentRunStartCommand command);

    /**
     * 按 runId 查询运行记录。
     */
    Optional<AgentRun> findRunById(String runId);

    /**
     * 查询 run 内已记录的执行步骤。
     */
    List<AgentStep> listSteps(String runId);

    /**
     * 取消运行；重复取消应保持幂等。
     */
    AgentRun cancel(String runId);

    /**
     * 将失败运行标记为等待重试；重复重试保持幂等。
     */
    AgentRun retry(String runId);

    /**
     * 将运行标记为成功；终态运行不再漂移。
     */
    AgentRun succeed(String runId);

    /**
     * 将运行标记为失败；终态运行不再漂移。
     */
    AgentRun fail(String runId, String errorCode, String errorMessage);
}
