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
 * 团队编排模式。
 *
 * <p>SUPERVISOR：由 supervisor 成员规划子任务，并通过 handoff 分派给成员执行；
 * WORKFLOW_DAG：按定义的有向无环图拓扑顺序执行成员节点。
 */
public enum AgentTeamMode {
    SUPERVISOR,
    WORKFLOW_DAG
}
