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

/**
 * 单个 Agent 版本工具绑定的入站命令项。
 *
 * @param toolId             工具目录中的稳定工具 ID
 * @param maxCallsPerRun     单次 Agent Run 允许调用该工具的最大次数
 * @param argumentPolicyJson 绑定级参数约束 JSON，为空时使用空 JSON 对象
 */
public record AgentToolBindingItemCommand(String toolId,
                                          int maxCallsPerRun,
                                          String argumentPolicyJson) {
}
