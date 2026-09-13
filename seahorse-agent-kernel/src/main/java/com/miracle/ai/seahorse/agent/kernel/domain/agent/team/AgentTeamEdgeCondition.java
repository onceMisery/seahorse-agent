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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.team;

/**
 * Workflow DAG 边的执行条件。
 *
 * <p>ALWAYS：源节点结束后无条件触发；ON_SUCCESS：仅当源节点成功后触发。
 * P1 执行策略为 fail-fast，源节点失败会终止整个团队运行，
 * 因此 ON_FAILURE 条件边在本阶段不被执行器支持，定义校验会拒绝。
 */
public enum AgentTeamEdgeCondition {
    ALWAYS,
    ON_SUCCESS
}
