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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.plan;

import java.util.List;
import java.util.Objects;

/**
 * 调度波次：一组互相之间没有先后约束、可以并发执行的步骤。
 *
 * <p>波次内只保存步骤下标而不是 {@link PlanStep} 本身：调度是纯结构推导，
 * 不持有规划快照，规划在循环推进中会不断产生新的不可变副本。
 *
 * @param index       波次序号，从 0 开始
 * @param stepIndexes 该波次包含的步骤下标，升序
 */
public record PlanWave(int index, List<Integer> stepIndexes) {

    public PlanWave {
        Objects.requireNonNull(stepIndexes, "stepIndexes must not be null");
        stepIndexes = List.copyOf(stepIndexes);
    }

    public int size() {
        return stepIndexes.size();
    }

    /**
     * @return 波次内有多个步骤，即存在可并行的空间
     */
    public boolean parallelizable() {
        return stepIndexes.size() > 1;
    }
}
