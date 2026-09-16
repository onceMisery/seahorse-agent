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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime;

public enum AgentCheckpointType {
    MODEL_TURN,
    BEFORE_TOOL,
    AFTER_TOOL,
    WAITING_APPROVAL,
    /**
     * 步骤边界：一轮 ReAct 迭代成功结束后落盘，用于把长时程任务拆成多次调用续跑。
     *
     * <p>与 {@link #WAITING_APPROVAL} 的区别在于它不等待任何外部事件——
     * 调用方可以在任意时刻带着同一个 runId 再次发起执行，循环会从断点继续。
     */
    STEP_BOUNDARY
}
