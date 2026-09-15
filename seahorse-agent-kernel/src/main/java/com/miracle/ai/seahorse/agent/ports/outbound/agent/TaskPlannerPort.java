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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlan;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlanningRequest;

/**
 * 任务规划端口：把自然语言目标分解为有序、带依赖的步骤。
 *
 * <p>这是"规划"这条主线的唯一入口。当前内核只提供确定性的
 * {@code DefaultTaskPlanner}（按标记词切分），后续接入 LLM 规划器时
 * 只需新增实现并在装配处替换，循环侧代码不用改。
 *
 * <p>契约要求：
 * <ul>
 *   <li>实现必须是纯函数式的——同样的入参产出同样的步骤，不产生外部副作用；</li>
 *   <li>不允许返回 {@code null}；无法规划时返回 {@link TaskPlan#empty(String, String)}；</li>
 *   <li>调用方必须兜住实现抛出的异常并退化为"无规划"，规划失败不能中断任务本身。</li>
 * </ul>
 */
public interface TaskPlannerPort {

    /**
     * @param request 规划入参，允许为 {@code null}（按空目标处理）
     * @return 规划结果，永不返回 {@code null}
     */
    TaskPlan plan(TaskPlanningRequest request);
}
