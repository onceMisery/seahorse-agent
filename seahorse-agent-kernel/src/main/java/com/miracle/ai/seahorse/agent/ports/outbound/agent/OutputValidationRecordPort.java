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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputGovernanceResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;

/**
 * 输出治理运行记录出站端口。
 *
 * <p>Slice 1a 保留接口以预留未来持久化能力，默认实现为 {@link #noop()}。在
 * 1c 接入 self-heal、1b 接入 DDL 之后，adapter 侧会增加 JDBC 实现把运行记录落库。
 *
 * <p>Slice 2（生产 noop 风险治理）会把本端口纳入 {@code NoopFallback} 标记体系，
 * 用于 starter 启动期识别并按生产策略分类降级；当前切片暂不引入该 marker。
 */
public interface OutputValidationRecordPort {

    /**
     * 记录一次输出治理的运行结果。
     */
    void record(OutputValidationRequest request, OutputGovernanceResult result);

    /**
     * 默认空实现：不持久化任何运行记录。
     */
    static OutputValidationRecordPort noop() {
        return (request, result) -> {
            // intentionally empty: production adapters override this method to persist records.
        };
    }
}
