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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;

import java.util.List;
import java.util.Optional;

public interface AgentRunRepositoryPort {

    /**
     * 创建新的运行记录。
     */
    void createRun(AgentRun run);

    /**
     * 更新运行状态、成本或错误信息。
     */
    void updateRun(AgentRun run);

    /**
     * 按 runId 查询运行记录。
     */
    Optional<AgentRun> findRunById(String runId);

    default AgentRunPage page(AgentRunQuery query) {
        AgentRunQuery safeQuery = query == null
                ? new AgentRunQuery(null, null, null, null, null, 1L, 15L)
                : query;
        return new AgentRunPage(List.of(), 0L, safeQuery.size(), safeQuery.current(), 0L);
    }

    /**
     * 追加执行步骤；调用方负责生成 run 内递增 stepNo。
     */
    void appendStep(AgentStep step);

    /**
     * 查询 run 内执行步骤，返回顺序应与 stepNo 一致。
     */
    List<AgentStep> listSteps(String runId);
}
