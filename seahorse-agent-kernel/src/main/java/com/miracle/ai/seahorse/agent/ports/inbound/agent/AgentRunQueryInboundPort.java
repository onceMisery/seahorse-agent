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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;

import java.util.List;

/**
 * Agent Run 查询（checkpoint/cost-summary）与恢复入站用例端口。
 *
 * <p>合并原本分散在三个单方法端口中的 Agent run 查询/恢复用例，由组合 facade 提供，
 * 供 Web 适配器通过契约依赖。</p>
 */
public interface AgentRunQueryInboundPort {

    List<AgentCheckpoint> listByRunId(String runId);

    CostUsageAggregate getCostSummary(String runId);

    AgentRun resume(String runId);
}
