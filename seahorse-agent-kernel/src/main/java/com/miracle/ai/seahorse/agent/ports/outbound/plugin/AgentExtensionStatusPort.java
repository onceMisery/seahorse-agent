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

package com.miracle.ai.seahorse.agent.ports.outbound.plugin;

import java.util.List;

/**
 * 插件启用状态端口。
 *
 * <p>实现可以落到数据库、配置中心或纯内存诊断视图；内核只依赖该端口查询或保存状态。
 */
public interface AgentExtensionStatusPort {

    List<AgentExtensionStatus> listStatuses();

    void saveStatus(AgentExtensionStatus status);

    static AgentExtensionStatusPort empty() {
        return new AgentExtensionStatusPort() {
            @Override
            public List<AgentExtensionStatus> listStatuses() {
                return List.of();
            }

            @Override
            public void saveStatus(AgentExtensionStatus status) {
            }
        };
    }
}

