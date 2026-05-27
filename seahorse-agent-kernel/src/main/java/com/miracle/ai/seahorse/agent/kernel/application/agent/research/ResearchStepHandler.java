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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;

/**
 * 研究步骤处理器接口。每种 ResearchStepType 对应一个实现。
 */
public interface ResearchStepHandler {

    ResearchStepType stepType();

    /**
     * 执行步骤逻辑。
     *
     * @param task 当前任务
     * @param context 步骤执行上下文（可读写中间结果）
     */
    void execute(DurableTask task, ResearchStepContext context);
}
