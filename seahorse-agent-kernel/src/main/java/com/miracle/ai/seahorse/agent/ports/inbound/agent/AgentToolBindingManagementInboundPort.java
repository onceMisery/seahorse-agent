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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;

import java.util.List;

public interface AgentToolBindingManagementInboundPort {

    /**
     * 整体替换某个 Agent 发布版本的工具绑定快照，避免增量修改造成版本工具集漂移。
     */
    List<AgentToolBinding> replaceBindings(String agentId,
                                           String versionId,
                                           AgentToolBindingReplaceCommand command);
}
