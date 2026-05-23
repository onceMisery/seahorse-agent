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

package com.miracle.ai.seahorse.agent.adapters.web;

import java.util.List;

/**
 * 替换 Agent 发布版本工具绑定快照的请求体。
 *
 * @param tools 目标版本允许使用的工具绑定列表；空列表表示清空工具集
 */
public record AgentToolBindingReplaceRequest(List<AgentToolBindingItemRequest> tools) {

    public AgentToolBindingReplaceRequest {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
