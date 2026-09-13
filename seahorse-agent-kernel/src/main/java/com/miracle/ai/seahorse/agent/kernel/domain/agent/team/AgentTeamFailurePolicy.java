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
 * Workflow DAG 节点失败策略（设计 §6.3：retry / skip / fail fast / wait human）。
 *
 * <p>FAIL_FAST：任一节点失败立即终止，剩余未执行节点置 SKIPPED，团队运行 FAILED；
 * SKIP：失败节点保持 FAILED，继续执行其余满足边条件的分支
 * （ON_FAILURE 条件边在该策略下生效），运行结束时任一节点失败即 FAILED；
 * RETRY：失败后重试最多 {@code maxRetries} 次，耗尽后等同 FAIL_FAST。
 * wait-human 需要审批集成，随后续阶段引入。
 *
 * <p>失败策略仅作用于 WORKFLOW_DAG 模式；SUPERVISOR 模式保持 fail-fast。
 */
public enum AgentTeamFailurePolicy {
    FAIL_FAST,
    SKIP,
    RETRY
}
