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

/**
 * 输出自愈模型端口。
 *
 * <p>Slice 1c 引入：当 {@code OutputGovernanceService} 检测到 BLOCK 时，可调用该端口一次以请求模型修复。
 * 适配器实现可走 OpenAI 兼容、本地模型，或复用当前 agent 主模型实例，由 starter 决定。
 *
 * <p>实现合同：
 * <ul>
 *     <li>不可以补造缺失业务事实，只做结构性修复。</li>
 *     <li>必须可重入；同一 request 多次调用结果可不同，但不得抛出未受控异常。</li>
 *     <li>任何运行时异常都视为修复失败，由调用方按 FAILED_AFTER_HEAL 处理。</li>
 * </ul>
 */
public interface OutputRepairModelPort {

    /**
     * 当前 repair 模型实现的标识。
     */
    String name();

    /**
     * 调用一次修复。
     */
    OutputRepairResult repair(OutputRepairRequest request);
}
