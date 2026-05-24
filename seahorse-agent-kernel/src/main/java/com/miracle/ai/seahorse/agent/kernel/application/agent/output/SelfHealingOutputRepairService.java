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

package com.miracle.ai.seahorse.agent.kernel.application.agent.output;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputRepairModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputRepairRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputRepairResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Slice 1c 自愈应用服务。
 *
 * <p>仅做一次修复重试，避免无限成本与幻觉累积。端口运行时异常被吞掉并返回空结果，
 * 由治理服务转换为 FAILED_AFTER_HEAL。
 */
public final class SelfHealingOutputRepairService {

    private final OutputRepairModelPort repairPort;

    public SelfHealingOutputRepairService(OutputRepairModelPort repairPort) {
        this.repairPort = Objects.requireNonNull(repairPort, "repairPort must not be null");
    }

    public String repairModelName() {
        return repairPort.name();
    }

    /**
     * 尝试一次修复。
     *
     * @return 修复后的内容；如果模型端口未返回有效修复内容或失败，返回 {@link Optional#empty()}
     */
    public Optional<String> repairOnce(OutputValidationRequest originalRequest, List<OutputValidationIssue> issues) {
        Objects.requireNonNull(originalRequest, "originalRequest must not be null");
        try {
            OutputRepairResult result = repairPort.repair(new OutputRepairRequest(originalRequest, issues));
            if (result == null || !result.hasRepairedContent()) {
                return Optional.empty();
            }
            return Optional.of(result.repairedContent());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
